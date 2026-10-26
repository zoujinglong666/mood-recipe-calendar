-- ===== 2026-10-26 补建 user_profile_snapshots（架构评审 P0-2）=====
-- 背景：该表 DDL 在 sql/init.sql 中存在，但未进入 sql/migrations 增量迁移，
--       走「init.sql 基线 + 增量迁移」路线的库会缺表，启动期报
--       Table 'mood_recipe.user_profile_snapshots' doesn't exist。
-- 幂等（IF NOT EXISTS）；本文件由 Flyway 作为 V20261026.01 自动执行。
-- 注意：trigger 是 MySQL 保留字，列名必须加反引号（实体列由 Hibernate 自动转义，故此前未暴露）。

CREATE TABLE IF NOT EXISTS user_profile_snapshots (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid       VARCHAR(64) NOT NULL COMMENT '微信 openid',
  snapshot_at  DATETIME    NOT NULL COMMENT '快照时间',
  `trigger`    VARCHAR(16) COMMENT '触发原因：LIKE/DISLIKE/MADE/SCHEDULED',
  confidence   DOUBLE COMMENT '当时画像置信度',
  profile_json TEXT COMMENT '完整 UserProfile JSON，支持回溯与离线重算',
  INDEX idx_profile_snapshot_openid_time (openid, snapshot_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户画像快照';
