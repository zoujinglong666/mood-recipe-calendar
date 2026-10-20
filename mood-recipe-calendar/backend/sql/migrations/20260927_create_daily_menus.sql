-- 今日菜单缓存表（TodayBoard）建表迁移。
-- 运行方式（指定库名，无需在脚本内 USE）：
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < sql/migrations/20260927_create_daily_menus.sql
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 注意：meal_type 列由后续的 20261017_add_daily_menu_meal_type.sql 追加。
CREATE TABLE IF NOT EXISTS daily_menus (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  menu_date   DATE         NOT NULL COMMENT '菜单归属日期',
  recipe_id   BIGINT       NOT NULL COMMENT '当日菜单对应的菜谱',
  guozai_line VARCHAR(200) COMMENT '锅仔今日一句话',
  source      VARCHAR(16)  NOT NULL DEFAULT 'POOL' COMMENT 'POOL=菜谱池挑选 AI=生成（预留）',
  variant     INT          NOT NULL DEFAULT 0 COMMENT '当日换了几次',
  created_at  DATETIME,
  updated_at  DATETIME,
  UNIQUE KEY uk_daily_menu_openid_date (openid, menu_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='今日菜单缓存（TodayBoard）';

SELECT 'daily_menus ready' AS status;
