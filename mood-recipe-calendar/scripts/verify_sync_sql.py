"""校验生成的同步 SQL：解析语句并与数据库当前值逐条比对。"""
import re
from pathlib import Path

import pymysql

DB = dict(host="127.0.0.1", port=3306, user="root", password="12345678",
          database="mood_recipe", charset="utf8mb4")

BASE = Path(__file__).resolve().parent.parent
SQL = BASE / "db-export" / "sql" / "sync_recipe_quality.sql"

text = SQL.read_text(encoding="utf-8")

# 抽出所有 UPDATE recipes SET ... WHERE id=N;
pat = re.compile(r"UPDATE recipes SET (.+?) WHERE id=(\d+);", re.S)
rows = pat.findall(text)
print(f"SQL 中解析到 {len(rows)} 条 id 定向 UPDATE")

# 值解析：逐个字段拆
field_pat = re.compile(r"(description|steps|ingredients)=('(?:[^']|'')*')")


def unesc(s):
    return s[1:-1].replace("''", "'").replace("\\\\", "\\")


targets = {}
errors = []
for set_clause, rid in rows:
    rid = int(rid)
    parts = field_pat.findall(set_clause)
    if not parts:
        errors.append(f"id={rid} 未解析出字段")
        continue
    for field, val in parts:
        targets.setdefault(rid, {})[field] = unesc(val)

print(f"覆盖 id：{len(targets)} 个")
if errors:
    print("解析异常：", errors[:5])

# 与数据库比对
conn = pymysql.connect(**DB)
cur = conn.cursor()
ids = sorted(targets)
ph = ",".join(["%s"] * len(ids))
cur.execute(f"SELECT id, description, steps, ingredients FROM recipes WHERE id IN ({ph})", ids)
db = {r[0]: {"description": r[1], "steps": r[2], "ingredients": r[3]} for r in cur.fetchall()}

mismatch = []
missing = []
total_fields = 0
for rid, fields in targets.items():
    if rid not in db:
        missing.append(rid)
        continue
    for f, v in fields.items():
        total_fields += 1
        if db[rid][f] != v:
            mismatch.append((rid, f, v[:40], (db[rid][f] or "")[:40]))

print(f"比对字段总数：{total_fields}")
print(f"数据库中不存在的 id：{len(missing)} {missing[:5]}")
print(f"值不一致：{len(mismatch)}")
for m in mismatch[:5]:
    print("  ", m)
if not mismatch and not missing and not errors:
    print("\n校验通过：SQL 写入值与数据库当前值完全一致。")
conn.close()
