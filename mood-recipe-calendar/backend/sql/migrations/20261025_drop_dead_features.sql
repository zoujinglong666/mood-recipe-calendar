-- ===== 2026-10-25 死功能下线清理 =====
-- 背景：本周第一个任务=删除。下线 theme-albums 主题专辑死页（零入口）、
-- nutrition-report 营养报告死页（零入口）、gallery 周边商城（商品/订单）。
-- 保留：每日签到（checkins，对话/三餐赠礼的总闸门）、分享链（share_records）。
-- 执行方式：在 mood_recipe 库手动执行；幂等（IF EXISTS）。
-- 注意：theme_albums 对应的 virtual_products 三行主题 SKU 已从 init.sql 种子移除，
--       线上存量行保留不影响已购用户权益，可另行手工清理。

DROP TABLE IF EXISTS theme_albums;
DROP TABLE IF EXISTS shop_orders;
DROP TABLE IF EXISTS products;
