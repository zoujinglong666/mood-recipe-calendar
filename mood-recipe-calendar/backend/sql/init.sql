-- ============================================================
-- 心情菜谱日历 · MySQL 初始化脚本（部署版，MySQL 8.0+）
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
  session_key_encrypted VARCHAR(512) COMMENT '加密保存的微信 session_key，仅供服务端虚拟支付签名使用',
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
  ai_summary   TEXT COMMENT '锅仔月度寄语',
  generated_at DATETIME,
  UNIQUE KEY uk_openid_month (openid, month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='月度画册';

-- ---------- 用户口味偏好 ----------
CREATE TABLE IF NOT EXISTS user_food_preferences (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid               VARCHAR(64) NOT NULL UNIQUE COMMENT '微信 openid',
  favorite_tags        VARCHAR(500) DEFAULT '' COMMENT '喜欢的口味标签',
  favorite_cuisines    VARCHAR(500) DEFAULT '' COMMENT '喜欢的菜系',
  favorite_dishes      VARCHAR(500) DEFAULT '' COMMENT '喜欢的菜品',
  avoid_ingredients    VARCHAR(500) DEFAULT '' COMMENT '忌口食材',
  allergens            VARCHAR(500) DEFAULT '' COMMENT '过敏原',
  eat_scallion         TINYINT(1) COMMENT '是否吃葱',
  eat_cilantro         TINYINT(1) COMMENT '是否吃香菜',
  spice_level          VARCHAR(16) DEFAULT 'NORMAL' COMMENT '辣度 NORMAL/MILD/SPICY/NONE',
  health_goal          VARCHAR(16) DEFAULT 'BALANCED' COMMENT '健康目标 BALANCED/LOW_CAL/HIGH_PROTEIN',
  onboarding_completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否完成口味引导',
  updated_at           DATETIME,
  INDEX idx_pref_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户口味偏好';

-- ---------- 每周备餐计划 ----------
CREATE TABLE IF NOT EXISTS weekly_meal_plans (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid        VARCHAR(64) NOT NULL COMMENT '微信 openid',
  plan_json     TEXT NOT NULL COMMENT '一周菜单计划JSON',
  shopping_json TEXT NOT NULL COMMENT '买菜清单JSON',
  favorite      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏',
  agent_json    TEXT COMMENT '智能体审计JSON：质量分/降级原因/用到的记忆/traceId',
  created_at    DATETIME,
  updated_at    DATETIME,
  INDEX idx_weekly_plan_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每周备餐计划';

-- ---------- 智能体事实记忆 ----------
CREATE TABLE IF NOT EXISTS agent_memory_facts (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid        VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  memory_key    VARCHAR(48)  NOT NULL COMMENT '事实键，如 spice / household / affinity.赣菜',
  memory_value  VARCHAR(200) NOT NULL COMMENT '事实值',
  source        VARCHAR(16)  NOT NULL DEFAULT 'CHAT' COMMENT '来源 EXPLICIT/CHAT/INFERRED/BEHAVIOR/LEARNED',
  confidence    DOUBLE       NOT NULL DEFAULT 0.6 COMMENT '置信度 0~1，随时间衰减',
  evidence      VARCHAR(500) COMMENT '这条记忆的依据，用于解释',
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  hit_count     INT          NOT NULL DEFAULT 0 COMMENT '被引用次数',
  last_used_at  DATETIME,
  expires_at    DATETIME COMMENT '有时效的事实的过期时间',
  updated_at    DATETIME     NOT NULL,
  UNIQUE KEY uk_agent_memory (openid, memory_key),
  INDEX idx_agent_memory_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='智能体事实记忆';

-- ---------- 周菜单执行反馈 ----------
CREATE TABLE IF NOT EXISTS plan_dish_outcomes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  plan_id     BIGINT       NOT NULL COMMENT '周计划 id',
  day_index   INT          NOT NULL COMMENT '第几天',
  dish_index  INT          NOT NULL COMMENT '当天的第几道菜',
  dish_name   VARCHAR(120) NOT NULL COMMENT '菜名',
  cooked      TINYINT(1) COMMENT '是否做了 null=未反馈',
  leftover    TINYINT(1) COMMENT '是否剩了很多',
  too_hard    TINYINT(1) COMMENT '是否太难做',
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  UNIQUE KEY uk_plan_dish (openid, plan_id, day_index, dish_index),
  INDEX idx_plan_dish_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='周菜单执行反馈';


-- ---------- 菜谱推荐曝光记录 ----------
CREATE TABLE IF NOT EXISTS recommendation_exposures (
  id         VARCHAR(36) PRIMARY KEY COMMENT 'UUID',
  openid     VARCHAR(64) NOT NULL COMMENT '微信 openid',
  dish_key   VARCHAR(64) NOT NULL COMMENT '菜品标识',
  source     VARCHAR(16) NOT NULL COMMENT '推荐来源',
  liked      TINYINT(1) NOT NULL DEFAULT 0,
  disliked   TINYINT(1) NOT NULL DEFAULT 0,
  made       TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_recommendation_exposure_user_time (openid, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜谱推荐曝光记录';

-- ---------- 菜谱交互行为 ----------
CREATE TABLE IF NOT EXISTS recipe_interactions (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid     VARCHAR(64) COMMENT '微信 openid',
  recipe_id  BIGINT NOT NULL COMMENT '菜谱ID',
  action     VARCHAR(16) NOT NULL COMMENT 'LIKE/DISLIKE/MADE/SHOWN',
  created_at DATETIME NOT NULL,
  INDEX idx_recipe_interaction_user_recipe (openid, recipe_id),
  INDEX idx_recipe_interaction_user_time (openid, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜谱交互行为';

-- ---------- 用户反馈 ----------
CREATE TABLE IF NOT EXISTS user_feedback (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid     VARCHAR(64) NOT NULL COMMENT '微信 openid',
  category   VARCHAR(32) NOT NULL COMMENT '反馈分类',
  content    VARCHAR(1000) NOT NULL COMMENT '反馈内容',
  contact    VARCHAR(100) COMMENT '联系方式',
  status     VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/CLOSED',
  created_at DATETIME,
  INDEX idx_feedback_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户反馈';

-- ---------- 运营事件 ----------
CREATE TABLE IF NOT EXISTS operational_events (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  severity   VARCHAR(12) NOT NULL COMMENT 'INFO/WARN/ERROR',
  openid     VARCHAR(64) COMMENT '关联用户',
  order_no   VARCHAR(64) COMMENT '关联订单',
  detail     VARCHAR(200) COMMENT '详情',
  created_at DATETIME,
  INDEX idx_op_event_type_time (event_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='运营事件';

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
  INDEX idx_shop_order_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='周边订单';

-- ---------- 虚拟商品与权益 ----------
CREATE TABLE IF NOT EXISTS virtual_products (
  sku                VARCHAR(64) PRIMARY KEY COMMENT '业务 SKU',
  platform_item_id   VARCHAR(128) COMMENT '微信虚拟支付后台道具 ID',
  title              VARCHAR(100) NOT NULL,
  description        TEXT,
  price_fen          INT NOT NULL COMMENT '价格，分',
  entitlement_code   VARCHAR(64) NOT NULL COMMENT '发放的权益代码',
  entitlement_amount INT NOT NULL DEFAULT 0 COMMENT '次数型权益数量',
  valid_days         INT NOT NULL DEFAULT 0 COMMENT '有效天数，0 为永久',
  active             TINYINT NOT NULL DEFAULT 1,
  sort_order         INT NOT NULL DEFAULT 0,
  created_at         DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='虚拟商品目录';

CREATE TABLE IF NOT EXISTS virtual_orders (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no                VARCHAR(48) NOT NULL UNIQUE,
  openid                  VARCHAR(64) NOT NULL,
  sku                     VARCHAR(64) NOT NULL,
  amount_fen              INT NOT NULL,
  status                  VARCHAR(20) NOT NULL COMMENT 'PENDING/PAID/DELIVERED/REFUNDED/CANCELLED',
  platform_transaction_id VARCHAR(128),
  created_at              DATETIME,
  paid_at                 DATETIME,
  delivered_at            DATETIME,
  INDEX idx_virtual_order_openid (openid),
  INDEX idx_virtual_order_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='虚拟商品订单';

CREATE TABLE IF NOT EXISTS user_entitlements (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid          VARCHAR(64) NOT NULL,
  code            VARCHAR(64) NOT NULL,
  remaining_uses  INT,
  source_order_no VARCHAR(48) NOT NULL UNIQUE,
  expires_at      DATETIME,
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME,
  INDEX idx_entitlement_openid_code (openid, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户虚拟权益';

-- ============================================================
-- 初始数据
-- ============================================================

-- ---------- 24道高质量菜谱（覆盖12种心情） ----------
INSERT INTO recipes (name, description, image, ingredients, steps, cooking_time, difficulty, mood_tags, season, created_at) VALUES
('番茄炒蛋', '红黄相间的温暖，是厨房小白也能搞定的幸福', 'https://aka.doubaocdn.com/s/uTUIkGVcFP',
 '["番茄 2个（约300g）","鸡蛋 3个","葱花 1小把","盐 2g","白糖 3g","食用油 2勺"]',
 '["鸡蛋打入碗中，加少许盐搅打至起泡","番茄去蒂切块，喜欢去皮的可以开水烫一下","热锅冷油，油热后倒入蛋液，大火快速划散至刚凝固盛出","锅中留底油，下番茄块中火翻炒出汁","加白糖和盐调味，炒至番茄软烂出红油","倒入炒好的鸡蛋，快速翻炒均匀","撒上葱花，出锅装盘"]',
 15, '简单', '开心,平静', '四季', NOW()),
('红烧肉', '浓油赤酱的一碗乡愁，肥而不腻入口即化（来源：下厨房）', 'https://i2.chuimg.com/11f3fef092f6480d823b3966c1edbf94_1920w_2560h.jpg',
 '["带皮五花肉 1000g（最好三层五花）","冰糖 45g","姜片 8片","八角 2个","香叶 3片","大葱 2段","黄酒 35ml","老抽 10ml","生抽 40ml","桂皮 1小块","盐 2g","开水 适量"]',
 '["五花肉切3cm左右方块","冷水下锅，加4片姜片、15ml黄酒，大火煮开2-3分钟，捞出用温水冲洗干净沥干","不放油或少放油，五花肉中小火煸至表面微黄盛出","锅中留少量油，放冰糖小火慢慢炒至融化","糖液变成琥珀色冒细密小泡时，立即放入五花肉快速翻炒上色","加姜片、葱段、八角、香叶、桂皮炒香，加生抽、老抽、盐、剩下的20ml黄酒翻炒均匀","倒入热水没过肉约1cm，大火烧开后转最小火炖50分钟","挑出香料，大火收汁至浓稠均匀裹在肉上，不要收太干"]',
 70, '中等', '想家,开心', '四季', NOW()),
('番茄牛腩汤', '一碗热汤下肚，所有疲惫都被温柔接住', 'https://aka.doubaocdn.com/s/7psLK7G6u5',
 '["牛腩 500g","番茄 3个","洋葱 半个","姜片 3片","番茄酱 2勺","盐 适量","黑胡椒 少许","食用油 1勺"]',
 '["牛腩切3cm块，冷水下锅焯水，撇去浮沫捞出","番茄顶部划十字，开水烫后去皮切块","锅中放油，下洋葱碎和姜片炒香","加入番茄块中火翻炒至出汁","加番茄酱继续炒1分钟","放入牛腩，加入没过食材的开水","大火烧开后转小火炖90分钟","加盐和黑胡椒调味，出锅"]',
 90, '中等', '疲惫,低落', '秋冬', NOW()),
('麻婆豆腐', '麻辣鲜香的一勺，让焦虑随热气消散（来源：下厨房）', 'https://i2.chuimg.com/f26a6015f9e94b9591bce6e685b2646e_1280w_1706h.jpg',
 '["嫩豆腐 1块（约400g）","牛肉末 100g","豆瓣酱 1勺","花椒 1把","蒜 4瓣","姜 2片","小葱 1根","生抽 1勺","白胡椒粉 1小勺","味精 1小勺","淀粉 1勺","辣椒面 1勺","白糖 半勺"]',
 '["豆腐切丁，肉切小丁，蒜切蒜泥，姜切丁，小葱切葱粒","水开加勺盐，倒豆腐煮1分钟捞出备用","锅里不放油，下花椒小火炒香，擀碎备用","锅里少许油，下肉末炒至8成熟","加豆瓣酱小火炒出红油","倒蒜姜泥和1勺辣椒面炒香","加生抽炒香（可放半勺老抽提色）","加一碗热水转大火，加白糖、味精、白胡椒粉","倒豆腐，大火煮2分钟","淀粉加水调成水淀粉，分3次倒入轻轻拌匀","撒花椒碎和小葱出锅"]',
 20, '中等', '焦虑,嘴馋', '四季', NOW()),
('酸辣土豆丝', '酸爽脆嫩的一口，是米饭的最佳搭档', 'https://aka.doubaocdn.com/s/uiQUhNdf5z',
 '["土豆 2个（约300g）","青椒 半个","干辣椒 5个","蒜 3瓣","白醋 2勺","盐 适量","食用油 2勺"]',
 '["土豆去皮切细丝，用清水浸泡10分钟去淀粉","青椒切丝，蒜切末，干辣椒剪段","锅中放油，爆香干辣椒和蒜末","捞出土豆丝沥干，下锅大火快炒1分钟","加青椒丝继续翻炒","沿锅边淋入白醋，加盐调味","大火翻炒均匀，立即出锅"]',
 15, '简单', '开心,嘴馋', '四季', NOW()),
('糖醋里脊', '外酥里嫩酸甜开胃，咬一口嘴角上扬', 'https://aka.doubaocdn.com/s/8V7cSYEWxc',
 '["里脊肉 300g","番茄酱 3勺","白糖 2勺","白醋 1勺","生抽 1勺","淀粉 50g","鸡蛋 1个","白芝麻 少许","食用油 适量"]',
 '["里脊切1cm宽的条，加生抽和少许盐腌制10分钟","鸡蛋打散，加入淀粉调成稠糊","里脊条裹满面糊","锅中放油烧至六成热，下里脊炸至金黄捞出","油温升至八成热，复炸30秒至酥脆","锅中留少许油，加番茄酱、白糖、白醋和少许水","熬至浓稠，倒入炸好的里脊快速裹匀","撒上白芝麻，出锅"]',
 40, '中等', '开心,嘴馋', '四季', NOW()),
('蒜蓉西兰花', '清爽翠绿的一盘，给身体放个假', 'https://aka.doubaocdn.com/s/ncCunlnOVj',
 '["西兰花 1颗（约400g）","蒜 6瓣","蚝油 1勺","盐 适量","食用油 1勺"]',
 '["西兰花掰成小朵，用盐水浸泡10分钟","蒜切末","锅中烧水，加少许盐和油，下西兰花焯2分钟捞出","锅中放油，下一半蒜末爆香","倒入西兰花大火翻炒","加蚝油和少许盐调味","撒上剩下的蒜末，翻炒均匀出锅"]',
 15, '简单', '平静,开心', '四季', NOW()),
('可乐鸡翅', '甜咸交织的治愈，连骨头都想嗦干净（来源：下厨房）', 'https://aka.doubaocdn.com/s/T7njr1oqww',
 '["鸡翅 500g","可口可乐 250g","八角 0.5个","姜 2块","大葱 1小段","柠檬 小半个","生抽 2勺","老抽 1勺","蚝油 1勺","料酒 1勺","盐 1/3茶匙","白芝麻 少许"]',
 '["鸡翅对半剪开，姜拍破，大葱拍破，加盐、料酒抓出汁水","鸡翅放入盆中抓拌1分钟，腌制10分钟","八角0.5个，3片姜切粗丝，柠檬切二","可乐250g加清水150g备用","腌好的鸡翅冷水下锅，水开后捞出擦干","平底锅抹少量油，鸡翅皮面朝下大火煎至金黄后翻面","煎至两面金黄，加姜丝和八角炒香，转最小火加生抽，再加可乐水","大火煮开，加老抽、蚝油、味精，盖盖转小火炖15分钟","挑出姜丝和八角，大火收汁3-4分钟，挤入柠檬汁翻拌，撒白芝麻出锅"]',
 30, '简单', '开心,嘴馋', '四季', NOW()),
('葱花鸡蛋面', '深夜的一碗面，是最朴素的温柔', 'https://aka.doubaocdn.com/s/vRMNMKV3xT',
 '["面条 1把（约150g）","鸡蛋 1个","葱花 1小把","生抽 1勺","香油 几滴","盐 适量","开水 适量"]',
 '["碗中放入生抽、盐、香油和一半葱花","冲入半碗开水调成汤底","锅中烧水，水开下面条煮3分钟","面条捞入汤底中","锅中放少许油，煎一个荷包蛋","荷包蛋铺在面条上","撒上剩下的葱花，趁热吃"]',
 10, '简单', '疲惫,难过', '四季', NOW()),
('黄焖鸡米饭', '鸡肉软嫩酱香浓郁，配米饭能吃两碗（来源：下厨房）', 'https://i2.chuimg.com/950054414f1944fb960912ee96bec2fb_4032w_3024h.jpg',
 '["鸡腿 350g","干香菇 20g（提前泡发）","土豆 120g","青椒 30g","干辣椒 3g","大蒜 7g","姜 40g","葱 25g","冰糖 8g","八角 1g","黄豆酱 20g","生抽 20g","蚝油 15g","盐 1-2g","料酒 15ml","白胡椒粉 1g","小米醋 8ml"]',
 '["鸡腿肉切块，干香菇提前泡发，土豆切块，葱姜蒜八角辣椒备好","调配焖鸡酱：黄豆酱20g+生抽20g+蚝油15g+少量清水混合均匀","适量食用油，姜片炸香","鸡腿肉煸炒出水分，表面微发黄，加料酒15ml去腥","放大葱段、大蒜、冰糖、干辣椒、八角，倒入调好的料汁翻炒出酱香","加香菇和泡香菇的水，淋入8ml小米醋解腻增香，盖盖小火焖炖15分钟","放土豆继续小火焖10分钟，根据口味放盐和白胡椒粉","待汤汁浓稠可转入砂锅，放青椒焖1分钟关火","配米饭食用，鸡肉软嫩酱香浓郁"]',
 40, '中等', '满足,想家', '四季', NOW()),
('银耳莲子羹', '温润甜糯的一碗，把难过慢慢融化', 'https://aka.doubaocdn.com/s/2lTcBT0T8r',
 '["银耳 1朵","莲子 30g","红枣 6颗","枸杞 1小把","冰糖 适量","清水 适量"]',
 '["银耳提前用冷水泡发2小时","泡发的银耳撕成小朵，去掉根部","莲子去芯，红枣洗净","所有材料放入锅中，加足量清水","大火烧开后转小火炖60分钟","炖至银耳出胶，汤汁浓稠","加冰糖和枸杞，再炖5分钟","出锅，温热食用"]',
 60, '简单', '难过,低落', '秋冬', NOW()),
('咖喱鸡肉饭', '浓郁咖喱裹着米饭，一口就被治愈', 'https://aka.doubaocdn.com/s/O3uIGIBfdC',
 '["鸡胸肉 250g","土豆 1个","胡萝卜 1根","洋葱 半个","咖喱块 2块","米饭 1碗","食用油 1勺","清水 适量"]',
 '["鸡肉切2cm块，土豆胡萝卜切块，洋葱切丁","锅中放油，下洋葱丁炒香","加入鸡肉块翻炒至变色","加土豆和胡萝卜翻炒1分钟","加入没过食材的清水","大火烧开后转小火炖15分钟","放入咖喱块，搅拌至融化","继续炖5分钟至浓稠，浇在米饭上"]',
 30, '简单', '开心,嘴馋', '四季', NOW()),
('香菇滑鸡', '鲜嫩多汁的一锅，懒人也能做出馆子味', 'https://aka.doubaocdn.com/s/DdEa2QrsSA',
 '["鸡腿 2个（约300g）","干香菇 6朵","姜片 3片","生抽 2勺","蚝油 1勺","淀粉 1勺","料酒 1勺","葱花 适量"]',
 '["干香菇用温水泡发，切片","鸡腿去骨切块，加生抽、蚝油、料酒、淀粉腌制20分钟","电饭煲中放少许油，铺姜片","放入腌制好的鸡块","铺上香菇片","倒入泡香菇的水（滤掉杂质）","按煮饭键，煮好后拌匀","撒上葱花，出锅"]',
 35, '简单', '疲惫,想家', '四季', NOW()),
('蒜蓉粉丝蒸虾', '鲜嫩入味的宴客菜，简单又有面子', 'https://aka.doubaocdn.com/s/kYu6qhcC4N',
 '["鲜虾 12只","粉丝 1把","蒜 10瓣","蒸鱼豉油 2勺","食用油 2勺","葱花 适量"]',
 '["粉丝用温水泡软，铺在盘底","虾开背去虾线，平铺在粉丝上","蒜切末，锅中放1勺油，下一半蒜末炸至金黄","与另一半生蒜末混合，铺在虾上","水开后上锅蒸8分钟","淋上蒸鱼豉油","烧1勺热油，浇在蒜蓉上激香","撒上葱花，出锅"]',
 20, '中等', '开心,想家', '四季', NOW()),
('清蒸鲈鱼', '鲜嫩洁白的原汁原味，是对食材最大的尊重', 'https://aka.doubaocdn.com/s/YDVpOcpMU0',
 '["鲈鱼 1条（约500g）","葱丝 适量","姜丝 适量","蒸鱼豉油 2勺","料酒 1勺","食用油 2勺"]',
 '["鲈鱼处理干净，两面各划三刀","鱼身抹少许盐和料酒，腌10分钟","盘底铺姜片，放上鲈鱼","水开后上锅大火蒸8分钟","倒掉蒸出的汤汁，去掉姜片","铺上葱丝和姜丝","淋上蒸鱼豉油","烧2勺热油至冒烟，浇在葱丝上"]',
 20, '简单', '期待,平静', '四季', NOW()),
('宫保鸡丁', '糊辣荔枝味的经典，每一口都有惊喜', 'https://aka.doubaocdn.com/s/f8YTXxmsaj',
 '["鸡胸肉 250g","花生米 50g","干辣椒 8个","花椒 1小勺","葱段 适量","生抽 1勺","醋 1勺","白糖 1勺","淀粉 1勺","食用油 2勺"]',
 '["鸡胸肉切丁，加生抽、淀粉腌制15分钟","调碗汁：生抽、醋、白糖、淀粉、少许水","锅中放油，下花生米小火炸至酥脆盛出","锅中留油，下干辣椒和花椒爆香","下鸡丁大火翻炒至变色","倒入碗汁快速翻炒","加入葱段和花生米","翻炒均匀，立即出锅"]',
 25, '中等', '得意,开心', '四季', NOW()),
('蒜蓉蒸金针菇', '蒜香浓郁鲜嫩下饭，10分钟搞定（来源：下厨房）', 'https://i2.chuimg.com/35d40d2ed4fa42efa2cb8fc9c77cca42_2834w_2919h.jpg',
 '["金针菇 250g","蒜 1头","小米辣 3根","香葱 2小根","生抽 2勺","盐 1小勺","蚝油 1勺","白糖 半勺","蒸鱼豉油 1勺","食用油 适量"]',
 '["金针菇去根部，用清水泡一会洗干净","控水，摆在盘子里","切小料：小米辣、蒜、香葱切碎","锅烧热放入适量底油，把小料煸炒出香味","加蚝油1勺、生抽2勺、盐1小勺、糖半勺，翻炒均匀","把炒好的蒜汁均匀淋在金针菇上","水开后放入蒸锅蒸6-7分钟","蒸好的金针菇会出汤汁，不要倒掉，很鲜美","在上面淋入1勺蒸鱼豉油，撒上小米辣和香葱","完成，鲜香味美超级下饭"]',
 15, '简单', '平静,开心', '四季', NOW()),
('鱼香肉丝', '酸甜咸辣的复合味，没有鱼却有鱼香', 'https://aka.doubaocdn.com/s/2cULmtVndC',
 '["猪里脊 200g","胡萝卜 半根","木耳 5朵","青椒 半个","泡椒 2个","蒜末 1勺","生抽 1勺","醋 1勺","白糖 1勺","淀粉 1勺","食用油 2勺"]',
 '["里脊切丝，加生抽、淀粉腌制10分钟","胡萝卜、木耳、青椒切丝","调碗汁：生抽、醋、白糖、淀粉、少许水","锅中放油，下肉丝滑炒至变色盛出","锅中留油，下泡椒和蒜末炒香","加胡萝卜丝和木耳丝翻炒1分钟","倒入肉丝和青椒丝","淋入碗汁，快速翻炒均匀出锅"]',
 25, '中等', '得意,开心', '四季', NOW()),
('糖醋排骨', '酸甜入骨的小排，是期待已久的味道', 'https://aka.doubaocdn.com/s/GfbbYIFi2u',
 '["猪小排 400g","冰糖 25g","生抽 1勺","老抽 半勺","醋 2勺","料酒 1勺","姜片 3片","白芝麻 少许","食用油 1勺"]',
 '["排骨冷水下锅，加姜片和料酒焯水","捞出沥干，用厨房纸吸干水分","锅中放少许油，下冰糖小火炒糖色","炒至琥珀色，倒入排骨翻炒上色","加生抽、老抽、醋翻炒","加入没过排骨的开水","大火烧开转小火炖30分钟","大火收汁，撒白芝麻出锅"]',
 45, '中等', '期待,开心', '四季', NOW()),
('红烧排骨', '软烂入味的满足，连骨头都想嗦干净', 'https://aka.doubaocdn.com/s/95805vJwXL',
 '["猪排骨 500g","冰糖 20g","生抽 2勺","老抽 1勺","料酒 2勺","姜片 4片","八角 2个","开水 适量","葱花 适量"]',
 '["排骨冷水下锅，加姜片和料酒焯水","捞出沥干","锅中放少许油，下冰糖小火炒糖色","炒至枣红色，倒入排骨翻炒上色","加姜片、八角炒香","倒入生抽、老抽、料酒翻炒","加入没过排骨的开水","大火烧开转小火炖45分钟，大火收汁撒葱花"]',
 50, '中等', '满足,想家', '四季', NOW()),
('蒸蛋羹', '嫩滑如镜的温柔，入口即化的安慰', 'https://aka.doubaocdn.com/s/K3xEnhYo0U',
 '["鸡蛋 2个","温水 150ml（蛋液的1.5倍）","盐 少许","生抽 半勺","香油 几滴","葱花 少许"]',
 '["鸡蛋打入碗中，加少许盐搅打均匀","加入温水，边加边搅拌","用滤网过滤蛋液，去掉气泡","盖上保鲜膜，扎几个小孔","水开后上锅，中小火蒸10分钟","关火焖2分钟","淋上生抽和香油","撒上葱花，出锅"]',
 15, '简单', '害羞,平静', '四季', NOW()),
('凉拌黄瓜', '清爽解腻的一拍，是夏天的味道', 'https://aka.doubaocdn.com/s/zEZO8H1aKS',
 '["黄瓜 2根","蒜 4瓣","小米辣 2个","生抽 2勺","醋 2勺","白糖 1勺","盐 少许","香油 几滴"]',
 '["黄瓜洗净，用刀背拍碎","切成3cm段，加少许盐腌10分钟","蒜切末，小米辣切圈","倒掉腌出的水分","加蒜末、小米辣、生抽、醋、白糖","淋上香油","拌匀，静置5分钟入味","出锅，冰镇后口感更佳"]',
 10, '简单', '平静,开心', '夏季', NOW()),
('蛋炒饭', '粒粒分明的剩饭改造，简单却最踏实', 'https://aka.doubaocdn.com/s/VRht8BibhB',
 '["隔夜米饭 1碗","鸡蛋 2个","葱花 1小把","盐 适量","生抽 半勺","食用油 2勺"]',
 '["鸡蛋打散，加少许盐","锅中放1勺油，倒入蛋液","大火快速划散，刚凝固盛出","锅中再放1勺油，倒入米饭","用铲子把米饭压散，大火翻炒","炒至米饭粒粒分明","倒入炒好的鸡蛋，加生抽和盐","撒上葱花，翻炒均匀出锅"]',
 10, '简单', '疲惫,平静', '四季', NOW()),
('油焖大虾', '红亮入味鲜香带甜，连壳都想嗦干净（来源：下厨房）', 'https://i2.chuimg.com/1e31dd87dfb547d4b501cdce8c12f4cf_2560w_2560h.jpg',
 '["鲜虾 500g","葱 2根","姜 3片","蒜 2瓣","生抽 2勺","老抽 0.5勺","料酒 1勺","白醋 0.5勺","蚝油 1勺","番茄酱 1勺","糖 1勺","水 7勺","猪油或芝麻香油 1勺","食用油 适量"]',
 '["准备葱姜碎和葱白，葱绿留用最后撒","冷冻虾泡冷水解冻3-4分钟，虾能弯曲就捞出处理虾枪虾线虾眼睛","趁虾解冻空档准备料汁：生抽2勺+老抽0.5勺+料酒1勺+白醋0.5勺+蚝油1勺+番茄酱1勺+糖1勺+水7勺","油稍微多一点，热油后下入虾干煎，煎久一点炒香至外皮泛白焦脆","倒入葱姜蒜爆香炒香","倒入料汁，大火煮开","盖盖小火焖煮2分钟","开盖大火收汁，加1勺猪油或芝麻香油把汤汁收浓","撒点葱绿上桌，红亮入味鲜香带甜"]',
 20, '简单', '得意,开心', '四季', NOW());


-- ---------- 100道精选家常菜（来自 docs.cook.ninthfeast.com，AI美食摄影图） ----------
INSERT INTO recipes (name, description, image, ingredients, steps, cooking_time, difficulty, mood_tags, season, created_at) VALUES
('番茄炒蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["番茄2个", "鸡蛋3个", "盐", "糖", "葱花"]', '["番茄切块,鸡蛋打散加盐", "热油炒鸡蛋至半凝固,盛出", "炒番茄出汁,加盐糖", "倒入鸡蛋翻炒均匀,撒葱花"]', 8, '简单', '开心,平静', '四季', NOW()),
('青椒炒蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/EXdka4udbt', '["青椒2个", "鸡蛋3个", "盐"]', '["青椒切块,鸡蛋打散", "炒鸡蛋盛出", "炒青椒至断生", "加回鸡蛋,调味翻炒"]', 8, '简单', '平静,嘴馋', '四季', NOW()),
('黄瓜炒鸡蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["黄瓜1根", "鸡蛋3个", "盐", "蒜"]', '["黄瓜切片,鸡蛋炒好盛出", "蒜末爆香,炒黄瓜", "加鸡蛋翻炒,调味"]', 7, '简单', '嘴馋,开心', '四季', NOW()),
('韭菜炒鸡蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/EXdka4udbt', '["韭菜1把", "鸡蛋4个", "盐"]', '["韭菜切段,鸡蛋打散", "鸡蛋炒至半熟加韭菜", "快速翻炒,调味出锅"]', 6, '简单', '开心,平静', '四季', NOW()),
('木须肉', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["猪肉100g", "鸡蛋3个", "木耳", "黄花菜", "黄瓜"]', '["肉切丝腌制,鸡蛋炒散", "炒肉丝变色盛出", "炒配菜,加鸡蛋肉丝", "调味翻炒"]', 15, '简单', '平静,嘴馋', '四季', NOW()),
('洋葱炒蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["洋葱1个", "鸡蛋3个", "盐"]', '["洋葱切丝,鸡蛋打散", "炒鸡蛋盛出", "炒洋葱至软,加鸡蛋", "调味出锅"]', 8, '简单', '嘴馋,开心', '四季', NOW()),
('鸡蛋羹', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["鸡蛋2个", "温水", "盐", "生抽", "香油"]', '["鸡蛋打散,加1.5倍温水", "过筛去泡沫", "盖保鲜膜扎孔", "水开后蒸10分钟", "淋生抽香油"]', 15, '简单', '开心,平静', '四季', NOW()),
('虎皮鸡蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["鸡蛋6个", "生抽", "老抽", "糖", "蒜", "葱"]', '["鸡蛋煮熟剥壳", "油炸至起皱虎皮,捞出", "锅中加调料和水煮开", "放鸡蛋小火煮15分钟", "大火收汁"]', 25, '简单', '平静,嘴馋', '四季', NOW()),
('苦瓜炒蛋', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["苦瓜1根", "鸡蛋3个", "盐"]', '["苦瓜切片,焯水去苦味", "鸡蛋炒好盛出", "炒苦瓜,加鸡蛋", "调味出锅"]', 10, '简单', '嘴馋,开心', '四季', NOW()),
('荷包蛋烧豆腐', '嫩滑鲜香，快手又营养', 'https://aka.doubaocdn.com/s/WB6j2NfAGt', '["鸡蛋2个", "豆腐1块", "葱姜蒜", "生抽", "蚝油"]', '["煎两个荷包蛋,盛出", "豆腐切块煎至两面金黄", "加调料和水烧开", "放入荷包蛋,小火煮5分钟", "大火收汁"]', 15, '简单', '开心,平静', '四季', NOW()),
('麻婆豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/WB6j2NfAGt', '["嫩豆腐400g", "肉末100g", "豆瓣酱", "花椒粉", "葱姜蒜"]', '["豆腐切块焯水", "炒肉末,加豆瓣酱炒出红油", "加水和豆腐,小火煮5分钟", "勾芡,撒花椒粉葱花"]', 15, '简单', '疲惫,难过', '四季', NOW()),
('家常豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/VVbgDravZe', '["老豆腐1块", "肉末", "青红椒", "豆瓣酱", "葱姜蒜"]', '["豆腐切块煎至两面金黄", "炒肉末,加豆瓣酱", "加豆腐和青红椒", "加水焖5分钟,勾芡"]', 20, '简单', '难过,平静', '四季', NOW()),
('红烧豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/VVbgDravZe', '["老豆腐1块", "生抽", "老抽", "糖", "葱姜蒜"]', '["豆腐切块煎至金黄", "加葱姜蒜爆香", "加调料和水烧开", "小火焖10分钟", "大火收汁"]', 20, '简单', '平静,疲惫', '四季', NOW()),
('鱼香豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/6u0NjFBN4h', '["豆腐", "肉末", "葱姜蒜", "鱼香汁(糖3:醋2:生抽1)"]', '["豆腐煎至金黄", "炒肉末,加葱姜蒜", "加豆腐和鱼香汁", "煮3分钟,勾芡"]', 15, '简单', '疲惫,难过', '四季', NOW()),
('客家酿豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/WB6j2NfAGt', '["老豆腐", "猪肉馅", "香菇", "葱姜", "生抽", "蚝油"]', '["豆腐切三角形,挖小洞", "肉馅调味,酿入豆腐", "煎至两面金黄", "加水和调料焖煮15分钟", "勾芡出锅"]', 30, '中等', '难过,平静', '四季', NOW()),
('白菜炖豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/VVbgDravZe', '["白菜", "豆腐", "粉丝", "葱姜", "盐"]', '["白菜切块,豆腐切块", "爆香葱姜,炒白菜", "加水烧开,加豆腐粉丝", "炖10分钟,调味"]', 15, '简单', '平静,疲惫', '四季', NOW()),
('小葱拌豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/uUozoveVfb', '["嫩豆腐", "小葱", "盐", "香油"]', '["豆腐切块摆盘", "小葱切碎撒上", "加盐、香油", "拌匀即可"]', 3, '简单', '疲惫,难过', '四季', NOW()),
('皮蛋豆腐', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/uUozoveVfb', '["内酯豆腐", "皮蛋", "葱", "生抽", "香油", "辣椒油"]', '["豆腐倒扣盘中", "皮蛋切碎铺上", "撒葱花", "淋调味汁"]', 5, '简单', '难过,平静', '四季', NOW()),
('鲫鱼豆腐汤', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["鲫鱼1条", "豆腐", "姜", "葱", "盐"]', '["鲫鱼煎至两面金黄", "加开水和姜片", "大火煮至汤色奶白(15分钟)", "加豆腐煮5分钟", "调味撒葱花"]', 25, '中等', '平静,疲惫', '四季', NOW()),
('冻豆腐炖白菜', '软嫩入味，下饭神器', 'https://aka.doubaocdn.com/s/VVbgDravZe', '["冻豆腐", "白菜", "粉丝", "葱姜"]', '["冻豆腐解冻撕块", "白菜切块炒软", "加水烧开,加豆腐粉丝", "炖15分钟,调味"]', 20, '简单', '疲惫,难过', '四季', NOW()),
('青椒肉丝', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/6u0NjFBN4h', '["里脊200g", "青椒2个", "腌料(料酒", "生抽", "淀粉)", "葱姜蒜"]', '["肉切丝腌制15分钟,青椒切丝", "滑炒肉丝变色盛出", "炒青椒至断生", "加回肉丝,调味翻炒"]', 15, '简单', '开心,嘴馋', '四季', NOW()),
('鱼香肉丝', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/6u0NjFBN4h', '["里脊200g", "胡萝卜", "青椒", "木耳", "鱼香汁(糖3:醋2:生抽1)"]', '["肉丝腌制,配菜切丝,调好鱼香汁", "滑炒肉丝盛出", "爆香葱姜蒜,炒配菜", "加肉丝和鱼香汁,翻炒收汁"]', 20, '中等', '嘴馋,满足', '四季', NOW()),
('京酱肉丝', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["里脊300g", "甜面酱", "葱丝", "黄瓜丝", "豆皮"]', '["肉切丝腌制", "滑炒肉丝至变色", "加甜面酱翻炒均匀", "配葱丝黄瓜丝,用豆皮卷着吃"]', 20, '中等', '满足,得意', '四季', NOW()),
('回锅肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["五花肉400g", "青蒜", "豆瓣酱", "豆豉", "姜蒜"]', '["五花肉煮至8成熟,切片", "肉片煸炒出油", "加豆瓣酱豆豉炒出红油", "加青蒜,翻炒出锅"]', 25, '中等', '得意,开心', '四季', NOW()),
('红烧肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/EyWU7vRI17', '["五花肉500g", "生抽", "老抽", "冰糖", "料酒", "八角", "桂皮"]', '["五花肉切块焯水", "炒糖色(可选)", "加调料和水,大火烧开", "小火炖1.5小时至软烂", "大火收浓汁"]', 2, '困难', '开心,嘴馋', '四季', NOW()),
('东坡肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/EyWU7vRI17', '["五花肉800g", "绍酒", "生抽", "老抽", "冰糖", "姜", "葱"]', '["五花肉切大块焯水", "锅底垫葱姜,摆肉块", "加绍酒和调料", "小火炖2小时", "收汁至浓稠"]', 2, '困难', '嘴馋,满足', '四季', NOW()),
('梅菜扣肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/EyWU7vRI17', '["五花肉500g", "梅干菜", "生抽", "老抽", "糖"]', '["五花肉煮熟,抹老抽炸至上色", "切片排碗中", "梅菜炒香铺上", "加调料,上锅蒸1.5小时", "倒扣出盘"]', 2, '困难', '满足,得意', '四季', NOW()),
('糖醋排骨', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/y9yX2pxT27', '["小排500g", "糖3勺", "醋2.5勺", "生抽", "料酒", "芝麻"]', '["排骨焯水", "炸至金黄", "炒糖醋汁", "加排骨煮5分钟", "大火收汁,撒芝麻"]', 30, '中等', '得意,开心', '四季', NOW()),
('糖醋里脊', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/y9yX2pxT27', '["里脊300g", "糖醋汁", "鸡蛋", "淀粉"]', '["里脊切条腌制", "裹蛋液和淀粉", "炸至金黄", "炒糖醋汁至浓稠", "裹上里脊,撒芝麻"]', 25, '中等', '开心,嘴馋', '四季', NOW()),
('锅包肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/y9yX2pxT27', '["里脊300g", "土豆淀粉", "糖醋汁", "胡萝卜", "葱姜"]', '["里脊切片腌制", "裹淀粉糊", "炸两次至酥脆", "炒糖醋汁,加配菜", "快速翻炒肉片"]', 30, '困难', '嘴馋,满足', '四季', NOW()),
('小炒肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["五花肉", "青椒", "红椒", "豆豉", "蒜"]', '["五花肉切片", "煸炒出油", "加豆豉蒜爆香", "加青红椒翻炒", "调味出锅"]', 12, '简单', '满足,得意', '四季', NOW()),
('蒜苔炒肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["猪肉150g", "蒜苔300g", "干辣椒", "姜蒜"]', '["肉切丝腌制,蒜苔切段", "炒肉丝盛出", "炒干辣椒,加蒜苔", "蒜苔断生加肉丝", "调味翻炒"]', 15, '简单', '得意,开心', '四季', NOW()),
('蒜薹炒腊肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["腊肉200g", "蒜薹300g", "干辣椒", "蒜"]', '["腊肉蒸熟切片", "蒜薹切段焯水", "煸炒腊肉出油", "加蒜薹干辣椒", "翻炒出锅(不用加盐)"]', 15, '简单', '开心,嘴馋', '四季', NOW()),
('咕咾肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/EyWU7vRI17', '["里脊300g", "菠萝", "青红椒", "番茄酱", "糖醋"]', '["里脊切块腌制,裹淀粉", "炸至金黄", "炒番茄酱和糖醋汁", "加菠萝青红椒", "加肉块翻炒均匀"]', 25, '中等', '嘴馋,满足', '四季', NOW()),
('木耳炒肉', '肉香四溢，满足感爆棚', 'https://aka.doubaocdn.com/s/6u0NjFBN4h', '["猪肉150g", "木耳", "胡萝卜", "葱姜"]', '["肉切片腌制,木耳泡发", "滑炒肉片盛出", "炒木耳胡萝卜", "加肉片调味翻炒"]', 15, '简单', '满足,得意', '四季', NOW()),
('宫保鸡丁', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/ugg35E74Hl', '["鸡胸300g", "黄瓜", "胡萝卜", "花生米", "干辣椒", "花椒", "宫保汁"]', '["鸡肉切丁腌制,配菜切丁,调好宫保汁", "滑炒鸡丁盛出", "炒干辣椒花椒", "加配菜、鸡丁、宫保汁", "翻炒收汁,加花生米"]', 20, '中等', '得意,开心', '四季', NOW()),
('辣子鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/gdYhXHpmhE', '["鸡腿2个", "干辣椒50g", "花椒", "姜蒜", "生抽"]', '["鸡腿切块腌制", "炸至金黄酥脆", "炒干辣椒花椒姜蒜", "加鸡块翻炒", "调味出锅"]', 25, '中等', '开心,期待', '四季', NOW()),
('可乐鸡翅', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/NLYbxN2vJh', '["鸡翅8个", "可乐250ml", "生抽", "料酒", "姜葱"]', '["鸡翅划刀焯水", "煎至两面金黄", "加姜葱爆香", "倒入可乐和调料", "炖20分钟,大火收汁"]', 30, '简单', '期待,满足', '四季', NOW()),
('红烧鸡腿', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/NLYbxN2vJh', '["鸡腿4个", "生抽", "老抽", "糖", "八角", "姜葱"]', '["鸡腿焯水", "煎至上色", "加调料和水烧开", "小火炖30分钟", "大火收汁"]', 45, '中等', '满足,得意', '四季', NOW()),
('口水鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/gdYhXHpmhE', '["鸡腿2个", "花生碎", "辣椒油", "生抽", "醋", "糖", "芝麻酱", "葱姜蒜"]', '["鸡腿煮熟放冰水", "撕成条摆盘", "调口水鸡汁(各种调料混合)", "浇在鸡肉上", "撒花生碎葱花"]', 30, '中等', '得意,开心', '四季', NOW()),
('黄焖鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/25iK7vnaY4', '["鸡腿2个", "香菇", "青椒", "土豆", "黄焖酱"]', '["鸡腿切块焯水", "煸炒鸡块", "加黄焖酱炒匀", "加土豆香菇和水", "炖20分钟,加青椒收汁"]', 35, '中等', '开心,期待', '四季', NOW()),
('三杯鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/25iK7vnaY4', '["鸡腿2个", "九层塔", "姜", "蒜", "辣椒", "麻油", "米酒", "酱油"]', '["鸡腿切块", "爆香姜蒜辣椒", "加鸡块煸炒", "加三杯(麻油、米酒、酱油各一杯)", "收汁加九层塔"]', 25, '中等', '期待,满足', '四季', NOW()),
('大盘鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/25iK7vnaY4', '["鸡腿3个", "土豆", "青椒", "红椒", "干辣椒", "花椒", "豆瓣酱"]', '["鸡腿切块焯水,土豆切块", "炒干辣椒花椒,加鸡块", "加豆瓣酱炒出红油", "加水和土豆炖20分钟", "加青红椒收汁"]', 40, '中等', '满足,得意', '四季', NOW()),
('照烧鸡腿', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/NLYbxN2vJh', '["鸡腿2个", "照烧汁(生抽", "味淋", "糖)", "姜", "芝麻"]', '["鸡腿去骨,皮朝下煎", "煎至金黄翻面", "加照烧汁", "小火煮10分钟", "收汁撒芝麻"]', 20, '简单', '得意,开心', '四季', NOW()),
('麻油鸡', '鲜嫩多汁，百吃不厌', 'https://aka.doubaocdn.com/s/25iK7vnaY4', '["鸡腿2个", "老姜", "麻油", "米酒", "盐"]', '["鸡腿切块,姜切片", "麻油炒姜片至焦黄", "加鸡块煸炒", "加米酒淹过鸡肉", "小火炖20分钟"]', 30, '简单', '开心,期待', '四季', NOW()),
('洋葱炒牛肉', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/QLy6Ky2t14', '["牛肉片250g", "洋葱1个", "腌料(生抽", "料酒", "淀粉", "小苏打)", "蚝油", "黑胡椒"]', '["牛肉逆纹切片,腌制20分钟", "洋葱切块", "大火快炒牛肉至变色,盛出", "炒洋葱至软", "加牛肉、蚝油、黑胡椒快速翻炒"]', 15, '简单', '满足,嘴馋', '秋冬', NOW()),
('青椒牛肉丝', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/QLy6Ky2t14', '["牛肉200g", "青椒2个", "腌料", "姜蒜"]', '["牛肉切丝腌制,青椒切丝", "滑炒牛肉丝盛出", "炒青椒", "加牛肉快速翻炒"]', 15, '简单', '嘴馋,期待', '秋冬', NOW()),
('红烧牛肉', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/tZD1YWkWFi', '["牛腩500g", "番茄", "土豆", "胡萝卜", "八角", "桂皮", "葱姜"]', '["牛腩切块焯水", "高压锅炖30分钟或炖锅1.5小时", "加番茄土豆胡萝卜", "再炖20分钟", "调味收汁"]', 1, '困难', '期待,得意', '秋冬', NOW()),
('番茄牛腩', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/tZD1YWkWFi', '["牛腩600g", "番茄3个", "土豆", "胡萝卜", "葱姜", "番茄酱"]', '["牛腩切块焯水", "番茄炒出汁,加番茄酱", "加牛腩和水,炖1小时", "加土豆胡萝卜炖20分钟", "收汁调味"]', 1, '困难', '得意,满足', '秋冬', NOW()),
('黑椒牛柳', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/QLy6Ky2t14', '["牛里脊250g", "洋葱", "青椒", "黑胡椒酱", "腌料"]', '["牛肉切条腌制", "滑炒牛肉盛出", "炒洋葱青椒", "加牛肉和黑椒酱", "快速翻炒"]', 15, '中等', '满足,嘴馋', '秋冬', NOW()),
('小炒黄牛肉', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/9Uxt6WPz73', '["牛肉250g", "小米椒", "线椒", "姜蒜", "豆豉"]', '["牛肉切片腌制", "煸炒牛肉至变色盛出", "炒姜蒜豆豉辣椒", "加牛肉快速翻炒", "调味出锅"]', 15, '中等', '嘴馋,期待', '秋冬', NOW()),
('干煸牛肉丝', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/9Uxt6WPz73', '["牛肉200g", "芹菜", "干辣椒", "花椒", "姜蒜"]', '["牛肉切丝腌制", "小火干煸牛肉至干香", "炒干辣椒花椒姜蒜", "加芹菜翻炒", "加牛肉调味"]', 20, '中等', '期待,得意', '秋冬', NOW()),
('孜然牛肉', '筋道入味，能量满满', 'https://aka.doubaocdn.com/s/9Uxt6WPz73', '["牛肉250g", "洋葱", "孜然粉", "辣椒粉", "腌料"]', '["牛肉切片腌制", "滑炒牛肉盛出", "炒洋葱", "加牛肉、孜然粉、辣椒粉", "快速翻炒"]', 12, '简单', '得意,满足', '秋冬', NOW()),
('清蒸鲈鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/9VzXgI5RwC', '["鲈鱼1条", "蒸鱼豉油", "料酒", "姜葱", "热油"]', '["鲈鱼清理,两面划刀", "抹料酒盐腌10分钟", "水开后蒸8分钟", "关火焖2分钟", "倒掉水,铺葱姜丝,淋豉油和热油"]', 25, '中等', '期待,满足', '春夏', NOW()),
('红烧鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/xIVSgjhgmz', '["鲤鱼/草鱼1条", "生抽", "老抽", "糖", "醋", "葱姜蒜"]', '["鱼两面划刀,煎至两面金黄", "爆香葱姜蒜", "加调料和水烧开", "中火炖15分钟", "大火收汁,撒葱花"]', 30, '中等', '满足,害羞', '春夏', NOW()),
('糖醋鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/xIVSgjhgmz', '["草鱼/鲤鱼1条", "糖醋汁", "淀粉", "姜葱"]', '["鱼划花刀,裹淀粉", "炸至金黄酥脆", "炒糖醋汁至浓稠", "浇在鱼上", "撒葱花"]', 25, '中等', '害羞,平静', '春夏', NOW()),
('红烧带鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/xIVSgjhgmz', '["带鱼500g", "生抽", "料酒", "糖", "醋", "葱姜蒜", "八角"]', '["带鱼切段腌制,拍淀粉", "煎至两面金黄", "爆香葱姜蒜八角", "放带鱼,加调料和水", "炖15分钟,收汁"]', 30, '中等', '平静,期待', '春夏', NOW()),
('水煮鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/UCWjL9yhA1', '["鱼片300g", "豆芽", "豆瓣酱", "干辣椒", "花椒", "葱姜蒜"]', '["鱼片腌制,豆芽焯水", "炒豆瓣酱出红油", "加水烧开煮豆芽", "捞出豆芽垫底", "煮鱼片,倒入碗中", "撒辣椒花椒,浇热油"]', 30, '困难', '期待,满足', '春夏', NOW()),
('酸菜鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/UCWjL9yhA1', '["鱼片300g", "酸菜", "泡椒", "豆芽", "葱姜蒜"]', '["鱼片腌制", "炒酸菜泡椒", "加水烧开煮豆芽", "煮鱼片3分钟", "出锅撒葱花"]', 25, '困难', '满足,害羞', '春夏', NOW()),
('番茄鱼', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/UCWjL9yhA1', '["鱼片300g", "番茄3个", "金针菇", "豆腐", "番茄酱"]', '["鱼片腌制", "番茄炒出汁,加番茄酱", "加水烧开,放豆腐金针菇", "煮5分钟后加鱼片", "煮3分钟出锅"]', 20, '中等', '害羞,平静', '春夏', NOW()),
('油焖大虾', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/lUqTIAgZkp', '["大虾300g", "番茄酱", "糖", "醋", "料酒", "葱姜"]', '["虾剪须去虾线", "煎至两面变红", "加葱姜爆香", "加调料焖5分钟", "大火收汁"]', 15, '简单', '平静,期待', '春夏', NOW()),
('白灼虾', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/lUqTIAgZkp', '["鲜虾300g", "姜", "料酒", "蘸料(生抽", "姜末", "香油)"]', '["水烧开加姜片料酒", "下虾煮至变红(3分钟)", "捞出过冰水", "调蘸料", "蘸食"]', 10, '简单', '期待,满足', '春夏', NOW()),
('蒜蓉粉丝蒸虾', '鲜美清甜，低脂健康', 'https://aka.doubaocdn.com/s/lUqTIAgZkp', '["大虾6只", "粉丝", "蒜蓉", "生抽", "蚝油"]', '["虾开背去虾线", "粉丝泡软铺底", "虾摆上,铺蒜蓉", "淋调味汁", "水开后蒸8分钟"]', 15, '简单', '满足,害羞', '春夏', NOW()),
('蒜蓉油麦菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["油麦菜300g", "蒜5瓣", "盐", "蚝油"]', '["油麦菜洗净切段", "蒜切末爆香", "菜梗先炒,后加菜叶", "大火快炒,加盐蚝油"]', 5, '简单', '开心,平静', '四季', NOW()),
('蒜蓉西兰花', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["西兰花", "蒜", "盐", "蚝油"]', '["西兰花切小朵焯水", "蒜末爆香", "加西兰花翻炒", "调味出锅"]', 8, '简单', '平静,疲惫', '四季', NOW()),
('蚝油生菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["生菜1棵", "蒜", "蚝油", "生抽", "糖"]', '["生菜撕大片,焯水30秒", "摆盘", "蒜末爆香,加蚝油生抽糖水煮开", "勾薄芡浇在生菜上"]', 8, '简单', '疲惫,焦虑', '四季', NOW()),
('清炒豆芽', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/aLZyoqkOxu', '["豆芽300g", "葱", "醋", "盐"]', '["豆芽洗净沥水", "葱段爆香", "豆芽大火快炒2分钟", "加盐醋调味"]', 5, '简单', '焦虑,开心', '四季', NOW()),
('醋溜白菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/aLZyoqkOxu', '["白菜", "醋", "干辣椒", "花椒", "盐", "糖"]', '["白菜梗叶分开切", "炒花椒干辣椒", "菜梗先炒,加醋", "加菜叶,调味快速翻炒"]', 8, '简单', '开心,平静', '四季', NOW()),
('醋溜土豆丝', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/aLZyoqkOxu', '["土豆2个", "干辣椒", "花椒", "醋", "盐"]', '["土豆切丝泡水15分钟", "炒花椒干辣椒", "沥干土豆丝,大火快炒", "分两次加醋", "调味出锅"]', 10, '简单', '平静,疲惫', '四季', NOW()),
('红烧茄子', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/79ZDDmgARK', '["茄子2个", "蒜", "青椒", "生抽", "糖", "醋"]', '["茄子切块加盐腌5分钟,挤水", "煎或炸茄子至软", "蒜末爆香,加茄子青椒", "加调料焖3分钟", "勾芡收汁"]', 20, '简单', '疲惫,焦虑', '四季', NOW()),
('鱼香茄子', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/79ZDDmgARK', '["茄子2个", "肉末", "鱼香汁", "葱姜蒜"]', '["茄子炸软", "炒肉末,加葱姜蒜", "加茄子和鱼香汁", "翻炒收汁"]', 15, '简单', '焦虑,开心', '四季', NOW()),
('干煸四季豆', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/79ZDDmgARK', '["四季豆300g", "肉末", "干辣椒", "花椒", "榨菜"]', '["四季豆撕筋,干煸至起皱", "炒肉末,加榨菜", "加四季豆翻炒", "调味出锅"]', 15, '简单', '开心,平静', '四季', NOW()),
('干锅花菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["花菜", "五花肉", "干辣椒", "花椒", "豆豉", "青蒜"]', '["花菜撕小朵焯水", "煸炒五花肉出油", "加豆豉干辣椒花椒", "加花菜翻炒", "加青蒜出锅"]', 15, '简单', '平静,疲惫', '四季', NOW()),
('西红柿炒菜花', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["番茄2个", "菜花", "葱", "盐", "糖"]', '["菜花切小朵焯水", "番茄炒出汁", "加菜花翻炒", "加盐糖调味"]', 12, '简单', '疲惫,焦虑', '四季', NOW()),
('虎皮青椒', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/aLZyoqkOxu', '["青椒", "蒜", "生抽", "醋", "糖"]', '["青椒压扁", "小火煎至起虎皮", "加蒜末和调味汁", "焖2分钟出锅"]', 8, '简单', '焦虑,开心', '四季', NOW()),
('炒三丁', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/79ZDDmgARK', '["鸡丁", "胡萝卜丁", "黄瓜丁", "腰果"]', '["鸡丁腌制,腰果炸酥", "滑炒鸡丁盛出", "炒胡萝卜黄瓜", "加鸡丁调味翻炒", "加腰果"]', 15, '简单', '开心,平静', '四季', NOW()),
('蒜蓉娃娃菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["娃娃菜", "蒜", "盐", "蚝油"]', '["娃娃菜切段", "蒜末爆香", "菜梗先炒,加菜叶", "加盐蚝油调味"]', 8, '简单', '平静,疲惫', '四季', NOW()),
('香菇炒青菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["小白菜300g", "香菇5朵", "蒜", "盐"]', '["青菜切段,香菇切片", "蒜片爆香", "炒香菇至软", "加青菜快炒", "调味出锅"]', 8, '简单', '疲惫,焦虑', '四季', NOW()),
('菠菜炒鸡蛋', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["菠菜", "鸡蛋3个", "盐", "蒜"]', '["菠菜焯水切段,鸡蛋炒好", "蒜末爆香", "炒菠菜", "加鸡蛋调味"]', 10, '简单', '焦虑,开心', '四季', NOW()),
('清炒芦笋', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["芦笋", "蒜", "盐"]', '["芦笋切段焯水", "蒜片爆香", "芦笋快炒", "调味出锅"]', 8, '简单', '开心,平静', '四季', NOW()),
('清炒山药', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["山药", "木耳", "胡萝卜", "盐"]', '["山药去皮切片,泡水", "快速焯水", "炒木耳胡萝卜", "加山药快炒", "调味出锅"]', 12, '简单', '平静,疲惫', '四季', NOW()),
('炝炒圆白菜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/aLZyoqkOxu', '["圆白菜", "干辣椒", "花椒", "醋", "盐"]', '["圆白菜手撕", "炒花椒干辣椒", "圆白菜大火快炒", "加醋盐调味"]', 6, '简单', '疲惫,焦虑', '四季', NOW()),
('地三鲜', '清爽脆嫩，轻负担', 'https://aka.doubaocdn.com/s/79ZDDmgARK', '["茄子", "土豆", "青椒", "蒜", "生抽", "糖"]', '["茄子土豆切块炸软", "青椒切块", "蒜末爆香", "加茄子土豆青椒", "加调料翻炒,勾芡"]', 25, '中等', '焦虑,开心', '四季', NOW()),
('番茄蛋汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["番茄1个", "鸡蛋2个", "葱花", "盐", "胡椒粉"]', '["番茄切块炒出汁", "加水烧开", "倒入蛋液搅散", "调味撒葱花"]', 10, '简单', '平静,疲惫', '秋冬', NOW()),
('紫菜蛋花汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["紫菜", "鸡蛋1个", "虾皮", "香油", "盐"]', '["水烧开", "加紫菜虾皮", "倒入蛋液", "调味加香油"]', 5, '简单', '疲惫,难过', '秋冬', NOW()),
('酸辣汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["豆腐", "木耳", "鸡蛋", "香醋", "白胡椒粉", "淀粉"]', '["豆腐木耳切丝", "水烧开,加豆腐木耳", "加醋胡椒粉调味", "勾芡", "倒入蛋液,滴香油"]', 12, '简单', '难过,想家', '秋冬', NOW()),
('三鲜汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["虾仁", "豆腐", "蘑菇", "小白菜", "盐", "胡椒粉"]', '["水烧开", "加豆腐蘑菇煮5分钟", "加虾仁煮至变色", "加小白菜", "调味出锅"]', 12, '简单', '想家,平静', '秋冬', NOW()),
('玉米排骨汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/czPUzpQNCP', '["排骨300g", "玉米1根", "胡萝卜", "姜", "盐"]', '["排骨焯水", "玉米切段,胡萝卜切块", "炖锅加排骨玉米姜片", "大火烧开转小火炖1小时", "加胡萝卜炖20分钟", "调味"]', 1, '中等', '平静,疲惫', '秋冬', NOW()),
('冬瓜排骨汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/QlWpfGCCyA', '["排骨500g", "冬瓜500g", "姜", "葱", "盐", "胡椒粉"]', '["排骨焯水", "炖锅加排骨姜葱", "小火炖1小时", "加冬瓜块炖20分钟至透明", "调味"]', 1, '中等', '疲惫,难过', '秋冬', NOW()),
('萝卜丝汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/QlWpfGCCyA', '["白萝卜", "虾仁(可选)", "葱姜", "盐", "胡椒粉"]', '["萝卜切丝", "水烧开加萝卜", "煮5分钟", "加虾仁煮熟", "调味"]', 10, '简单', '难过,想家', '秋冬', NOW()),
('豆腐泡菜汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["豆腐", "韩式辣白菜", "猪肉片(可选)", "洋葱"]', '["辣白菜洋葱炒香", "加泡菜汁和水", "加豆腐肉片煮10分钟", "调味"]', 15, '简单', '想家,平静', '秋冬', NOW()),
('丝瓜蛋汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["丝瓜1根", "鸡蛋1个", "姜", "盐"]', '["丝瓜去皮切块", "姜丝爆香,炒丝瓜", "加水烧开", "倒入蛋液", "调味出锅"]', 8, '简单', '平静,疲惫', '秋冬', NOW()),
('番茄豆腐蛋花汤', '暖胃暖心，一碗治愈', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["番茄1个", "豆腐", "鸡蛋1个", "葱花"]', '["番茄切块炒出汁", "加水烧开", "加豆腐煮3分钟", "倒入蛋液", "调味撒葱花"]', 10, '简单', '疲惫,难过', '秋冬', NOW()),
('凉拌黄瓜', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/IoZPemUno4', '["黄瓜2根", "蒜", "香醋", "生抽", "糖", "香油", "辣椒油(可选)"]', '["黄瓜拍碎切段", "加盐腌10分钟,倒掉水", "蒜切末", "加所有调料拌匀", "冷藏10分钟更入味"]', 5, '简单', '焦虑,嘴馋', '春夏', NOW()),
('凉拌木耳', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/P5HaTV08NM', '["木耳", "胡萝卜", "香菜", "蒜", "醋", "生抽", "香油"]', '["木耳泡发焯水", "胡萝卜切丝焯水", "香菜切段,蒜切末", "所有食材混合", "加调料拌匀"]', 10, '简单', '嘴馋,平静', '春夏', NOW()),
('凉拌海带丝', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/yg7CpVgy8G', '["海带丝", "胡萝卜", "蒜", "醋", "生抽", "香油", "辣椒油"]', '["海带丝焯水2分钟", "胡萝卜切丝", "蒜切末", "混合加调料拌匀"]', 8, '简单', '平静,开心', '春夏', NOW()),
('凉拌豆腐丝', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/P5HaTV08NM', '["豆腐丝", "黄瓜丝", "香菜", "蒜", "醋", "生抽", "香油"]', '["豆腐丝焯水", "黄瓜切丝,香菜切段", "混合加调料拌匀"]', 8, '简单', '开心,焦虑', '春夏', NOW()),
('凉拌三丝', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/yg7CpVgy8G', '["海带丝", "胡萝卜丝", "黄瓜丝", "蒜", "醋", "生抽", "香油"]', '["海带胡萝卜分别焯水", "黄瓜切丝", "混合加调料拌匀"]', 10, '简单', '焦虑,嘴馋', '春夏', NOW()),
('凉拌豆皮', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/P5HaTV08NM', '["豆皮", "黄瓜", "香菜", "花生碎", "蒜", "醋", "生抽", "香油", "辣椒油"]', '["豆皮切丝焯水", "黄瓜切丝,香菜切段", "混合加调料和花生碎", "拌匀即可"]', 8, '简单', '嘴馋,平静', '春夏', NOW()),
('老虎菜', '开胃解腻，清爽一夏', 'https://aka.doubaocdn.com/s/IoZPemUno4', '["尖椒", "香菜", "洋葱", "番茄", "盐", "醋", "香油"]', '["尖椒切丝", "香菜切段,洋葱切丝", "番茄切块", "混合加盐醋香油", "拌匀即食"]', 5, '简单', '平静,开心', '春夏', NOW());


-- ---------- 36道补充菜谱（早餐/面点/地方特色/甜点） ----------
INSERT INTO recipes (name, description, image, ingredients, steps, cooking_time, difficulty, mood_tags, season, created_at) VALUES
('葱花鸡蛋饼', '外酥里软的早餐饼，葱香扑鼻', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["面粉 100g", "鸡蛋 2个", "葱花 1小把", "盐 2g", "清水 150ml", "食用油 适量"]', '["面粉倒入碗中，加鸡蛋和盐", "慢慢加入清水，搅拌成无颗粒的面糊", "加入葱花拌匀", "平底锅刷薄油，倒入一勺面糊摊开", "小火煎至两面金黄", "出锅切块食用"]', 10, '简单', '开心,平静', '四季', NOW()),
('小米南瓜粥', '金黄绵软的暖胃粥，甜润舒心', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["小米 80g", "南瓜 200g", "清水 800ml", "冰糖 适量"]', '["小米淘洗干净，南瓜去皮切小块", "锅中加水烧开，下小米大火煮开", "转小火煮20分钟", "加入南瓜块继续煮15分钟", "煮至南瓜软烂、粥浓稠", "加冰糖调味即可"]', 40, '简单', '平静,疲惫', '秋冬', NOW()),
('三鲜水饺', '皮薄馅大的三鲜水饺，一口一个鲜', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["饺子皮 500g", "猪肉馅 300g", "虾仁 150g", "韭菜 100g", "鸡蛋 2个", "姜末 适量", "盐 适量", "生抽 1勺", "香油 1勺"]', '["鸡蛋炒熟切碎，虾仁切小丁", "猪肉馅加姜末、生抽、盐搅拌上劲", "加入虾仁、鸡蛋碎、韭菜碎", "淋入香油拌匀成馅", "取饺子皮包入馅料捏紧", "水开后下饺子，煮至浮起再煮3分钟"]', 45, '中等', '满足,想家', '四季', NOW()),
('葱油拌面', '葱香浓郁的快手拌面，简单却上瘾', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["细面条 150g", "小葱 1把", "生抽 2勺", "老抽 半勺", "白糖 1勺", "食用油 3勺"]', '["小葱切段，锅中放油小火慢慢熬葱", "熬至葱段焦黄捞出，葱油留用", "生抽、老抽、白糖调成酱汁", "面条煮熟捞出过凉水", "淋上葱油和酱汁", "撒上葱花拌匀即可"]', 15, '简单', '开心,嘴馋', '四季', NOW()),
('豆沙包', '松软香甜的豆沙包，早餐好选择', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["中筋面粉 300g", "酵母 3g", "温水 150ml", "豆沙馅 200g", "白糖 10g"]', '["酵母用温水化开，加面粉和白糖揉成光滑面团", "温暖处发酵至两倍大", "排气揉匀，分成小剂子", "擀皮包入豆沙馅，捏紧收口", "醒发15分钟，水开后蒸12分钟", "关火焖3分钟出锅"]', 60, '中等', '满足,开心', '四季', NOW()),
('皮蛋瘦肉粥', '绵密鲜香的广式粥品，暖胃又暖心', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["大米 80g", "皮蛋 2个", "瘦肉 100g", "姜丝 适量", "葱花 适量", "盐 适量", "白胡椒粉 少许", "香油 几滴"]', '["大米淘洗后加水熬煮成白粥", "瘦肉切丝用料酒盐腌制", "皮蛋切小块", "粥煮至浓稠，加入肉丝和姜丝", "煮至肉丝变色，加皮蛋", "加盐、白胡椒粉调味，撒葱花淋香油"]', 50, '简单', '疲惫,想家', '四季', NOW()),
('香煎葱油饼', '层层酥脆的葱油饼，葱香四溢', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["中筋面粉 250g", "开水 120ml", "小葱 3根", "盐 3g", "五香粉 少许", "食用油 适量"]', '["面粉加开水烫面，揉成光滑面团", "醒面20分钟", "擀成大薄片，刷油撒盐和葱花", "卷起来盘成圆饼，再擀薄", "平底锅刷油，中小火煎至两面金黄", "出锅切块"]', 35, '简单', '开心,嘴馋', '四季', NOW()),
('西红柿鸡蛋面', '酸甜开胃的经典汤面，百吃不厌', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["面条 150g", "番茄 2个", "鸡蛋 2个", "葱花 适量", "盐 适量", "糖 少许", "食用油 适量"]', '["番茄切块，鸡蛋打散", "热油炒鸡蛋盛出", "炒番茄出汁，加盐糖调味", "加水烧开，下面条煮熟", "倒入炒好的鸡蛋", "撒葱花出锅"]', 15, '简单', '开心,平静', '四季', NOW()),
('蒸蛋羹升级版', '嫩滑如镜的蒸蛋配虾仁，鲜上加鲜', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["鸡蛋 3个", "温水 200ml", "虾仁 6只", "葱花 适量", "生抽 半勺", "香油 几滴", "盐 少许"]', '["鸡蛋打散加温水和盐，过筛去泡沫", "盖保鲜膜扎孔，水开后蒸8分钟", "虾仁用料酒腌制", "蛋液凝固后铺上虾仁", "再蒸3分钟至虾仁变红", "淋生抽香油撒葱花"]', 15, '简单', '平静,害羞', '四季', NOW()),
('燕麦牛奶粥', '奶香浓郁的营养早餐，活力满满', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["即食燕麦 50g", "牛奶 250ml", "香蕉 1根", "蜂蜜 1勺", "坚果碎 适量"]', '["燕麦倒入锅中", "加入牛奶，小火加热搅拌", "煮至燕麦软糯浓稠", "香蕉切片铺在上面", "淋上蜂蜜", "撒坚果碎即可"]', 8, '简单', '平静,满足', '四季', NOW()),
('韭菜盒子', '皮薄馅足的韭菜盒子，外酥里鲜', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["中筋面粉 200g", "韭菜 200g", "鸡蛋 3个", "粉丝 50g", "虾皮 适量", "盐 适量", "香油 1勺"]', '["面粉加温水揉成面团醒20分钟", "鸡蛋炒熟切碎，粉丝泡软切碎", "韭菜切碎加鸡蛋、粉丝、虾皮", "加盐和香油拌匀成馅", "面团分剂子，擀皮包馅捏成盒子", "平底锅刷油煎至两面金黄"]', 40, '中等', '满足,开心', '春夏', NOW()),
('小笼包', '皮薄汤鲜的小笼包，一口爆汁', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["中筋面粉 250g", "猪肉馅 250g", "皮冻 100g", "姜末 适量", "生抽 1勺", "盐 适量", "糖 少许"]', '["面粉加温水揉成光滑面团醒30分钟", "猪肉馅加调料搅拌上劲，拌入皮冻", "面团搓条切小剂子，擀成薄皮", "包入馅料捏出18个褶", "水开后蒸8分钟", "趁热食用，先开窗喝汤"]', 50, '困难', '期待,满足', '四季', NOW()),
('白切鸡', '皮爽肉滑的经典粤菜，原汁原味', 'https://aka.doubaocdn.com/s/ugg35E74Hl', '["三黄鸡 1只", "姜 1块", "葱 2根", "盐 适量", "花生油 2勺", "沙姜粉 适量"]', '["鸡洗净，锅中加水放姜葱烧开", "手提鸡头反复汆烫三次", "整鸡放入水中，小火浸煮20分钟", "捞出放入冰水中浸泡5分钟", "斩块装盘", "姜葱蓉加盐，淋热油做成蘸料"]', 35, '中等', '满足,平静', '四季', NOW()),
('叉烧肉', '甜咸交织的蜜汁叉烧，肉质松软', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["梅花肉 500g", "叉烧酱 3勺", "蜂蜜 2勺", "生抽 1勺", "老抽 半勺", "料酒 1勺", "蒜末 适量"]', '["梅花肉切长条，加所有调料腌制4小时以上", "烤箱预热200度", "肉放在烤架上，中层烤20分钟", "取出刷一层蜂蜜和叉烧酱混合汁", "翻面再烤15分钟", "稍凉后切片装盘"]', 45, '中等', '满足,得意', '四季', NOW()),
('豉汁蒸排骨', '豉香浓郁的蒸排骨，嫩滑入味', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["猪小排 300g", "豆豉 1勺", "蒜末 适量", "生抽 1勺", "蚝油 1勺", "糖 少许", "淀粉 1勺", "食用油 1勺"]', '["排骨剁小块，泡水去血水", "加豆豉、蒜末、生抽、蚝油、糖", "加淀粉和油抓匀腌制20分钟", "平铺在盘中", "水开后大火蒸15分钟", "出锅撒葱花"]', 25, '简单', '满足,期待', '四季', NOW()),
('东坡肘子', '肥而不腻的东坡肘子，入口即化', 'https://aka.doubaocdn.com/s/EyWU7vRI17', '["猪肘子 1个", "冰糖 30g", "生抽 2勺", "老抽 1勺", "料酒 2勺", "八角 2个", "桂皮 1小块", "姜葱 适量"]', '["肘子焯水去血沫", "锅中放油炒糖色至琥珀色", "下肘子翻炒上色", "加料酒、生抽、老抽、香料", "加热水没过肘子", "大火烧开转小火炖2小时至软烂"]', 120, '困难', '满足,得意', '秋冬', NOW()),
('水煮肉片', '麻辣鲜香的水煮肉片，下饭神器', 'https://aka.doubaocdn.com/s/c7tuvox0UW', '["猪里脊 300g", "豆芽 200g", "郫县豆瓣酱 2勺", "干辣椒 20g", "花椒 1勺", "蒜末 适量", "淀粉 1勺", "蛋清 1个"]', '["肉片加蛋清、淀粉、盐腌制", "豆芽焯水铺碗底", "热油炒豆瓣酱出红油", "加水烧开，下肉片煮至变色", "连汤倒入碗中", "撒干辣椒花椒蒜末，淋热油激香"]', 25, '中等', '嘴馋,焦虑', '秋冬', NOW()),
('毛血旺', '麻辣滚烫的毛血旺，食材丰富', 'https://aka.doubaocdn.com/s/QLy6Ky2t14', '["鸭血 300g", "毛肚 100g", "黄喉 100g", "午餐肉 100g", "豆芽 150g", "豆瓣酱 2勺", "干辣椒 适量", "花椒 适量"]', '["鸭血切厚片，毛肚黄喉切条", "豆芽焯水铺底", "炒豆瓣酱出红油，加水烧开", "下鸭血、午餐肉煮5分钟", "下毛肚黄喉烫30秒", "倒入碗中，撒辣椒花椒淋热油"]', 30, '中等', '嘴馋,得意', '秋冬', NOW()),
('佛跳墙', '山珍海味荟萃的佛跳墙，醇厚鲜香', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["鲍鱼 4只", "海参 2条", "花胶 50g", "瑶柱 30g", "鸽子蛋 4个", "高汤 500ml", "绍兴酒 100ml", "盐 适量"]', '["所有干货提前泡发", "食材焯水后码入坛中", "倒入高汤和绍兴酒", "荷叶封口，盖上盖子", "隔水炖3小时", "加盐调味即可"]', 120, '困难', '期待,满足', '秋冬', NOW()),
('西湖醋鱼', '酸甜鲜嫩的西湖醋鱼，别具风味', 'https://aka.doubaocdn.com/s/9VzXgI5RwC', '["草鱼 1条", "姜末 适量", "生抽 2勺", "醋 3勺", "糖 2勺", "淀粉 1勺", "清水 适量"]', '["鱼处理干净，从背部剖开", "锅中加水烧开，放鱼小火浸煮5分钟", "捞出装盘", "锅中加生抽、醋、糖、姜末烧开", "水淀粉勾芡", "浇在鱼身上即可"]', 20, '中等', '平静,期待', '春夏', NOW()),
('北京烤鸭', '皮脆肉嫩的北京烤鸭，经典名吃', 'https://aka.doubaocdn.com/s/ugg35E74Hl', '["鸭子 1只", "蜂蜜 2勺", "白醋 1勺", "麦芽糖 1勺", "苹果 1个", "荷叶饼 适量", "葱丝 适量", "甜面酱 适量"]', '["鸭子洗净，吹气使皮肉分离", "开水浇烫鸭皮，挂起风干", "蜂蜜、白醋、麦芽糖调成汁刷在鸭皮上", "继续风干6小时", "烤箱200度烤40分钟至皮脆", "片皮配荷叶饼、葱丝、甜面酱食用"]', 60, '困难', '得意,满足', '四季', NOW()),
('酸汤肥牛', '酸辣开胃的酸汤肥牛，一口上瘾', 'https://aka.doubaocdn.com/s/QLy6Ky2t14', '["肥牛卷 300g", "金针菇 200g", "黄灯笼辣椒酱 2勺", "泡椒 适量", "蒜末 适量", "白醋 1勺", "盐 适量"]', '["金针菇去根洗净铺碗底", "热油炒黄灯笼酱和泡椒出香味", "加水烧开，加白醋和盐", "下肥牛卷煮至变色", "连汤倒入碗中", "撒蒜末，淋热油激香"]', 15, '简单', '嘴馋,焦虑', '四季', NOW()),
('叫花鸡', '荷叶清香的叫花鸡，肉质鲜嫩', 'https://aka.doubaocdn.com/s/ugg35E74Hl', '["三黄鸡 1只", "荷叶 2张", "黄泥 适量", "香菇 5朵", "笋丁 50g", "五花肉丁 50g", "生抽 2勺", "料酒 2勺"]', '["鸡洗净，用调料腌制2小时", "香菇、笋丁、五花肉丁炒香填入鸡腹", "荷叶包裹整鸡", "黄泥加水调成糊状，厚厚裹在荷叶外", "烤箱200度烤90分钟", "敲开黄泥和荷叶，撕鸡食用"]', 120, '困难', '期待,得意', '四季', NOW()),
('螺蛳粉', '酸辣鲜爽的螺蛳粉，闻着臭吃着香', 'https://aka.doubaocdn.com/s/V0UABjsurA', '["干米粉 150g", "螺蛳粉汤料包 1份", "酸笋 50g", "腐竹 30g", "花生米 适量", "青菜 2棵", "辣椒油 适量", "卤蛋 1个"]', '["干米粉用温水泡软", "锅中加水烧开，下米粉煮8分钟", "捞出过凉水", "汤料包加水烧开", "下米粉和青菜煮1分钟", "倒入碗中，加酸笋、腐竹、花生米、卤蛋，淋辣椒油"]', 15, '简单', '嘴馋,开心', '四季', NOW()),
('红豆双皮奶', '嫩滑香甜的双皮奶，入口即化', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["全脂牛奶 500ml", "蛋清 3个", "白糖 30g", "蜜红豆 适量"]', '["牛奶加热至微沸，倒入碗中放凉", "表面结出奶皮后，倒出牛奶留奶皮", "蛋清加白糖打散，与牛奶混合过筛", "沿碗边倒回有奶皮的碗中", "盖保鲜膜，水开后蒸15分钟", "放凉后铺上蜜红豆"]', 25, '中等', '满足,害羞', '四季', NOW()),
('杨枝甘露', '芒果香浓的杨枝甘露，清爽解暑', 'https://aka.doubaocdn.com/s/IoZPemUno4', '["芒果 2个", "西柚 半个", "西米 50g", "椰浆 200ml", "淡奶油 50ml", "白糖 20g"]', '["西米煮至透明，过凉水备用", "芒果一个切丁，一个打成泥", "芒果泥加椰浆、淡奶油、白糖搅匀", "加入西米拌匀", "倒入碗中", "撒上芒果丁和西柚粒"]', 20, '简单', '开心,满足', '春夏', NOW()),
('银耳莲子羹', '浓稠滋润的银耳羹，养颜安神', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["银耳 1朵", "莲子 30g", "红枣 6颗", "枸杞 10g", "冰糖 适量", "清水 1000ml"]', '["银耳提前泡发，撕成小朵", "莲子去芯，红枣洗净", "锅中加水，放银耳莲子大火煮开", "转小火炖1小时至银耳出胶", "加红枣和冰糖继续炖20分钟", "出锅前撒枸杞"]', 90, '简单', '平静,疲惫', '秋冬', NOW()),
('芒果班戟', '皮薄馅多的芒果班戟，奶油香浓', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["低筋面粉 60g", "鸡蛋 2个", "牛奶 150ml", "淡奶油 200ml", "糖粉 20g", "芒果 2个", "黄油 10g"]', '["鸡蛋加糖粉打散，加牛奶和融化黄油", "筛入面粉搅拌成面糊，过筛", "平底锅小火摊成薄饼", "淡奶油打发", "饼皮上铺奶油和芒果条", "包成四方形即可"]', 30, '中等', '满足,开心', '春夏', NOW()),
('桂花糖芋苗', '香甜软糯的桂花糖芋苗，金陵名点', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["小芋头 300g", "红糖 30g", "桂花蜜 1勺", "藕粉 1勺", "清水 适量"]', '["芋头去皮切小块", "锅中加水煮芋头至软烂", "加红糖煮至融化", "藕粉用冷水化开，倒入锅中勾芡", "煮至浓稠", "淋上桂花蜜即可"]', 30, '简单', '平静,满足', '秋冬', NOW()),
('姜撞奶', '嫩滑微辣的姜撞奶，暖胃驱寒', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["老姜 50g", "全脂牛奶 250ml", "白糖 20g"]', '["老姜磨成泥，挤出姜汁", "牛奶加糖加热至70-80度", "牛奶从高处冲入姜汁中", "不要搅拌，静置10分钟", "至凝固即可", "趁热食用"]', 15, '中等', '平静,害羞', '秋冬', NOW()),
('冰糖雪梨', '清甜润肺的冰糖雪梨，止咳化痰', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["雪梨 2个", "冰糖 30g", "枸杞 10g", "川贝粉 少许", "清水 适量"]', '["雪梨洗净，顶部切开去核", "中间放入冰糖、枸杞、川贝粉", "盖回顶部，用牙签固定", "放入碗中，加少许水", "水开后隔水蒸40分钟", "至雪梨透明软烂即可"]', 50, '简单', '平静,疲惫', '秋冬', NOW()),
('提拉米苏', '浓郁香醇的提拉米苏，意式经典', 'https://aka.doubaocdn.com/s/i0u7bPXeH3', '["马斯卡彭奶酪 250g", "淡奶油 150ml", "蛋黄 3个", "白糖 50g", "手指饼干 1包", "浓缩咖啡 100ml", "可可粉 适量"]', '["蛋黄加糖打发至浓稠", "加入马斯卡彭奶酪拌匀", "淡奶油打发至6分发，拌入奶酪糊", "手指饼干快速蘸咖啡铺底", "铺一层奶酪糊，再铺饼干", "重复铺层，冷藏4小时，筛可可粉"]', 30, '中等', '满足,得意', '四季', NOW()),
('绿豆汤', '清热解暑的绿豆汤，夏日必备', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["绿豆 150g", "冰糖 30g", "清水 1000ml", "陈皮 少许"]', '["绿豆洗净，加水浸泡1小时", "锅中加水和陈皮，大火煮开", "转小火煮30分钟至绿豆开花", "加冰糖继续煮10分钟", "关火焖10分钟", "放凉后饮用，冰镇更佳"]', 45, '简单', '平静,焦虑', '夏季', NOW()),
('芒果布丁', 'Q弹嫩滑的芒果布丁，果香浓郁', 'https://aka.doubaocdn.com/s/IoZPemUno4', '["芒果 2个", "牛奶 200ml", "淡奶油 100ml", "吉利丁片 10g", "白糖 20g"]', '["吉利丁片冷水泡软", "芒果一个切丁，一个打成泥", "牛奶加糖加热，加泡软的吉利丁融化", "加入芒果泥和淡奶油搅匀", "倒入布丁杯，冷藏4小时", "脱模后装饰芒果丁"]', 20, '简单', '开心,满足', '春夏', NOW()),
('酒酿圆子', '香甜软糯的酒酿圆子，江南风味', 'https://aka.doubaocdn.com/s/khWYcdgPeh', '["糯米小圆子 150g", "酒酿 200g", "枸杞 10g", "白糖 适量", "桂花 少许"]', '["锅中加水烧开，下小圆子", "煮至圆子浮起", "加入酒酿和白糖", "小火煮2分钟", "撒枸杞和桂花", "出锅即可"]', 10, '简单', '开心,平静', '四季', NOW()),
('双皮奶', '双层奶皮的经典双皮奶，嫩滑香甜', 'https://aka.doubaocdn.com/s/vwGsxumUTB', '["全脂牛奶 500ml", "蛋清 2个", "白糖 25g"]', '["牛奶倒入锅中加热至微沸", "倒入碗中放凉，表面结奶皮", "倒出牛奶，留奶皮在碗底", "蛋清加白糖打散，与牛奶混合过筛", "沿碗边倒回，奶皮浮起", "盖保鲜膜蒸15分钟，放凉凝固"]', 25, '中等', '满足,害羞', '四季', NOW());


-- ---------- 初始周边商品（锅仔形象馆） ----------
INSERT INTO products (name, image, price, exchange_price, category, description, stock, sort_order, is_active, created_at) VALUES
('锅仔搪瓷杯',        '/static/guozai/mood_01_happy.png',    39.00, 1.00, 'cup',     '把锅仔带在身边，喝水也有好心情', 100, 1, 1, NOW()),
('锅仔帆布袋',        '/static/guozai/action_07_empty.png',  49.00, 1.00, 'bag',     '背着锅仔去买菜，治愈整个通勤路', 100, 2, 1, NOW()),
('锅仔保温杯',        '/static/guozai/mood_05_sad.png',      69.00, 1.00, 'cup',     '冬日暖心伴侣，让每杯热水都有温度', 80,  3, 1, NOW()),
('锅仔围裙',          '/static/guozai/action_02_soup.png',   59.00, 1.00, 'clothes', '做饭仪式感拉满，锅仔陪你下厨',   60,  4, 1, NOW()),
('锅仔搪瓷碗套装',    '/static/guozai/action_01_bowl.png',   79.00, 1.00, 'kitchen', '一套温暖饭碗，好好吃饭每一天',   50,  5, 1, NOW()),
('锅仔手机壳',        '/static/guozai/mood_08_homesick.png', 29.00, 1.00, 'digital', '锅仔替你挡住生活的磕磕碰碰',     200, 6, 1, NOW());

-- ---------- 虚拟商品（需在微信虚拟支付后台录入同名道具） ----------
INSERT IGNORE INTO virtual_products
  (sku, platform_item_id, title, description, price_fen, entitlement_code, entitlement_amount, valid_days, active, sort_order, created_at)
VALUES
  ('AI_MENU_7D', NULL, '锅仔私人菜单 7 天包', '7 天内可使用 21 次按食材、时长和口味生成的菜谱。', 690, 'AI_DEEP_RECOMMEND', 21, 7, 1, 1, NOW()),
  ('ALBUM_HD_EXPORT', NULL, '月度画册收藏版', '解锁 1 次高清无水印导出与收藏版排版。', 490, 'ALBUM_HD_EXPORT', 1, 0, 1, 2, NOW()),
  ('GUOZAI_MEMBER_30D', NULL, '锅仔会员 30 天权益包', '30 天会员身份；上线后可在此叠加会员专属菜谱与画册权益。', 1290, 'MEMBER', 0, 30, 1, 3, NOW());

-- ---------- 验证 ----------
SELECT '初始化完成' AS status;
SELECT COUNT(*) AS total_recipes FROM recipes;
SELECT COUNT(*) AS total_products FROM products;
SELECT COUNT(*) AS total_virtual_products FROM virtual_products;
