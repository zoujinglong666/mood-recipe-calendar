CREATE TABLE IF NOT EXISTS pending_asset_deletions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  object_url TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_asset_deletion_status (status, attempts)
);
