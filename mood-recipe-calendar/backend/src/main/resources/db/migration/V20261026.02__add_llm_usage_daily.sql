-- ===== 2026-10-26 LLM 用量每日落库（架构评审 P2-2）=====
-- 每天 23:57 由 LlmUsageDailyService 把内存计数快照清零后按日+档位 upsert 到本表，
-- 形成可对账的用量史（此前 LlmUsageCounter 重启即清零，成本/会员对账无历史）。
-- 幂等（唯一键 uk_llm_usage_daily_date_tier，服务层 find-then-add）。

CREATE TABLE IF NOT EXISTS llm_usage_daily (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  usage_date      DATE        NOT NULL COMMENT '业务日期（Asia/Shanghai）',
  tier            VARCHAR(32) NOT NULL COMMENT '档位：free/paid_pro/paid_flash',
  calls           BIGINT      NOT NULL DEFAULT 0 COMMENT '调用次数',
  failures        BIGINT      NOT NULL DEFAULT 0 COMMENT '失败次数',
  total_attempts  BIGINT      NOT NULL DEFAULT 0 COMMENT '总尝试次数（含重试）',
  total_latency_ms BIGINT     NOT NULL DEFAULT 0 COMMENT '总时延 ms',
  created_at      DATETIME    NOT NULL COMMENT '入库时间',
  UNIQUE KEY uk_llm_usage_daily_date_tier (usage_date, tier)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='LLM 用量日报';
