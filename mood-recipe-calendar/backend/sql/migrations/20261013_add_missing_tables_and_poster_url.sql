-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
-- 补齐「实体已存在但 SQL 缺失」的漂移：user_records.poster_url（20261003 已迁，但 init.sql 漏回填）
-- 与 guideline_chunks / share_records / daily_active_users 三张实体表（一直靠 ddl-auto=update 兜底）。
-- 全部幂等，可重复执行。

SET @has_poster_url := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_records' AND COLUMN_NAME = 'poster_url'
);
SET @ddl_poster_url := IF(@has_poster_url = 0,
  'ALTER TABLE user_records ADD COLUMN poster_url TEXT COMMENT ''会员精装海报URL（record-detail 海报+后端模式）''',
  'SELECT 1');
PREPARE stmt FROM @ddl_poster_url; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS guideline_chunks (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  page           INT NOT NULL COMMENT '来源PDF页码',
  section        VARCHAR(120) COMMENT '章节名',
  category       VARCHAR(40) NOT NULL COMMENT '知识分类',
  title          VARCHAR(300) NOT NULL COMMENT '知识点标题',
  content        LONGTEXT NOT NULL COMMENT '改写后的知识点正文',
  tags           VARCHAR(400) COMMENT '逗号分隔关键词',
  source_name    VARCHAR(120) NOT NULL COMMENT '来源名称',
  source_url     VARCHAR(400) COMMENT '来源链接',
  embedding_json LONGTEXT COMMENT '向量JSON浮点数组',
  enabled        TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME NOT NULL,
  reviewed_at    DATETIME NOT NULL,
  INDEX idx_guideline_chunk_category (category, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='膳食指南RAG知识块';

CREATE TABLE IF NOT EXISTS share_records (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid     VARCHAR(64) NOT NULL COMMENT '微信 openid',
  scene      VARCHAR(32) NOT NULL COMMENT 'recipe/album/meal-agent',
  share_date DATE NOT NULL COMMENT '分享日期（按天去重）',
  created_at DATETIME,
  INDEX idx_share_record_openid_date (openid, share_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='分享记录';

CREATE TABLE IF NOT EXISTS daily_active_users (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  active_date DATE NOT NULL COMMENT '活跃日期',
  openid      VARCHAR(64) NOT NULL COMMENT '微信 openid',
  UNIQUE KEY uk_dau_date_openid (active_date, openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每日活跃用户去重表';
