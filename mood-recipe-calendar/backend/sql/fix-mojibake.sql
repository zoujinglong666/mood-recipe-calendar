-- ============================================================
-- 修复因 latin1/cp1252 客户端连接导入 init.sql 导致的中文双编码乱码
-- 症状：接口返回形如 "é”…ä»” AI ç§äººèœå• 7 å¤©åŒ…"（正确应为"锅仔 AI 私人菜单 7 天包"）
-- 成因：UTF-8 字节被按 cp1252 解释后再次以 utf8mb4 存储（E9 94 85 -> é ” … -> C3A9 E2809D E280A6）
--
-- 使用方式（务必带 utf8mb4）：
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < fix-mojibake.sql
--
-- 安全性：只修复字段字节含 C2/C3（双编码特征）的行，已正确的中文不会被改动；
--         脚本幂等，可重复执行，重复执行时 0 行受影响。
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------- 1. 修复前核对（可先看一眼当前乱码） ----------
SELECT sku, title, description FROM virtual_products
WHERE HEX(title) LIKE '%C3%' OR HEX(title) LIKE '%C2%'
   OR HEX(description) LIKE '%C3%' OR HEX(description) LIKE '%C2%';

-- ---------- 2. 虚拟商品（小程序权益列表） ----------
UPDATE virtual_products
SET title       = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4),
    description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(title) LIKE '%C3%' OR HEX(title) LIKE '%C2%'
   OR HEX(description) LIKE '%C3%' OR HEX(description) LIKE '%C2%';

-- ---------- 3. 周边商品 ----------
UPDATE products
SET name        = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4),
    category    = CONVERT(CAST(CONVERT(category USING latin1) AS BINARY) USING utf8mb4),
    description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' OR HEX(name) LIKE '%C2%'
   OR HEX(category) LIKE '%C3%' OR HEX(category) LIKE '%C2%'
   OR HEX(description) LIKE '%C3%' OR HEX(description) LIKE '%C2%';

-- ---------- 4. 菜谱库（含 JSON 字段 ingredients / steps） ----------
UPDATE recipes
SET name        = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4),
    description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4),
    ingredients = CONVERT(CAST(CONVERT(ingredients USING latin1) AS BINARY) USING utf8mb4),
    steps       = CONVERT(CAST(CONVERT(steps USING latin1) AS BINARY) USING utf8mb4),
    mood_tags   = CONVERT(CAST(CONVERT(mood_tags USING latin1) AS BINARY) USING utf8mb4),
    season      = CONVERT(CAST(CONVERT(season USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' OR HEX(name) LIKE '%C2%'
   OR HEX(description) LIKE '%C3%' OR HEX(description) LIKE '%C2%'
   OR HEX(ingredients) LIKE '%C3%' OR HEX(ingredients) LIKE '%C2%'
   OR HEX(steps) LIKE '%C3%' OR HEX(steps) LIKE '%C2%';

-- ---------- 5. 烹饪知识库 ----------
UPDATE cooking_knowledge_chunks
SET title       = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4),
    content     = CONVERT(CAST(CONVERT(content USING latin1) AS BINARY) USING utf8mb4),
    keywords    = CONVERT(CAST(CONVERT(keywords USING latin1) AS BINARY) USING utf8mb4),
    source_name = CONVERT(CAST(CONVERT(source_name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(title) LIKE '%C3%' OR HEX(title) LIKE '%C2%'
   OR HEX(content) LIKE '%C3%' OR HEX(content) LIKE '%C2%'
   OR HEX(keywords) LIKE '%C3%' OR HEX(keywords) LIKE '%C2%';

-- ---------- 6. 修复后校验：以下查询均应返回 0 行 ----------
SELECT 'virtual_products' AS tbl, COUNT(*) AS remain
FROM virtual_products WHERE HEX(title) LIKE '%C3%' OR HEX(title) LIKE '%C2%';
SELECT 'products' AS tbl, COUNT(*) AS remain
FROM products WHERE HEX(name) LIKE '%C3%' OR HEX(name) LIKE '%C2%';
SELECT 'recipes' AS tbl, COUNT(*) AS remain
FROM recipes WHERE HEX(name) LIKE '%C3%' OR HEX(name) LIKE '%C2%';
SELECT 'cooking_knowledge_chunks' AS tbl, COUNT(*) AS remain
FROM cooking_knowledge_chunks WHERE HEX(title) LIKE '%C3%' OR HEX(title) LIKE '%C2%';

-- ---------- 7. 抽查结果 ----------
SELECT sku, title, description FROM virtual_products ORDER BY sort_order;
