package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 乱码清扫定时任务：备份→删除的顺序、幂等语义与告警上报。
 */
class MojibakeCleanupJobTest {

    @Test
    void backsUpBeforeDeleteAndRecordsEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperationalEventService events = mock(OperationalEventService.class);
        when(jdbc.update(anyString())).thenReturn(3);

        new MojibakeCleanupJob(jdbc, events).cleanCorruptedRows();

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).execute("CREATE TABLE IF NOT EXISTS recipes_mojibake_bak LIKE recipes");
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO recipes_mojibake_bak"));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("DELETE r FROM recipes"));
        // 备份语句必须带 NOT EXISTS 幂等去重，防止重复备份
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("NOT EXISTS (SELECT 1 FROM recipes_mojibake_bak b WHERE b.id = r.id)"));
        verify(events).record(eq("MOJIBAKE_CLEANUP"), eq("INFO"), eq(null), eq(null),
                org.mockito.ArgumentMatchers.contains("deleted_recipes=3"));
    }

    @Test
    void cleanDatabaseRecordsNothing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperationalEventService events = mock(OperationalEventService.class);
        when(jdbc.update(anyString())).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        new MojibakeCleanupJob(jdbc, events).cleanCorruptedRows();

        verify(events, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void brokenReferenceTablesAreAlertedNotDeleted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperationalEventService events = mock(OperationalEventService.class);
        when(jdbc.update(anyString())).thenReturn(0);
        // virtual_products 命中损坏，其余两张干净
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM virtual_products"), eq(Integer.class))).thenReturn(2);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM products WHERE"), eq(Integer.class))).thenReturn(0);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM cooking_knowledge_chunks"), eq(Integer.class))).thenReturn(0);

        new MojibakeCleanupJob(jdbc, events).cleanCorruptedRows();

        verify(events).record(eq("MOJIBAKE_CLEANUP"), eq("ALERT"), eq(null), eq(null),
                org.mockito.ArgumentMatchers.contains("virtual_products:2"));
        // 引用表绝不自动删除
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("DELETE FROM virtual_products"));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("DELETE FROM products"));
    }

    @Test
    void failureIsSwallowedAndAlerted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OperationalEventService events = mock(OperationalEventService.class);
        doThrow(new RuntimeException("db down")).when(jdbc).execute(anyString());

        new MojibakeCleanupJob(jdbc, events).cleanCorruptedRows();

        verify(events).record(eq("MOJIBAKE_CLEANUP_FAILED"), eq("ALERT"), eq(null), eq(null),
                org.mockito.ArgumentMatchers.contains("db down"));
    }
}
