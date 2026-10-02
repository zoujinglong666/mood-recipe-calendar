SET @turn_json_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_conversations' AND COLUMN_NAME = 'turn_json'
);
SET @turn_json_sql := IF(
  @turn_json_exists = 0,
  'ALTER TABLE agent_conversations ADD COLUMN turn_json TEXT NULL AFTER state_json',
  'SELECT 1'
);
PREPARE turn_json_stmt FROM @turn_json_sql;
EXECUTE turn_json_stmt;
DEALLOCATE PREPARE turn_json_stmt;

SET @transcript_json_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_conversations' AND COLUMN_NAME = 'transcript_json'
);
SET @transcript_json_sql := IF(
  @transcript_json_exists = 0,
  'ALTER TABLE agent_conversations ADD COLUMN transcript_json TEXT NULL AFTER turn_json',
  'SELECT 1'
);
PREPARE transcript_json_stmt FROM @transcript_json_sql;
EXECUTE transcript_json_stmt;
DEALLOCATE PREPARE transcript_json_stmt;

UPDATE agent_conversations SET turn_json = '{}' WHERE turn_json IS NULL;
UPDATE agent_conversations SET transcript_json = '[]' WHERE transcript_json IS NULL;

ALTER TABLE agent_conversations
  MODIFY COLUMN turn_json TEXT NOT NULL,
  MODIFY COLUMN transcript_json TEXT NOT NULL;
