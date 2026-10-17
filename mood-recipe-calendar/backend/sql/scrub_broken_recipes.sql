-- ============================================================
-- 清理「中文被吞成字面问号」的损坏菜谱（乱码不可逆，只能删后重灌）
-- 症状：小程序卡片上菜名/简介显示 ????、?????????????????（真问号，非字体缺字）
-- 成因：以 latin1/非 utf8mb4 客户端连接导入菜谱 SQL，中文字符在入库时被 MySQL
--       直接替换成 0x3F（'?'），原始字节已丢失——fix-mojibake.sql 救不回这一类。
--       后端已有 RecipeTextGuard 防线（接口层不外露），本脚本做数据层根治。
--
-- 使用方式（务必带 utf8mb4）：
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < scrub_broken_recipes.sql
--
-- 重灌正确姿势（先备份，再以 utf8mb4 客户端导入种子）：
--   mysqldump -uroot -p --default-character-set=utf8mb4 mood_recipe recipes > recipes_backup_before_scrub.sql
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < expand_recipes.sql
-- 幂等性：删除语句可重复执行，第二次跑 0 行受影响。
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------- 1. 删除前核对：先看一眼要删哪些 ----------
SELECT id, source, name, LEFT(description, 40) AS descr, created_at
FROM recipes
WHERE name LIKE '%?%' OR description LIKE '%?%'
   OR name LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR description LIKE CONCAT('%', CHAR(0xEFBFBD), '%');

-- ---------- 2. 备份损坏行（只备份将被删除的行，留追溯凭证） ----------
CREATE TABLE IF NOT EXISTS recipes_mojibake_bak LIKE recipes;
INSERT INTO recipes_mojibake_bak
SELECT r.* FROM recipes r
WHERE (r.name LIKE '%?%' OR r.description LIKE '%?%'
   OR r.name LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR r.description LIKE CONCAT('%', CHAR(0xEFBFBD), '%'))
  AND NOT EXISTS (SELECT 1 FROM recipes_mojibake_bak b WHERE b.id = r.id);

-- ---------- 3. 删除损坏菜谱 ----------
DELETE r FROM recipes r
WHERE r.name LIKE '%?%' OR r.description LIKE '%?%'
   OR r.name LIKE CONCAT('%', CHAR(0xEFBFBD), '%')
   OR r.description LIKE CONCAT('%', CHAR(0xEFBFBD), '%');

-- ---------- 4. 修复后校验：应返回 0 ----------
SELECT COUNT(*) AS remaining_broken FROM recipes
WHERE name LIKE '%?%' OR description LIKE '%?%';

-- ---------- 5. 顺带排查其它共享表的同类损坏（只读，不动数据） ----------
SELECT 'virtual_products' AS tbl, COUNT(*) AS broken FROM virtual_products
 WHERE title LIKE '%?%' OR description LIKE '%?%'
UNION ALL
SELECT 'products', COUNT(*) FROM products
 WHERE name LIKE '%?%' OR description LIKE '%?%'
UNION ALL
SELECT 'cooking_knowledge_chunks', COUNT(*) FROM cooking_knowledge_chunks
 WHERE title LIKE '%?%' OR content LIKE '%?%';
