SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'usage_quotas'
    AND COLUMN_NAME = 'last_request_id'
);
SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE usage_quotas ADD COLUMN last_request_id VARCHAR(96) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
