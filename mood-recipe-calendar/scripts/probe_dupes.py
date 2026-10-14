"""查看同名菜的来源与差异，判断该拿什么当去重键。"""
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()

cur.execute("""SELECT name, COUNT(*) c FROM recipes GROUP BY name
               HAVING c > 2 ORDER BY c DESC, name LIMIT 8""")
names = [r[0] for r in cur.fetchall()]

for n in names:
    cur.execute("""SELECT id, IFNULL(source,'(空)'), LEFT(description,42)
                   FROM recipes WHERE name=%s ORDER BY id""", (n,))
    print(f"=== {n} ===")
    for r in cur.fetchall():
        print(f"   id={r[0]:<5} source={r[1]:<10} {r[2]}")
    print()

# name+source 组合的重复情况
cur.execute("""SELECT COUNT(*) FROM (
                 SELECT name, source FROM recipes
                 GROUP BY name, source HAVING COUNT(*) > 1) x""")
print(f"同名且同来源的重复组数：{cur.fetchone()[0]}")

cur.execute("SELECT COUNT(DISTINCT name) FROM recipes")
print(f"不同菜名数：{cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM recipes WHERE source IS NULL AND name IN "
            "(SELECT name FROM recipes GROUP BY name HAVING COUNT(*)>1)")
print(f"来源为空的重名菜条数：{cur.fetchone()[0]}")
conn.close()
