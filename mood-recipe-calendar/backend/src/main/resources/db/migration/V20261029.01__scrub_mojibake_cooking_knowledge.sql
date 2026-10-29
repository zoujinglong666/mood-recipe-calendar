-- ============================================================
-- 修复烹饪知识库（cooking_knowledge_chunks）的 '?' 型乱码数据。
--
-- 病史：2026-09 以 latin1 客户端连接导入 20260917_add_cooking_agent_rag.sql，
-- 中文字符在入库时被替换成字面 '?'（0x3F），数据不可逆（fix-mojibake.sql
-- 只能救双编码型，救不回 '?'）。损坏行在「这次依据」卡片显示为全问号，
-- 且因 CookingKnowledgeService.normalize() 把 '?' 剥成空串导致任意提问都命中。
--
-- 处置口径与 MojibakeCleanupJob / RecipeTextGuard 一致：先备份后删除，
-- 再按原种子幂等重灌。products/virtual_products 仍维持只扫不删（贸易引用）。
-- 幂等：重复执行时备份 0 行、删除 0 行、重灌 WHERE NOT EXISTS 拦下。
-- ============================================================

-- 1) 备份损坏行（留追溯凭证，按 id 去重）
CREATE TABLE IF NOT EXISTS cooking_knowledge_chunks_mojibake_bak LIKE cooking_knowledge_chunks;
INSERT INTO cooking_knowledge_chunks_mojibake_bak
SELECT c.* FROM cooking_knowledge_chunks c
WHERE c.title LIKE '%?%' OR c.content LIKE '%?%'
       OR c.keywords LIKE '%?%' OR c.source_name LIKE '%?%'
       OR c.title LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
       OR c.content LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
       OR c.keywords LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
       OR c.source_name LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
  AND NOT EXISTS (SELECT 1 FROM cooking_knowledge_chunks_mojibake_bak b WHERE b.id = c.id);

-- 2) 删除损坏行（title/content/keywords/source_name 任一含 '?' 或 U+FFFD）
DELETE FROM cooking_knowledge_chunks
WHERE title LIKE '%?%' OR content LIKE '%?%'
   OR keywords LIKE '%?%' OR source_name LIKE '%?%'
   OR title LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR content LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR keywords LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR source_name LIKE CONCAT('%', CHAR(0xEFBFBD), '%');

-- 3) 重灌首批 WHO 食品安全知识（与冻结存档的 20260917_add_cooking_agent_rag.sql 保持逐字一致）
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
