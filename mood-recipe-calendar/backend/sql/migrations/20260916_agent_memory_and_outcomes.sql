-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
-- 智能体（真）落地：事实记忆表、执行反馈表、计划审计列。

ALTER TABLE weekly_meal_plans
  ADD COLUMN agent_json TEXT COMMENT '智能体审计JSON：质量分/降级原因/用到的记忆/traceId' AFTER favorite;

CREATE TABLE IF NOT EXISTS agent_memory_facts (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid        VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  memory_key    VARCHAR(48)  NOT NULL COMMENT '事实键，如 spice / household / affinity.赣菜',
  memory_value  VARCHAR(200) NOT NULL COMMENT '事实值',
  source        VARCHAR(16)  NOT NULL DEFAULT 'CHAT' COMMENT '来源 EXPLICIT/CHAT/INFERRED/BEHAVIOR/LEARNED',
  confidence    DOUBLE       NOT NULL DEFAULT 0.6 COMMENT '置信度 0~1，随时间衰减',
  evidence      VARCHAR(500) COMMENT '这条记忆的依据，用于解释',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  hit_count     INT          NOT NULL DEFAULT 0 COMMENT '被引用次数',
  last_used_at  DATETIME,
  expires_at    DATETIME COMMENT '有时效的事实的过期时间',
  updated_at    DATETIME     NOT NULL,
  UNIQUE KEY uk_agent_memory (openid, memory_key),
  INDEX idx_agent_memory_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='智能体事实记忆';

CREATE TABLE IF NOT EXISTS plan_dish_outcomes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  plan_id     BIGINT       NOT NULL COMMENT '周计划 id',
  day_index   INT          NOT NULL COMMENT '第几天',
  dish_index  INT          NOT NULL COMMENT '当天的第几道菜',
  dish_name   VARCHAR(120) NOT NULL COMMENT '菜名',
  cooked      TINYINT(1) COMMENT '是否做了 null=未反馈',
  leftover    TINYINT(1) COMMENT '是否剩了很多',
  too_hard    TINYINT(1) COMMENT '是否太难做',
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  UNIQUE KEY uk_plan_dish (openid, plan_id, day_index, dish_index),
  INDEX idx_plan_dish_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='周菜单执行反馈';
