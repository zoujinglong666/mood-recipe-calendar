-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
-- 1) 家庭共享：会员权益可穿透给家庭成员
-- 2) 会员年卡 SKU（锚定）

CREATE TABLE IF NOT EXISTS family_groups (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_openid VARCHAR(64) NOT NULL COMMENT '创建者 openid',
  name        VARCHAR(50) NOT NULL DEFAULT '锅仔家庭' COMMENT '家庭名称',
  invite_code VARCHAR(16) NOT NULL COMMENT '邀请码',
  created_at  DATETIME NOT NULL,
  UNIQUE KEY uk_family_invite_code (invite_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='家庭共享组';

CREATE TABLE IF NOT EXISTS family_members (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id  BIGINT NOT NULL COMMENT '家庭组ID',
  openid    VARCHAR(64) NOT NULL COMMENT '成员 openid',
  role      VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/MEMBER',
  joined_at DATETIME NOT NULL,
  UNIQUE KEY uk_family_member_openid (openid),
  INDEX idx_family_member_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='家庭成员';

INSERT IGNORE INTO virtual_products
  (sku, platform_item_id, title, description, price_fen, benefits, entitlement_code, entitlement_amount, valid_days, active, sort_order, created_at)
VALUES
  ('GUOZAI_MEMBER_365D', NULL, '锅仔会员年卡', '365 天家庭共享：全家共用一份会员，含本月营养月报。', 3900, NULL, 'MEMBER', 0, 365, 1, 0, NOW());

UPDATE virtual_products SET benefits = JSON_ARRAY(
  JSON_OBJECT('group','MEAL','groupTitle','每天吃什么，锅仔替你想','icon','✦','title','锅仔智能体不限次对话','value','免费用户仅签到送 1 次，会员不限次','detail','想换菜、想调整、想问怎么做，随时开口；结合人数、预算、忌口和不做饭的日期。'),
  JSON_OBJECT('group','MEAL','groupTitle','每天吃什么，锅仔替你想','icon','▤','title','周菜单不限次生成与重排','value','免费用户每周 1 次，会员不限次','detail','安排整周晚餐、一键生成购物清单；哪天不合适，直接让锅仔换一道。'),
  JSON_OBJECT('group','MEAL','groupTitle','每天吃什么，锅仔替你想','icon','600','title','首页推荐每天 20 次','value','免费用户每天 3 次','detail','按你的心情、口味和忌口推荐，量足到可以任性挑；额度每天刷新。'),
  JSON_OBJECT('group','MEAL','groupTitle','每天吃什么，锅仔替你想','icon','◇','title','AI 私人菜单深度定制','value','一次给到贴合你的整份菜单','detail','输入心情和现有食材，生成一份完整可用、带做法的私人菜单，不用逐项凑。'),
  JSON_OBJECT('group','FAMILY','groupTitle','一家人，共用一份会员','icon','❋','title','家庭共享权益','value','全家人共用这一份会员','detail','创建家庭、把邀请码发给家人；家人的会员权益全部通用，一份钱全家用。'),
  JSON_OBJECT('group','FAMILY','groupTitle','一家人，共用一份会员','icon','✉','title','本月营养月报','value','每月一期，锅仔写给你的一封信','detail','把这个月的记录、心情和最常做的菜，写成锅仔的一封信，附上你的饮食小洞察。'),
  JSON_OBJECT('group','FRIDGE','groupTitle','冰箱里有什么，拍一张就知道','icon','◉','title','冰箱拍照识别食材','value','拍一张，自动认食材、算保质期','detail','对着冰箱拍一张，锅仔自动认出食材并估算保质期，确认后一键入库，不用一个个手输。'),
  JSON_OBJECT('group','FRIDGE','groupTitle','冰箱里有什么，拍一张就知道','icon','⏱','title','食材临期提醒','value','快过期时提醒你，别浪费','detail','临期或过期的食材会主动提醒，并告诉你哪些其实不该放冰箱、该怎么存。'),
  JSON_OBJECT('group','MEMORY','groupTitle','认真吃过的每一餐，都替你收好','icon','▣','title','高清无水印月度画册','value','把这一个月认真收藏下来','detail','将当月真实记录、心情和锅仔寄语整理成无水印收藏图，随时保存、分享。'),
  JSON_OBJECT('group','MEMORY','groupTitle','认真吃过的每一餐，都替你收好','icon','↗','title','锅仔联网搜索','value','问到库外知识也能答','detail','时令食材、食材挑选、某道菜的多样做法，锅仔可以联网帮你查，并注明来源。')
) WHERE sku = 'GUOZAI_MEMBER_365D';

-- 定价调整：年卡 268 元 → 39 元（对已执行过本迁移的环境幂等修正）
UPDATE virtual_products SET price_fen = 3900 WHERE sku = 'GUOZAI_MEMBER_365D';
