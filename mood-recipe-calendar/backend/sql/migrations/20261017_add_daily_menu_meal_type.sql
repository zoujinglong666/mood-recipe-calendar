-- 今日菜单按餐别智能推荐：记录生成时的餐别窗口，跨餐自动换菜（早/午/晚/夜宵各推各的）
ALTER TABLE daily_menus
    ADD COLUMN meal_type VARCHAR(20) NULL AFTER source,
    ADD INDEX idx_daily_menu_date_openid (menu_date, openid);
