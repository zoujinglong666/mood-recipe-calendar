-- 激励视频广告奖励记账：每看完一次广告记一条，用于给「换一整天」按当日观看次数累加额度（看一次规划一次）。
CREATE TABLE IF NOT EXISTS ad_rewards (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  reward_type VARCHAR(32)  NOT NULL COMMENT '奖励类型：RESHUFFLE_UNLIMITED=看完广告兑换一次换一整天',
  reward_date DATE         NOT NULL COMMENT '奖励归属日期',
  ad_unit_id  VARCHAR(64)  COMMENT '广告位 id（可选，用于归因）',
  created_at  DATETIME     COMMENT '记录时间',
  KEY idx_ad_reward (openid, reward_type, reward_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激励视频广告奖励记账';
