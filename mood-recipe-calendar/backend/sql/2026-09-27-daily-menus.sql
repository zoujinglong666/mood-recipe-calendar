-- ============================================================
-- v2 · TodayBoard 每日菜单缓存表（2026-09-27）
-- 用法：
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < sql/2026-09-27-daily-menus.sql
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行
-- ============================================================
USE mood_recipe;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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
