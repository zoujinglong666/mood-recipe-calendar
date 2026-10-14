"""生成「本地菜谱融合进线上」脚本。

融合语义：只补线上没有的菜，不删、不改线上任何已有数据。
- 线上没有的菜名  → 从本地插入
- 线上已有的菜名  → 完全不动
同名菜只保留一条，取信息最完整的版本（食材数 + 步骤数最多）。
"""
import json
import pymysql
from datetime import datetime
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
OUT = BASE / "db-export" / "sql" / "merge_recipes_into_online.sql"

FIELDS = ["name", "description", "image", "ingredients", "steps",
          "cooking_time", "difficulty", "mood_tags", "season",
          "created_at", "source", "taste", "meal_type", "tags", "seasonings"]

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute(f"SELECT {', '.join(FIELDS)} FROM recipes ORDER BY id")
all_rows = cur.fetchall()
total = len(all_rows)

# 同名菜只留一条，取信息最完整的：食材条目数 + 步骤条目数，描述长度作次要因素
def _count(v):
    if not v:
        return 0
    try:
        d = json.loads(v)
        return len(d) if isinstance(d, list) else 0
    except Exception:
        return len([x for x in str(v).split(",") if x.strip()])


def richness(row):
    name, desc, image, ing, steps = row[0], row[1], row[2], row[3], row[4]
    return _count(ing) + _count(steps) * 1.5 + min(len(desc or ""), 60) / 20


best = {}
for r in all_rows:                      # all_rows 已按 id 升序
    cur_best = best.get(r[0])
    if cur_best is None or richness(r) > richness(cur_best):
        best[r[0]] = r
picked = list(best.values())
conn.close()

print(f"本地菜谱 {total} 条 → 按菜名去重后 {len(picked)} 条"
      f"（合并掉 {total - len(picked)} 条同名重复）")


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    if hasattr(v, "strftime"):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return "'" + s + "'"


COL_LIST = ", ".join(f"`{c}`" for c in FIELDS)
DDL_COLS = """  `name` varchar(255), `description` varchar(255), `image` varchar(255),
  `ingredients` text, `steps` text, `cooking_time` int, `difficulty` varchar(255),
  `mood_tags` varchar(255), `season` varchar(255), `created_at` datetime,
  `source` varchar(16), `taste` varchar(255), `meal_type` varchar(255),
  `tags` varchar(255), `seasonings` varchar(255)"""

BATCH = 100
batches = [picked[i:i + BATCH] for i in range(0, len(picked), BATCH)]

parts = [f"""-- ============================================================
-- 心情菜谱 · 本地菜谱融合进线上（只增不删）
-- 生成时间：{datetime.now():%Y-%m-%d %H:%M:%S}
-- 本地菜谱：{total} 条，按菜名去重后 {len(picked)} 条
--   同名菜只保留一条，取食材与步骤最完整的版本（不保留多个做法版本）
--
-- 【融合规则】
--   线上没有的菜名  → 插入本地版本（id 由线上自增分配）
--   线上已有的菜名  → 保持线上原样，不覆盖、不修改
--   线上独有的菜    → 不动
--   全程不执行任何 DELETE / UPDATE / DROP / REPLACE
--
-- 【执行方式】在线上库执行（文件内含连接库要求）：
--   mysql -u<用户> -p -D <线上库名> --default-character-set=utf8mb4 \\
--     < merge_recipes_into_online.sql
--
-- 【前提】线上 recipes 表必须已含以下 15 个列：
--   name, description, image, ingredients, steps, cooking_time,
--   difficulty, mood_tags, season, created_at, source, taste,
--   meal_type, tags, seasonings
--   若报 Unknown column，说明线上结构落后，需先同步表结构。
--
-- 【幂等】可重复执行，第二次会全部跳过（菜名已存在）。
-- ============================================================

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `_local_recipes`;
CREATE TEMPORARY TABLE `_local_recipes` (
{DDL_COLS}
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

"""]

for i, b in enumerate(batches, 1):
    vals = ",\n".join("(" + ", ".join(lit(v) for v in row) + ")" for row in b)
    parts.append(f"INSERT INTO `_local_recipes` ({COL_LIST}) VALUES\n{vals};\n")

parts.append(f"""
-- 迁入前统计
SELECT COUNT(*) AS `本地待融合菜谱数` FROM `_local_recipes`;
SELECT COUNT(*) AS `其中线上已有同名` FROM `_local_recipes` l
  WHERE EXISTS (SELECT 1 FROM `recipes` r WHERE r.name = l.name);

START TRANSACTION;

INSERT INTO `recipes` ({COL_LIST})
SELECT {', '.join('l.`%s`' % c for c in FIELDS)}
FROM `_local_recipes` l
WHERE NOT EXISTS (SELECT 1 FROM `recipes` r WHERE r.name = l.name);

SELECT ROW_COUNT() AS `本次新增菜谱数`;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS `_local_recipes`;

SELECT COUNT(*) AS `融合后线上菜谱总数` FROM `recipes`;
""")

OUT.write_text("".join(parts), encoding="utf-8")
print(f"已生成 {OUT.name}，{OUT.stat().st_size / 1024:.1f} KB，"
      f"临时表分批 {len(batches)} 段")
