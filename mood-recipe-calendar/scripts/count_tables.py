"""精确统计各表行数并分组。"""
import pymysql

CONTENT = {"recipes", "products", "virtual_products",
           "seasonal_ingredients", "nutrition_knowledge_packs",
           "cooking_knowledge_chunks", "guideline_chunks"}

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute("""SELECT table_name FROM information_schema.tables
               WHERE table_schema='mood_recipe' ORDER BY table_name""")
ts = [r[0] for r in cur.fetchall()]

rows = {}
for t in ts:
    cur.execute(f"SELECT COUNT(*) FROM `{t}`")
    rows[t] = cur.fetchone()[0]

content = {t: v for t, v in rows.items() if t in CONTENT}
user = {t: v for t, v in rows.items() if t not in CONTENT}
print(f"表总数 {len(ts)}")
print(f"内容表 {len(content)} 张，{sum(content.values())} 行")
for t, v in sorted(content.items(), key=lambda x: -x[1]):
    print(f"    {t}: {v}")
print(f"用户/运行时表 {len(user)} 张，{sum(user.values())} 行")
for t, v in sorted(user.items(), key=lambda x: -x[1]):
    if v:
        print(f"    {t}: {v}")
print(f"合计 {sum(rows.values())} 行")
conn.close()
