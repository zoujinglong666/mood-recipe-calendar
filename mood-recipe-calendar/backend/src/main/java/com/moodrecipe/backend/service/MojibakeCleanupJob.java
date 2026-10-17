package com.moodrecipe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 乱码数据每日清扫定时任务：
 * 生产库曾因以 latin1 客户端连接导入 SQL，中文字符在入库时被替换成字面 '?'（0x3F），
 * 用户端直接看到「????」的菜名/简介（数据不可逆，只能删后重灌）。
 *
 * 接口层已有 RecipeTextGuard 防线兜底（不外露），本任务做数据层根治：
 * 每天凌晨低峰扫描 recipes 中菜名/简介含 '?' 或 U+FFFD 的行，
 * 先备份进 recipes_mojibake_bak（留追溯凭证）再删除；菜谱池 60s TTL 自动刷新，删除即时生效。
 *
 * 商品/虚拟商品/烹饪知识库只扫不删：orders/entitlements 引用商品行，
 * 贸易数据自动删除会断链，发现损坏仅记 ALERT 由人工处理（可用 fix-mojibake.sql 救双编码型）。
 * 口径与 backend/sql/scrub_broken_recipes.sql 完全一致，幂等可重复执行。
 */
@Service
public class MojibakeCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MojibakeCleanupJob.class);

    /** recipes 删除口径（与 scrub_broken_recipes.sql 保持全等，防止两处漂移），固定用 r 表别名 */
    private static final String RECIPE_BROKEN_PREDICATE =
            "r.name LIKE '%?%' OR r.description LIKE '%?%'"
                    + " OR r.name LIKE CONCAT('%', CHAR(0xEFBFBD), '%')"
                    + " OR r.description LIKE CONCAT('%', CHAR(0xEFBFBD), '%')";

    private final JdbcTemplate jdbc;
    private final OperationalEventService operationalEvents;

    public MojibakeCleanupJob(JdbcTemplate jdbc, OperationalEventService operationalEvents) {
        this.jdbc = jdbc;
        this.operationalEvents = operationalEvents;
    }

    /** 每天 04:17 低峰清扫（避开 9 点提醒任务与早晚做饭高峰）。 */
    @Scheduled(cron = "0 17 4 * * ?", zone = "Asia/Shanghai")
    public void cleanCorruptedRows() {
        try {
            int deleted = scrubRecipes();
            Map<String, Integer> alertTables = scanReferenceTables();
            if (deleted > 0 || !alertTables.isEmpty()) {
                record(deleted, alertTables);
            }
            log.info("乱码清扫任务完成：删除菜谱 {} 行{}", deleted, alertTables.isEmpty() ? "" : "，需人工处理 " + alertTables);
        } catch (RuntimeException e) {
            // 清扫失败只告警，不影响调度线程与其它任务（接口层防线仍在兜底）
            log.warn("乱码清扫任务失败: {}", e.getMessage());
            recordFailure(e);
        }
    }

    /** 备份后删除损坏菜谱，返回删除行数；备份表按 id 去重，重复执行 0 行受影响。 */
    private int scrubRecipes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS recipes_mojibake_bak LIKE recipes");
        jdbc.update("INSERT INTO recipes_mojibake_bak SELECT r.* FROM recipes r "
                + "WHERE (" + RECIPE_BROKEN_PREDICATE + ") "
                + "AND NOT EXISTS (SELECT 1 FROM recipes_mojibake_bak b WHERE b.id = r.id)");
        return jdbc.update("DELETE r FROM recipes r WHERE " + RECIPE_BROKEN_PREDICATE);
    }

    /** 引用表只扫不删：返回「表名 -> 损坏行数」的告警清单（空 Map 表示干净）。 */
    private Map<String, Integer> scanReferenceTables() {
        Map<String, Integer> alerts = new LinkedHashMap<>();
        countBroken("virtual_products", "title", "description", alerts);
        countBroken("products", "name", "description", alerts);
        countBroken("cooking_knowledge_chunks", "title", "content", alerts);
        return alerts;
    }

    private void countBroken(String table, String colA, String colB, Map<String, Integer> alerts) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + colA + " LIKE '%?%' OR " + colB + " LIKE '%?%'"
                        + " OR " + colA + " LIKE CONCAT('%', CHAR(0xEFBFBD), '%')"
                        + " OR " + colB + " LIKE CONCAT('%', CHAR(0xEFBFBD), '%')",
                Integer.class);
        if (n != null && n > 0) alerts.put(table, n);
    }

    private String buildAlertDetail(Map<String, Integer> alerts) {
        StringBuilder sb = new StringBuilder("; reference_tables_broken=");
        alerts.forEach((table, n) -> sb.append(table).append(':').append(n).append(' '));
        return sb.toString().trim();
    }

    private void record(int deleted, Map<String, Integer> alerts) {
        try {
            operationalEvents.record("MOJIBAKE_CLEANUP", alerts.isEmpty() ? "INFO" : "ALERT", null, null,
                    "deleted_recipes=" + deleted + buildAlertDetail(alerts));
        } catch (RuntimeException ignored) {
            // 告警失败不覆盖清扫结果
        }
    }

    private void recordFailure(RuntimeException e) {
        try {
            operationalEvents.record("MOJIBAKE_CLEANUP_FAILED", "ALERT", null, null, String.valueOf(e.getMessage()));
        } catch (RuntimeException ignored) {
            // 告警失败只能依赖日志
        }
    }
}
