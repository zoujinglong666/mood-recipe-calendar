"""验收：原有记录逐字未变 + 脚本幂等。"""
import subprocess
import pymysql
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
SCRIPT = BASE / "db-export" / "sql" / "merge_recipes_into_online.sql"
TEST = "mood_recipe_merge_test"

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

cur.execute("SELECT COUNT(*) FROM mood_recipe.recipes WHERE id <= 400")
print(f"源库 id<=400 条数：{cur.fetchone()[0]}")

# 1. 原 400 条是否逐字未变
cur.execute(f"""SELECT COUNT(*) FROM (
    SELECT id,name,description,image,ingredients,steps,cooking_time,
           difficulty,mood_tags,season,created_at,source,taste,
           meal_type,tags,seasonings FROM mood_recipe.recipes WHERE id<=400
) a JOIN (
    SELECT id,name,description,image,ingredients,steps,cooking_time,
           difficulty,mood_tags,season,created_at,source,taste,
           meal_type,tags,seasonings FROM `{TEST}`.recipes WHERE id<=400
) b ON a.id=b.id
WHERE NOT (a.name<=>b.name AND a.description<=>b.description
   AND a.image<=>b.image AND a.ingredients<=>b.ingredients
   AND a.steps<=>b.steps AND a.cooking_time<=>b.cooking_time
   AND a.difficulty<=>b.difficulty AND a.mood_tags<=>b.mood_tags
   AND a.season<=>b.season AND a.created_at<=>b.created_at
   AND a.source<=>b.source AND a.taste<=>b.taste
   AND a.meal_type<=>b.meal_type AND a.tags<=>b.tags
   AND a.seasonings<=>b.seasonings)""")
changed = cur.fetchone()[0]
print(f"原有 400 条中被改动的：{changed} 条 {'（通过）' if changed == 0 else '（异常）'}")

# 2. 幂等：再跑一次
cur.execute(f"SELECT COUNT(*) FROM `{TEST}`.recipes")
n1 = cur.fetchone()[0]
r = subprocess.run(
    [MYSQL, "-uroot", "-p12345678", "--default-character-set=utf8mb4",
     f"-D{TEST}", f"--execute=source {SCRIPT.as_posix()}"],
    capture_output=True, text=True, encoding="utf-8", errors="replace")
cur.execute(f"SELECT COUNT(*) FROM `{TEST}`.recipes")
n2 = cur.fetchone()[0]
print(f"\n重复执行：{n1} → {n2} 条，{'幂等通过' if n1 == n2 else '异常，重复插入了'}")

# 3. 新插入记录的完整性抽查
cur.execute(f"""SELECT id, name, LEFT(description,30), source
                FROM `{TEST}`.recipes WHERE id > 400 ORDER BY id LIMIT 6""")
print("\n新插入记录抽查：")
for row in cur.fetchall():
    print(f"   id={row[0]} {row[1]} | {row[2]} | source={row[3]}")

cur.execute(f"""SELECT COUNT(*) FROM `{TEST}`.recipes
                WHERE id > 400 AND (name IS NULL OR description IS NULL)""")
print(f"\n新插入记录中缺名或缺描述的：{cur.fetchone()[0]} 条")

# 4. 清理
cur.execute(f"DROP DATABASE IF EXISTS `{TEST}`")
print(f"\n测试库 {TEST} 已清理")
conn.close()
