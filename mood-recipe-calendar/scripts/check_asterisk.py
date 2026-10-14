import re

import pymysql

c = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                    password="12345678", database="mood_recipe", charset="utf8mb4")
cur = c.cursor()
cur.execute("SELECT id, name, steps FROM recipes WHERE steps LIKE '%%*%%' ORDER BY id")
for rid, n, s in cur.fetchall():
    for m in re.finditer(r"[^*]{0,14}\*[^*]{1,20}\*[^*]{0,6}", s):
        print(rid, n, "|", m.group(0).replace("\n", " "))
