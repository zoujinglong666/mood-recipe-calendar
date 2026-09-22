-- 2026-09-22 虚拟支付发货推送重试支持
-- 已部署数据库执行一次：为 virtual_orders 增加「是否已回传微信发货推送」与「重试次数」字段。
-- 幂等：字段已存在则跳过。

SET @exist_wx := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_orders' AND COLUMN_NAME = 'wx_notified');
SET @exist_att := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_orders' AND COLUMN_NAME = 'notify_attempts');

SET @sql := IF(@exist_wx = 0,
    'ALTER TABLE virtual_orders ADD COLUMN wx_notified TINYINT NOT NULL DEFAULT 0 COMMENT ''是否已成功回传微信发货推送''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(@exist_att = 0,
    'ALTER TABLE virtual_orders ADD COLUMN notify_attempts INT NOT NULL DEFAULT 0 COMMENT ''发货推送重试次数''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
