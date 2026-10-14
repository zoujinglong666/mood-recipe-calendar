"""根据质量修复备份文件，生成可同步到线上库的 SQL 脚本。

只输出实际发生变化的字段，避免覆盖线上未改动的数据。
用法：python gen_sync_sql.py
"""
import json
from datetime import datetime
from pathlib import Path

import pymysql

DB = dict(host="127.0.0.1", port=3306, user="root", password="12345678",
          database="mood_recipe", charset="utf8mb4")

BASE = Path(__file__).resolve().parent.parent
QF = BASE / "db-export" / "quality-fix"
OUT = BASE / "db-export" / "sql" / "sync_recipe_quality.sql"


def esc(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def load(fn):
    p = QF / fn
    if not p.exists():
        return {}
    return json.loads(p.read_text(encoding="utf-8"))


# ---- 1. 汇总每个 id 的原始值 ----
origin = {}  # id -> {field: original}


def merge(rid, field, value):
    origin.setdefault(int(rid), {})[field] = value


for rid, rec in load("descriptions_original.json").items():
    if isinstance(rec, dict) and "description" in rec:
        merge(rid, "description", rec["description"])

for rid, rec in load("steps_asterisk_backup.json").items():
    if isinstance(rec, dict) and "steps" in rec:
        merge(rid, "steps", rec["steps"])

si = load("steps_ingredients_backup.json")
for rec in si.get("rows", []):
    if "steps" in rec:
        merge(rec["id"], "steps", rec["steps"])
    if "ingredients" in rec:
        merge(rec["id"], "ingredients", rec["ingredients"])

print(f"备份覆盖 id：{len(origin)} 个")

# ---- 2. 读数据库当前值 ----
conn = pymysql.connect(**DB)
cur = conn.cursor()
ids = sorted(origin)
ph = ",".join(["%s"] * len(ids))
cur.execute(f"SELECT id, description, steps, ingredients FROM recipes WHERE id IN ({ph})", ids)
current = {r[0]: {"description": r[1], "steps": r[2], "ingredients": r[3]}
           for r in cur.fetchall()}

# ---- 3. 生成 UPDATE ----
stmts = []
stat = {"description": 0, "steps": 0, "ingredients": 0}
for rid in ids:
    cur_rec = current.get(rid)
    if not cur_rec:
        continue
    sets = []
    for field in ("description", "steps", "ingredients"):
        if field not in origin[rid]:
            continue
        old = origin[rid][field]
        new = cur_rec[field]
        if new is None or new == old:
            continue
        sets.append(f"{field}={esc(new)}")
        stat[field] += 1
    if sets:
        stmts.append(f"UPDATE recipes SET {', '.join(sets)} WHERE id={rid};")

# ---- 4. 汇总写文件 ----
header = f"""-- ============================================================
-- 心情菜谱 · 菜谱描述质量修复同步脚本
-- 生成时间：{datetime.now():%Y-%m-%d %H:%M:%S}
-- 来源库  ：mood_recipe.recipes（本地 940 条）
-- 目的库  ：线上 mood_recipe.recipes
-- 说明    ：仅更新发生变化的字段，未改动的记录不涉及。
--           全部语句包在事务中，执行后可跑文件末尾的校验 SQL。
--
-- 本次修复内容：
--   1) description 为 Markdown 图片引用 / 模板句复用 → 逐条重写
--   2) steps、ingredients 里混入的 Markdown 图片语法 → 剔除
--   3) steps 里 Markdown 强调星号 → 清除（尺寸乘号规范为 ×）
--   4) steps、ingredients 里 Markdown 反引号 → 清除
-- ============================================================

SET NAMES utf8mb4;
START TRANSACTION;

"""

footer = """
-- ---- Markdown 反引号全库清理（幂等，防止线上仍有残留）----
UPDATE recipes SET steps = REPLACE(steps, CHAR(96), '') WHERE steps LIKE CONCAT('%', CHAR(96), '%');
UPDATE recipes SET ingredients = REPLACE(ingredients, CHAR(96), '') WHERE ingredients LIKE CONCAT('%', CHAR(96), '%');

COMMIT;

-- ---- 执行后校验：四项应全为 0，总数为 940 ----
SELECT COUNT(*) AS 菜谱总数,
       SUM(description LIKE '![%') AS 描述为图片引用,
       SUM(description IS NULL OR TRIM(description)='') AS 描述为空,
       SUM(steps LIKE '%![%' OR ingredients LIKE '%![%') AS 字段含图片语法,
       SUM(steps LIKE CONCAT('%', CHAR(96), '%') OR ingredients LIKE CONCAT('%', CHAR(96), '%')) AS 含反引号
FROM recipes;
"""

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(header + "\n".join(stmts) + footer, encoding="utf-8")

print(f"生成语句：{len(stmts)} 条（description {stat['description']} / steps {stat['steps']} / ingredients {stat['ingredients']}）")
print(f"输出文件：{OUT}")
print(f"文件大小：{OUT.stat().st_size / 1024:.1f} KB")
conn.close()
