-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
-- 主题菜谱专辑：内容编排层（theme_albums）+ 虚拟商品（virtual_products）。
-- 购买/解锁完全复用既有虚拟支付链路，user_entitlements.valid_days=0 → 永久有效。

CREATE TABLE IF NOT EXISTS theme_albums (
  sku           VARCHAR(64) PRIMARY KEY COMMENT '对应 virtual_products.sku',
  title         VARCHAR(100) NOT NULL COMMENT '专辑标题',
  subtitle      VARCHAR(200) COMMENT '副标题',
  cover         VARCHAR(500) COMMENT '封面图（/static 相对路径）',
  description   TEXT COMMENT '专辑介绍',
  recipe_ids    TEXT COMMENT '菜谱ID JSON 数组',
  preview_count INT NOT NULL DEFAULT 3 COMMENT '未购买可预览道数',
  sort_order    INT NOT NULL DEFAULT 0,
  active        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='主题菜谱专辑（内容编排层）';

INSERT IGNORE INTO theme_albums (sku, title, subtitle, cover, description, preview_count, sort_order, active, created_at) VALUES
('THEME_FATLOSS_21D', '减脂 21 天', '清淡、低卡、不挨饿', '/static/guozai/action_07_empty.png', '锅仔按减脂餐的常见思路挑出 21 道快手清淡菜：多蒸煮、少红烧，简单到不需要意志力。一次购买，长期可看。', 3, 1, 1, NOW()),
('THEME_SOLO_COMFORT_30', '一人食治愈 30 道', '一碗热汤，接住一整天的疲惫', '/static/guozai/action_02_soup.png', '一个人也要好好吃饭。30 道小份量的家常治愈菜，有面有汤有一锅炖，10 分钟也能吃得踏实。一次购买，长期可看。', 3, 2, 1, NOW()),
('THEME_NEW_YEAR_FEAST_18', '过年家宴 18 道', '镇得住一桌子人', '/static/guozai/action_09_celebrate.png', '18 道拿得出手的宴客菜：蒸鱼、油焖大虾、梅菜扣肉……按家宴节奏排好，提前一天照着备料就行。一次购买，长期可看。', 3, 3, 1, NOW());

-- 按菜名从菜谱库编排（同名菜只取最早入库的一个 id）
UPDATE theme_albums SET recipe_ids = (
  SELECT JSON_ARRAYAGG(id) FROM (
    SELECT MIN(id) AS id FROM recipes WHERE name IN
    ('蒜蓉西兰花','清蒸鲈鱼','凉拌黄瓜','番茄炒蛋','青椒炒蛋','黄瓜炒鸡蛋','酸辣土豆丝','蒸蛋羹','小葱拌豆腐','白菜炖豆腐','皮蛋豆腐','鲫鱼豆腐汤','香菇滑鸡','洋葱炒蛋','蛋炒饭','葱花鸡蛋面','苦瓜炒蛋','荷包蛋烧豆腐','木耳炒肉','青椒肉丝','鱼香豆腐')
    GROUP BY name
  ) t
) WHERE sku = 'THEME_FATLOSS_21D';

UPDATE theme_albums SET recipe_ids = (
  SELECT JSON_ARRAYAGG(id) FROM (
    SELECT MIN(id) AS id FROM recipes WHERE name IN
    ('葱花鸡蛋面','番茄牛腩汤','银耳莲子羹','咖喱鸡肉饭','黄焖鸡米饭','蒸蛋羹','蛋炒饭','麻婆豆腐','酸辣土豆丝','可乐鸡翅','糖醋里脊','蒜蓉粉丝蒸虾','宫保鸡丁','鱼香肉丝','回锅肉','小炒肉','蒜苔炒肉','青椒炒蛋','黄瓜炒鸡蛋','洋葱炒蛋','虎皮鸡蛋','荷包蛋烧豆腐','白菜炖豆腐','鲫鱼豆腐汤','皮蛋豆腐','小葱拌豆腐','红烧豆腐','家常豆腐','客家酿豆腐','冻豆腐炖白菜')
    GROUP BY name
  ) t
) WHERE sku = 'THEME_SOLO_COMFORT_30';

UPDATE theme_albums SET recipe_ids = (
  SELECT JSON_ARRAYAGG(id) FROM (
    SELECT MIN(id) AS id FROM recipes WHERE name IN
    ('红烧肉','糖醋排骨','清蒸鲈鱼','油焖大虾','蒜蓉粉丝蒸虾','可乐鸡翅','红烧排骨','黄焖鸡米饭','梅菜扣肉','东坡肉','锅包肉','咕咾肉','京酱肉丝','回锅肉','辣子鸡','糖醋里脊','宫保鸡丁','鱼香肉丝')
    GROUP BY name
  ) t
) WHERE sku = 'THEME_NEW_YEAR_FEAST_18';

-- 虚拟商品 SKU：entitlement_amount=0（不限次访问）+ valid_days=0（永久）
INSERT IGNORE INTO virtual_products
  (sku, platform_item_id, title, description, price_fen, benefits, entitlement_code, entitlement_amount, valid_days, active, sort_order, created_at)
VALUES
  ('THEME_FATLOSS_21D', NULL, '减脂 21 天', '21 道清淡低卡快手菜，一次购买长期可看。', 190, NULL, 'THEME_FATLOSS_21D', 0, 0, 1, 4, NOW()),
  ('THEME_SOLO_COMFORT_30', NULL, '一人食治愈 30 道', '30 道小份治愈家常菜，一次购买长期可看。', 290, NULL, 'THEME_SOLO_COMFORT_30', 0, 0, 1, 5, NOW()),
  ('THEME_NEW_YEAR_FEAST_18', NULL, '过年家宴 18 道', '18 道镇得住场面的宴客菜，一次购买长期可看。', 490, NULL, 'THEME_NEW_YEAR_FEAST_18', 0, 0, 1, 6, NOW());

-- 价格调整（1.9 / 2.9 / 4.9 三档）：INSERT IGNORE 对已存在 SKU 不生效，已部署库需显式更新
UPDATE virtual_products SET price_fen = 190 WHERE sku = 'THEME_FATLOSS_21D';
UPDATE virtual_products SET price_fen = 290 WHERE sku = 'THEME_SOLO_COMFORT_30';
UPDATE virtual_products SET price_fen = 490 WHERE sku = 'THEME_NEW_YEAR_FEAST_18';
