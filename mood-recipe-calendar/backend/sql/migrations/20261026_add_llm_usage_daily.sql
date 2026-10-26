-- [2026-10-26] LLM 用量每日落库（架构评审 P2-2）
-- 每天 23:57 由 LlmUsageDailyService 把内存计数快照并清零，按日 + 档位落库，形成可持续对账的用量史。
-- 对应实体：com.moodrecipe.backend.entity.LlmUsageDaily
CREATE TABLE IF NOT EXISTS llm_usage_daily (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  usage_date      DATE         NOT NULL COMMENT '业务日期（Asia/Shanghai）',
  tier            VARCHAR(32)  NOT NULL COMMENT '档位：free / paid_pro / paid_flash',
  calls           BIGINT       NOT NULL DEFAULT 0 COMMENT 'complete() 调用次数',
  failures        BIGINT       NOT NULL DEFAULT 0 COMMENT '失败次数（LlmResult.failure != null）',
  total_attempts  BIGINT       NOT NULL DEFAULT 0 COMMENT '总尝试次数（含重试）',
  total_latency_ms BIGINT      NOT NULL DEFAULT 0 COMMENT '总时延 ms，除以 calls 得均值',
  created_at      DATETIME     NOT NULL,
  UNIQUE KEY uk_llm_usage_daily_date_tier (usage_date, tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 用量每日快照';
