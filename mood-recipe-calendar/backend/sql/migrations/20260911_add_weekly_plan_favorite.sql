-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
ALTER TABLE weekly_meal_plans
  ADD COLUMN favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏' AFTER shopping_json;
