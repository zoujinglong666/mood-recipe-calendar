-- ============================================================
-- 心情菜谱日历 · MySQL 初始化脚本（MySQL 8.0+）
-- 使用方式：
--   mysql -uroot -p < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS mood_recipe
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE mood_recipe;

-- ---------- 用户表 ----------
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid        VARCHAR(64)  NOT NULL UNIQUE COMMENT '微信 openid',
  nickname      VARCHAR(50)  COMMENT '昵称',
  avatar_url    VARCHAR(255) COMMENT '头像',
  first_use_date DATE        COMMENT '首次使用日期',
  is_member     TINYINT DEFAULT 0 COMMENT '是否会员',
  member_expire DATETIME     COMMENT '会员到期时间',
  remind_time   VARCHAR(10) DEFAULT '20:00' COMMENT '每日提醒时间',
  created_at    DATETIME,
  updated_at    DATETIME,
  INDEX idx_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------- 每日记录表 ----------
CREATE TABLE IF NOT EXISTS user_records (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid       VARCHAR(64)   COMMENT '微信 openid',
  image_url    TEXT          COMMENT '菜品图片地址',
  dish_name    VARCHAR(100)  COMMENT '菜名',
  mood_tag     VARCHAR(20)   COMMENT '心情标签',
  note         VARCHAR(200)  COMMENT '心情日记',
  recipe_id    BIGINT        COMMENT '关联推荐菜谱ID',
  cooking_time INT           COMMENT '烹饪分钟数',
  record_date  VARCHAR(20)   COMMENT '记录日期 YYYY-MM-DD',
  created_at   DATETIME,
  updated_at   DATETIME,
  INDEX idx_openid_date (openid, record_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户每日伙食记录';

-- ---------- 菜谱库 ----------
CREATE TABLE IF NOT EXISTS recipes (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) COMMENT '菜名',
  description  VARCHAR(200) COMMENT '一句话简介',
  image        VARCHAR(255) COMMENT '菜品图片',
  ingredients  TEXT COMMENT '食材清单，JSON数组',
  steps        TEXT COMMENT '做法步骤，JSON数组',
  cooking_time INT COMMENT '烹饪分钟数',
  difficulty   VARCHAR(20) COMMENT '难度',
  mood_tags    VARCHAR(100) COMMENT '匹配心情，逗号分隔',
  season       VARCHAR(20) COMMENT '季节标签',
  created_at   DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜谱库';

-- ---------- 月度画册 ----------
CREATE TABLE IF NOT EXISTS monthly_albums (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid       VARCHAR(64),
  month        VARCHAR(7) COMMENT 'YYYY-MM',
  record_ids   TEXT COMMENT '当月记录ID集合，JSON数组',
  cover_text   VARCHAR(100) COMMENT '封面文案',
  stats        TEXT COMMENT '月度统计，JSON',
  ai_summary   TEXT COMMENT 'AI 月度寄语',
  generated_at DATETIME,
  UNIQUE KEY uk_openid_month (openid, month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='月度画册';

-- ---------- 初始菜谱数据（20道） ----------
INSERT INTO recipes (name, description, image, ingredients, steps, cooking_time, difficulty, mood_tags, season, created_at) VALUES
('番茄牛腩汤', '疲惫的时候，一碗热汤比任何话都暖', '/static/dish_tomato_beef.png',
 '["番茄 2个","牛腩 500g","洋葱 1个","姜片 3片"]',
 '["牛腩焯水去沫","番茄去皮切块","热油炒香洋葱和番茄","加入牛腩和水炖1.5小时","加盐调味出锅"]',
 90, '中等', '疲惫,低落', '秋季', NOW()),
('番茄炒蛋', '简单又下饭，厨房小白也能搞定', '/static/dish_fanqiechaodan.png',
 '["番茄 2个","鸡蛋 3个","葱花 少许","盐 适量"]',
 '["鸡蛋打散炒熟盛出","番茄切块炒出汁","倒入鸡蛋翻炒","加盐调味撒葱花"]',
 15, '简单', '开心,平静', '四季', NOW()),
('红烧肉', '想家的时候，一碗红烧肉就是乡愁', '/static/dish_hongshaorou.png',
 '["五花肉 500g","冰糖 30g","生抽 2勺","老抽 1勺","料酒 1勺","姜片 3片"]',
 '["五花肉切块焯水","冰糖炒糖色","下肉翻炒上色","加调料和水炖1小时","大火收汁"]',
 60, '中等', '想家,开心', '四季', NOW()),
('青椒肉丝', '快手家常菜，10分钟上桌', '/static/dish_qingjiaorousi.png',
 '["青椒 2个","猪肉 200g","蒜末 少许","生抽 1勺","淀粉 1勺"]',
 '["肉丝用淀粉生抽腌制","热油滑炒肉丝盛出","青椒切丝翻炒","倒入肉丝调味出锅"]',
 20, '简单', '平静,嘴馋', '四季', NOW()),
('酸辣土豆丝', '酸爽开胃，米饭杀手', '/static/dish_potato.png',
 '["土豆 2个","干辣椒 3个","醋 2勺","蒜 2瓣"]',
 '["土豆切丝泡水去淀粉","热油爆香干辣椒蒜末","下土豆丝大火翻炒","加醋盐调味出锅"]',
 15, '简单', '开心,嘴馋', '四季', NOW()),
('葱花鸡蛋面', '深夜的一碗面，治愈所有疲惫', '/static/dish_noodle.png',
 '["面条 1把","鸡蛋 1个","葱花 少许","酱油 1勺","香油 几滴"]',
 '["水开下面条","碗里放酱油葱花","面条捞入碗中","煎个荷包蛋铺上"]',
 10, '简单', '疲惫,难过', '四季', NOW()),
('冬瓜排骨汤', '清淡鲜美，夏天喝最舒服', '',
 '["排骨 300g","冬瓜 300g","姜片 3片","盐 适量"]',
 '["排骨焯水洗净","加水和姜片炖40分钟","下冬瓜再炖20分钟","加盐调味"]',
 60, '简单', '平静,疲惫', '夏季', NOW()),
('可乐鸡翅', '甜咸适口，小朋友最爱', '',
 '["鸡翅 8个","可乐 1罐","生抽 2勺","姜片 3片"]',
 '["鸡翅划刀焯水","热油煎至两面金黄","倒入可乐和生抽","大火收汁"]',
 30, '简单', '开心,嘴馋', '四季', NOW()),
('麻婆豆腐', '麻辣鲜香，超级下饭', '',
 '["嫩豆腐 1块","肉末 100g","豆瓣酱 1勺","花椒粉 少许","葱花 少许"]',
 '["豆腐切块焯水","热油炒香肉末豆瓣酱","加水下豆腐炖5分钟","勾芡撒花椒粉葱花"]',
 20, '中等', '焦虑,嘴馋', '四季', NOW()),
('紫菜蛋花汤', '3分钟搞定的暖胃汤', '',
 '["紫菜 少许","鸡蛋 1个","虾皮 少许","香油 几滴"]',
 '["水烧开放紫菜虾皮","淋入蛋液搅散","加盐香油出锅"]',
 5, '简单', '疲惫,平静', '四季', NOW()),
('蒜蓉西兰花', '健康轻食，减脂期首选', '',
 '["西兰花 1颗","蒜 5瓣","盐 适量","蚝油 1勺"]',
 '["西兰花切小朵焯水","热油爆香蒜末","下西兰花翻炒","加蚝油盐调味"]',
 15, '简单', '平静,开心', '四季', NOW()),
('糖醋里脊', '外酥里嫩，酸甜开胃', '',
 '["里脊肉 300g","番茄酱 3勺","白糖 2勺","醋 1勺","淀粉 适量"]',
 '["里脊切条腌制裹淀粉","油炸至金黄","调糖醋汁","倒入里脊翻炒裹匀"]',
 40, '中等', '开心,嘴馋', '四季', NOW()),
('香菇滑鸡', '鲜嫩多汁，懒人电饭煲菜', '',
 '["鸡腿 2个","香菇 5朵","姜片 3片","生抽 2勺","蚝油 1勺"]',
 '["鸡腿切块腌制","香菇切片","全部放入电饭煲","煮饭键煮熟拌匀"]',
 30, '简单', '疲惫,想家', '四季', NOW()),
('凉拌黄瓜', '清爽解腻，夏天必备', '',
 '["黄瓜 2根","蒜 3瓣","醋 2勺","生抽 1勺","香油 几滴"]',
 '["黄瓜拍碎切段","加蒜末调料","拌匀腌制10分钟"]',
 10, '简单', '平静,开心', '夏季', NOW()),
('蛋炒饭', '剩饭的最佳归宿，粒粒分明', '',
 '["米饭 1碗","鸡蛋 2个","葱花 少许","盐 适量"]',
 '["鸡蛋打散","热油炒散鸡蛋","倒入米饭翻炒","加盐葱花出锅"]',
 10, '简单', '疲惫,平静', '四季', NOW()),
('水煮牛肉', '麻辣鲜香，川菜经典', '',
 '["牛肉 300g","豆芽 200g","豆瓣酱 2勺","干辣椒 1把","花椒 1把"]',
 '["牛肉切片腌制","豆芽焯水铺底","炒香豆瓣酱加水煮牛肉","泼热油激香"]',
 40, '挑战', '焦虑,嘴馋', '四季', NOW()),
('银耳莲子羹', '润燥养颜，女生最爱', '',
 '["银耳 1朵","莲子 30g","红枣 5颗","冰糖 适量"]',
 '["银耳泡发撕小朵","所有材料入锅","小火炖1小时","加冰糖调味"]',
 60, '简单', '难过,低落', '秋季', NOW()),
('咖喱鸡肉饭', '浓郁咖喱，拌米饭绝了', '',
 '["鸡胸肉 200g","土豆 1个","胡萝卜 1根","咖喱块 2块","洋葱 半个"]',
 '["鸡肉蔬菜切块","热油炒香洋葱","下鸡肉蔬菜翻炒","加水炖15分钟加咖喱"]',
 30, '简单', '开心,嘴馋', '四季', NOW()),
('蒜蓉粉丝蒸虾', '宴客硬菜，简单又有面', '',
 '["鲜虾 10只","粉丝 1把","蒜 10瓣","生抽 1勺","蒸鱼豉油 1勺"]',
 '["虾开背去虾线","粉丝泡软铺底","蒜茸铺在虾上","蒸8分钟淋热油"]',
 20, '中等', '开心,想家', '四季', NOW()),
('汤圆', '团圆的味道，甜在心里', '',
 '["糯米粉 200g","黑芝麻馅 100g","温水 适量"]',
 '["糯米粉加温水揉成团","包入芝麻馅","水开下汤圆煮至浮起"]',
 20, '简单', '想家,难过', '冬季', NOW());

-- ---------- 周边商品表（锅仔形象馆·周边商城） ----------
CREATE TABLE IF NOT EXISTS products (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(100) COMMENT '商品名称',
  image          TEXT         COMMENT '商品图片',
  price          DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '原价（元）',
  exchange_price DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT '1元兑换价',
  category       VARCHAR(30)  COMMENT '分类：cup/clothes/kitchen/...',
  description    TEXT         COMMENT '商品简介',
  stock          INT DEFAULT 0 COMMENT '库存',
  sort_order     INT DEFAULT 0 COMMENT '排序权重',
  is_active      TINYINT DEFAULT 1 COMMENT '是否上架',
  created_at     DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='锅仔周边商品';

-- ---------- 签到记录表（锅仔形象馆·签到福利） ----------
CREATE TABLE IF NOT EXISTS checkins (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid       VARCHAR(64) COMMENT '微信 openid',
  checkin_date VARCHAR(20) COMMENT '签到日期 YYYY-MM-DD',
  created_at   DATETIME,
  UNIQUE KEY uk_openid_date (openid, checkin_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='签到记录';

-- ---------- 周边订单表（锅仔形象馆·购买/兑换） ----------
CREATE TABLE IF NOT EXISTS shop_orders (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no      VARCHAR(40) NOT NULL UNIQUE COMMENT '业务订单号',
  openid        VARCHAR(64) COMMENT '微信 openid',
  product_id    BIGINT COMMENT '商品ID',
  product_name  VARCHAR(100) COMMENT '商品名称',
  product_image TEXT COMMENT '商品图片',
  amount        DECIMAL(10,2) COMMENT '实付金额（元）',
  pay_type      VARCHAR(20) DEFAULT 'normal' COMMENT 'normal 原价 / exchange 1元兑换',
  status        VARCHAR(20) DEFAULT 'pending' COMMENT 'pending 待支付 / paid 已支付 / cancelled 已取消',
  created_at    DATETIME,
  paid_at       DATETIME,
  INDEX idx_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='周边订单';

-- ---------- 初始周边商品数据（锅仔形象馆） ----------
INSERT INTO products (name, image, price, exchange_price, category, description, stock, sort_order, is_active, created_at) VALUES
('锅仔搪瓷杯',        '/static/guozai/mood_01_happy.png',    39.00, 1.00, 'cup',     '把锅仔带在身边，喝水也有好心情', 100, 1, 1, NOW()),
('锅仔帆布袋',        '/static/guozai/action_07_empty.png',  49.00, 1.00, 'bag',     '背着锅仔去买菜，治愈整个通勤路', 100, 2, 1, NOW()),
('锅仔保温杯',        '/static/guozai/mood_05_sad.png',      69.00, 1.00, 'cup',     '冬日暖心伴侣，让每杯热水都有温度', 80,  3, 1, NOW()),
('锅仔围裙',          '/static/guozai/action_02_soup.png',   59.00, 1.00, 'clothes', '做饭仪式感拉满，锅仔陪你下厨',   60,  4, 1, NOW()),
('锅仔搪瓷碗套装',    '/static/guozai/action_01_bowl.png',   79.00, 1.00, 'kitchen', '一套温暖饭碗，好好吃饭每一天',   50,  5, 1, NOW()),
('锅仔手机壳',        '/static/guozai/mood_08_homesick.png', 29.00, 1.00, 'digital', '锅仔替你挡住生活的磕磕碰碰',     200, 6, 1, NOW());
