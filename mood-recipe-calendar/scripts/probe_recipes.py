"""探查本地菜谱表结构，为融合方案做准备。"""
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()


def q(label, sql):
    cur.execute(sql)
    rows = cur.fetchall()
    print(f"--- {label} ---")
    for r in rows:
        print("   ", " | ".join(str(x) for x in r))
    print()


q("总数与 ID 范围",
  "SELECT COUNT(*) AS 总数, MIN(id) AS 最小id, MAX(id) AS 最大id FROM recipes")

q("source 分布",
  "SELECT source, COUNT(*) FROM recipes GROUP BY source ORDER BY COUNT(*) DESC")

q("同名菜数量（前 15）",
  """SELECT name, COUNT(*) c FROM recipes GROUP BY name
     HAVING c > 1 ORDER BY c DESC, name LIMIT 15""")

cur.execute("SELECT COUNT(*) FROM (SELECT name FROM recipes GROUP BY name HAVING COUNT(*)>1) x")
print(f"--- 重名菜品类数：{cur.fetchone()[0]} ---\n")

q("ID 是否有空洞（前 10 个缺口）",
  """SELECT (t1.id + 1) AS 缺口起点,
            (SELECT MIN(t3.id) FROM recipes t3 WHERE t3.id > t1.id) - 1 AS 缺口终点
     FROM recipes t1
     WHERE NOT EXISTS (SELECT 1 FROM recipes t2 WHERE t2.id = t1.id + 1)
       AND t1.id < (SELECT MAX(id) FROM recipes)
     LIMIT 10""")

q("创建时间分布（按月）",
  """SELECT DATE_FORMAT(created_at, '%Y-%m') AS 月份, COUNT(*)
     FROM recipes GROUP BY 1 ORDER BY 1""")

conn.close()
