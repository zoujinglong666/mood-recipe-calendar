-- 可追溯的一日三餐规划基础数据。此迁移不导入任何未获授权的指南正文、图形或图示。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nutrition_knowledge_packs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(80) NOT NULL,
  version VARCHAR(32) NOT NULL,
  title VARCHAR(120) NOT NULL,
  audience VARCHAR(80) NOT NULL,
  recommendation_text TEXT NOT NULL,
  ingredient_notes TEXT,
  hard_constraints TEXT,
  explanation_text TEXT NOT NULL,
  source_kind VARCHAR(32) NOT NULL,
  source_reference VARCHAR(500) NOT NULL,
  license_status VARCHAR(32) NOT NULL,
  reviewed_at DATETIME,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME,
  UNIQUE KEY uk_nutrition_knowledge_pack_code_version (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可追溯日常饮食知识包';

CREATE TABLE IF NOT EXISTS seasonal_ingredients (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  region VARCHAR(32) NOT NULL,
  start_month TINYINT NOT NULL,
  end_month TINYINT NOT NULL,
  substitutes TEXT,
  source_reference VARCHAR(500) NOT NULL,
  license_status VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  KEY idx_seasonal_ingredient_lookup (region, start_month, end_month, enabled, license_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当季食材目录';

CREATE TABLE IF NOT EXISTS daily_meal_plans (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, openid VARCHAR(64) NOT NULL, plan_date DATE NOT NULL,
 plan_json TEXT NOT NULL, UNIQUE KEY uk_daily_meal_plan_openid_date (openid, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一日三餐计划快照';
