#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导出 source=NULL 的菜谱到 CSV"""
import subprocess, json, csv

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

cmd = [MYSQL, "-uroot", "-p12345678", "mood_recipe", "--default-character-set=utf8mb4", "-e",
       "SELECT id, name, description, IFNULL(ingredients,''), IFNULL(steps,''), IFNULL(cooking_time,0), IFNULL(difficulty,''), IFNULL(mood_tags,''), IFNULL(season,'') FROM recipes WHERE source IS NULL ORDER BY id;"]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')
lines = result.stdout.strip().split('\n')
rows = []
for line in lines[1:]:
    parts = line.split('\t')
    if len(parts) >= 9:
        rows.append(parts)

print(f"共 {len(rows)} 条")

out = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\原始种子菜谱清单.csv"
with open(out, 'w', newline='', encoding='utf-8-sig') as f:
    w = csv.writer(f)
    w.writerow(["ID", "菜名", "简介", "食材清单", "做法步骤", "烹饪时间(分钟)", "难度", "心情标签", "季节"])
    for r in rows:
        try:
            ings = json.loads(r[3]) if r[3] else []
            ing_text = "\n".join(f"• {i}" for i in ings)
        except:
            ing_text = r[3]
        try:
            steps = json.loads(r[4]) if r[4] else []
            step_text = "\n".join(f"{i+1}. {s}" for i, s in enumerate(steps))
        except:
            step_text = r[4]
        w.writerow([r[0], r[1], r[2], ing_text, step_text, r[5], r[6], r[7], r[8]])

print(f"已保存: {out}")

from collections import Counter
tags = Counter()
diffs = Counter()
for r in rows:
    if r[7]:
        for t in r[7].split(','):
            tags[t.strip()] += 1
    diffs[r[6]] += 1

print("\n难度分布:")
for d, c in diffs.most_common():
    print(f"  {d}: {c}")
print("\n心情标签Top10:")
for t, c in tags.most_common(10):
    print(f"  {t}: {c}")
