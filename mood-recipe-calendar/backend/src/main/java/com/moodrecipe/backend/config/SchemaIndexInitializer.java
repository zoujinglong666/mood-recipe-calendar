package com.moodrecipe.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等地补齐高频查询缺失的复合索引。
 * 不依赖 Hibernate ddl-auto 的 @Index（MySQL 重部署时若索引已存在会报 Duplicate key 致启动失败），
 * 改为启动时查 information_schema，仅在缺失时才 CREATE INDEX。索引缺失只影响查询性能，不影响启动。
 */
@Component
public class SchemaIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaIndexInitializer.class);

    private final JdbcTemplate jdbc;

    public SchemaIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure("idx_wmp_openid_created", "weekly_meal_plans", "openid, created_at");
        ensure("idx_ur_openid_created", "user_records", "openid, created_at");
        ensure("idx_amf_openid_status_updated", "agent_memory_facts", "openid, status, updated_at");
        ensure("idx_ac_openid_status_updated", "agent_conversations", "openid, status, updated_at");
    }

    private void ensure(String index, String table, String columns) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                    Integer.class, table, index);
            if (count != null && count > 0) return;
            jdbc.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
            log.info("已创建索引 {}.{} ({})", table, index, columns);
        } catch (Exception e) {
            // 非致命：索引缺失不影响启动，仅影响查询性能
            log.warn("创建索引 {}.{} 失败（可忽略，仅影响性能）: {}", table, index, e.getMessage());
        }
    }
}
