CREATE TABLE IF NOT EXISTS fridge_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid       VARCHAR(64) NOT NULL,
  name         VARCHAR(80) NOT NULL,
  quantity     DECIMAL(10,2) NOT NULL,
  unit         VARCHAR(20) NOT NULL,
  purchased_on DATE,
  expires_on   DATE,
  note         VARCHAR(240),
  created_at   DATETIME NOT NULL,
  updated_at   DATETIME NOT NULL,
  KEY idx_fridge_openid_expiry (openid, expires_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户冰箱食材库存';
