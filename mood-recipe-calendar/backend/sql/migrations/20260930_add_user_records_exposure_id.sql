-- 2026-09-30 用户伙食记录持久化推荐曝光ID
-- 已部署数据库执行一次：为 user_records 增加 exposure_id 列，保存记录时持久化推荐曝光ID，
-- 供前端编辑回填与推荐反馈关联（修复 RecordItem.exposureId 恒为 undefined 的数据模型缺失）。
-- 幂等：列已存在则跳过。

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_records' AND COLUMN_NAME = 'exposure_id');

SET @sql := IF(@exist = 0,
    'ALTER TABLE user_records ADD COLUMN exposure_id VARCHAR(36) NULL COMMENT ''关联推荐曝光ID（recommendation_exposures.id）'' AFTER recipe_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
