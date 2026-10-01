CREATE TABLE IF NOT EXISTS agent_conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(96) NOT NULL,
  state_json TEXT NOT NULL,
  last_action VARCHAR(32),
  turn_count INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_agent_conversation (openid, conversation_id),
  KEY idx_agent_conversation_updated (openid, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锅仔服务端对话状态';
