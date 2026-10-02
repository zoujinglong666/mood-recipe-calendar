-- 脏菜谱审计与隔离
--
-- 背景：线上历史数据里，AI 灌入的部分菜谱 ingredients/steps 是「菜名 + 通用模板」，
--       会让周计划的「食材与做法」和采购清单出现"菜名冒充食材、每道菜步骤一模一样"。
--       代码侧已修（MenuPlannerAgent 不再套模板、兜底路径改用真实食材/步骤），
--       本脚本负责把已落库的脏记录找出来并隔离，避免继续被选中。
--
-- 用法（分两步，先看后改）：
--   1) 审计（只读）：mysql ... < 20261002_audit_dirty_recipes.sql
--      会打印脏记录条数与明细。
--   2) 确认无误后，执行文件末尾的隔离 UPDATE（默认注释掉，手动取消注释运行）。
--
-- 隔离策略：把 source 改成 'RETIRED'，而不是 DELETE。
--   - RecipeRepository.findAiWithImages 只取 source='AI'，findAll 其余也仍可选，
--     因此为了让 RETIRED 彻底不被选中，排菜候选需排除 RETIRED（见 MenuPlannerAgent.localRecipes 的过滤）。
--   - 数据保留，可随时回滚：UPDATE recipes SET source = '<原值>' WHERE source = 'RETIRED'。

-- ============ 一、审计：判定三类脏记录 ============

-- 1) 食材把菜名当食材（ingredients 里出现与 name 完全相同的项）
SELECT 'ingredients_has_dish_name' AS reason, id, name, LEFT(ingredients, 120) AS detail
FROM recipes
WHERE ingredients LIKE CONCAT('%"', name, '"%');

-- 2) 步骤命中通用模板特征句
SELECT 'steps_is_template' AS reason, id, name, LEFT(steps, 160) AS detail
FROM recipes
WHERE steps LIKE '%需要的食材洗净切好%'
   OR steps LIKE '%按易熟程度依次下锅%'
   OR steps LIKE '%锅中少油加热%'
   OR steps LIKE '%调味后炒熟即可%'
   OR steps LIKE '%洗净切好。%'
   OR steps LIKE '%按食材易熟程度依次下锅%';

-- 3) 食材或步骤为空 / 空数组
SELECT 'empty_fields' AS reason, id, name, ingredients, steps
FROM recipes
WHERE ingredients IS NULL OR ingredients IN ('', '[]', '[""]')
   OR steps IS NULL OR steps IN ('', '[]', '[""]');

-- 脏记录总览（去重计数）
SELECT COUNT(DISTINCT id) AS dirty_total
FROM recipes
WHERE ingredients LIKE CONCAT('%"', name, '"%')
   OR steps LIKE '%需要的食材洗净切好%'
   OR steps LIKE '%按易熟程度依次下锅%'
   OR steps LIKE '%锅中少油加热%'
   OR steps LIKE '%调味后炒熟即可%'
   OR steps LIKE '%洗净切好。%'
   OR steps LIKE '%按食材易熟程度依次下锅%'
   OR ingredients IS NULL OR ingredients IN ('', '[]', '[""]')
   OR steps IS NULL OR steps IN ('', '[]', '[""]');

-- ============ 二、隔离（确认审计结果后再执行） ============
-- 取消下面注释后运行，把脏菜谱移出可选池：
--
-- UPDATE recipes
-- SET source = 'RETIRED'
-- WHERE source <> 'RETIRED'
--   AND (
--     ingredients LIKE CONCAT('%"', name, '"%')
--     OR steps LIKE '%需要的食材洗净切好%'
--     OR steps LIKE '%按易熟程度依次下锅%'
--     OR steps LIKE '%锅中少油加热%'
--     OR steps LIKE '%调味后炒熟即可%'
--     OR steps LIKE '%洗净切好。%'
--     OR steps LIKE '%按食材易熟程度依次下锅%'
--     OR ingredients IS NULL OR ingredients IN ('', '[]', '[""]')
--     OR steps IS NULL OR steps IN ('', '[]', '[""]')
--   );
--
-- 回滚：
-- UPDATE recipes SET source = 'AI' WHERE source = 'RETIRED';
