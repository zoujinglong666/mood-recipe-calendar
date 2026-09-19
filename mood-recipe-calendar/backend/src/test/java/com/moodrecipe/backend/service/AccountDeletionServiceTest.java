package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void deletionAnonymizesOrdersAndRemovesPrivateRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("user-1"))).thenReturn(List.of());
        when(jdbc.update("delete from users where openid = ?", "user-1")).thenReturn(1);

        new AccountDeletionService(jdbc, mock(CosImageStorageService.class)).delete("user-1");

        verify(jdbc).update(startsWith("update virtual_orders set openid"), startsWith("deleted_"), eq("user-1"));
        verify(jdbc).update("delete from agent_memory_facts where openid = ?", "user-1");
        verify(jdbc).update("delete from users where openid = ?", "user-1");
    }
}
