-- 冰箱临期提醒定时任务：记录每个食材最近一次收到提醒的日期（限频，同一天不重复打扰）
ALTER TABLE fridge_items
    ADD COLUMN expiry_notified_on DATE NULL AFTER note;
