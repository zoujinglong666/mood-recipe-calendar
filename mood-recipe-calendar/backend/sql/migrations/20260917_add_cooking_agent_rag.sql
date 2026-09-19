-- 做菜智能体 RAG：只新增表，不修改现有菜谱与记录。
CREATE TABLE IF NOT EXISTS cooking_knowledge_chunks (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  title         VARCHAR(160) NOT NULL,
  content       TEXT         NOT NULL,
  category      VARCHAR(32)  NOT NULL,
  keywords      VARCHAR(500) NOT NULL,
  source_name   VARCHAR(160) NOT NULL,
  source_url    VARCHAR(500) NOT NULL,
  source_version VARCHAR(64) NOT NULL DEFAULT '1',
  reviewed_at   DATETIME     NOT NULL,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  INDEX idx_cooking_knowledge_enabled (enabled, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有来源且经审核的烹饪知识块';

CREATE TABLE IF NOT EXISTS cooking_learning_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64) NOT NULL,
  recipe_id   BIGINT,
  step_index  INT,
  step_type   VARCHAR(48),
  event_type  VARCHAR(24) NOT NULL,
  created_at  DATETIME NOT NULL,
  INDEX idx_cooking_learning_openid (openid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户明确提交的做菜学习事件';

-- 首批仅导入权威、通用的食品安全知识；后续知识通过同样字段批量导入。
INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '食物要彻底做熟',
       '需要烧熟煮透的食品，中心温度应达到70℃以上。汤、煲等食物应煮开；无法确认时使用食品温度计，不要只凭颜色判断。',
       'FOOD_SAFETY', '熟透,中心温度,温度计,肉,禽,蛋,海鲜,汤,煲',
       '世界卫生组织：食品安全五大要点',
       'https://www.who.int/activities/promoting-safe-food-handling/five-key-to-safer-food',
       '2026-09', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='食物要彻底做熟');

INSERT INTO cooking_knowledge_chunks
  (title, content, category, keywords, source_name, source_url, source_version, reviewed_at, enabled, created_at, updated_at)
SELECT '生熟分开避免交叉污染',
       '生食与熟食分开处理和存放；刀、砧板和容器应分开，接触生肉后清洁双手和器具再处理熟食。',
       'FOOD_SAFETY', '生熟分开,交叉污染,砧板,刀具,生肉,清洁',
       '世界卫生组织：食品安全五大要点',
       'https://www.who.int/activities/promoting-safe-food-handling/five-key-to-safer-food',
       '2026-09', NOW(), 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks WHERE title='生熟分开避免交叉污染');
