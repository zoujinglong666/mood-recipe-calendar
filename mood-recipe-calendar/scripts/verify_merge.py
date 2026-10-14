"""模拟线上库验证融合脚本：只保留前 400 条菜，跑融合后检查结果。"""
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

# 1. 造一个"线上"库：结构与本地一致，但只有前 400 条菜
cur.execute(f"DROP DATABASE IF EXISTS `{TEST}`")
cur.execute(f"CREATE DATABASE `{TEST}` DEFAULT CHARACTER SET utf8mb4")
cur.execute(f"CREATE TABLE `{TEST}`.recipes LIKE mood_recipe.recipes")
cur.execute(f"INSERT INTO `{TEST}`.recipes SELECT * FROM mood_recipe.recipes WHERE id <= 400")
cur.execute(f"SELECT COUNT(*) FROM `{TEST}`.recipes")
before = cur.fetchone()[0]
print(f"模拟线上库初始菜谱数：{before}")

cur.execute(f"SELECT MD5(GROUP_CONCAT(name ORDER BY id)) FROM `{TEST}`.recipes")
before_hash = cur.fetchone()[0]

# 2. 执行融合脚本
r = subprocess.run(
    [MYSQL, "-uroot", "-p12345678", "--default-character-set=utf8mb4",
     f"-D{TEST}", f"--execute=source {SCRIPT.as_posix()}"],
    capture_output=True, text=True, encoding="utf-8", errors="replace")
print("--- 执行输出 ---")
print((r.stdout or "").strip())
if r.stderr and "Warning" not in r.stderr:
    print("STDERR:", r.stderr.strip())
print(f"退出码 {r.returncode}\n")

# 3. 检查结果
cur.execute(f"SELECT COUNT(*) FROM `{TEST}`.recipes")
after = cur.fetchone()[0]
print(f"融合后菜谱数：{after}（新增 {after - before}）")

cur.execute(f"SELECT MD5(GROUP_CONCAT(name ORDER BY id)) FROM `{TEST}`.recipes")
after_hash = cur.fetchone()[0]
print(f"原有数据是否被改动：{'是（异常）' if after_hash == 'x' else '正在核对'}")

# 原有 400 条是否原样保留
cur.execute(f"""SELECT COUNT(*) FROM `{TEST}`.recipes WHERE id <= 400""")
print(f"原 400 条仍在：{cur.fetchone()[0]} 条")

# 是否出现重复菜名
cur.execute(f"""SELECT COUNT(*) FROM (SELECT name FROM `{TEST}`.recipes
               GROUP BY name HAVING COUNT(*)>1) x""")
print(f"融合后重复菜名组数：{cur.fetchone()[0]}")

# 覆盖度
cur.execute("SELECT COUNT(DISTINCT name) FROM mood_recipe.recipes")
local_names = cur.fetchone()[0]
cur.execute(f"SELECT COUNT(DISTINCT name) FROM `{TEST}`.recipes")
merged_names = cur.fetchone()[0]
print(f"本地不同菜名 {local_names}，融合后线上不同菜名 {merged_names}")

conn.close()
