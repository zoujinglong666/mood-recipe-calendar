"""把本地 recipes 表导出为带 BOM 的 UTF-8 CSV，便于 Excel 直接打开。"""
import csv
import json
import pymysql

OUT = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\db-export\recipes_export.csv"

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute("""
    SELECT id, name, description, image, cooking_time, difficulty,
           season, source, taste, meal_type, created_at,
           ingredients, steps, mood_tags, tags, seasonings
    FROM recipes ORDER BY id
""")
rows = cur.fetchall()
cols = [d[0] for d in cur.description]
conn.close()


def flat(v):
    """JSON 数组 -> 顿号分隔的纯文本，方便在表格里看。"""
    if v is None:
        return ""
    s = str(v).strip()
    if s.startswith("["):
        try:
            arr = json.loads(s)
            return " / ".join(str(x).strip() for x in arr if str(x).strip())
        except Exception:
            pass
    return s


with open(OUT, "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(cols)
    for r in rows:
        out = list(r)
        # 数组型字段转成可读文本
        for idx in (11, 12, 13, 14, 15):
            out[idx] = flat(r[idx])
        w.writerow(out)

import os
print(f"已导出 {len(rows)} 条 -> {OUT}")
print(f"文件大小 {round(os.path.getsize(OUT)/1024, 1)} KB")
print("字段：", ", ".join(cols))
