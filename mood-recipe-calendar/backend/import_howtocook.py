#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""解析 HowToCook 所有菜谱，生成 INSERT SQL"""
import os, re, json, pathlib

BASE = pathlib.Path(r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\howtocook-ds\HowToCook-master\dishes")

CAT_MAP = {
    "meat_dish": "荤菜",
    "vegetable_dish": "素菜",
    "aquatic": "水产",
    "soup": "汤品",
    "staple": "主食",
    "breakfast": "早餐",
    "dessert": "甜点",
    "drink": "饮品",
    "condiment": "调料",
    "semi-finished": "半成品",
}

def parse_md(filepath):
    text = filepath.read_text(encoding='utf-8')
    lines = text.split('\n')

    # 菜名: # XXX的做法
    name = ""
    for line in lines:
        if line.startswith('# '):
            name = line.replace('# ', '').replace('的做法', '').strip()
            break

    # 简介: # 标题后的第一段非空文本
    desc = ""
    found_title = False
    for line in lines:
        if line.startswith('# '):
            found_title = True
            continue
        if found_title and line.strip() and not line.startswith('预估') and not line.startswith('---'):
            desc = line.strip()
            break

    # 难度: 星数
    difficulty = "简单"
    for line in lines:
        if '预估烹饪难度' in line:
            stars = line.count('★')
            if stars <= 1: difficulty = "简单"
            elif stars == 2: difficulty = "简单"
            elif stars == 3: difficulty = "中等"
            elif stars == 4: difficulty = "较难"
            else: difficulty = "困难"
            break

    # 烹饪时间: 从简介提取
    cook_time = 30
    m = re.search(r'(\d+)\s*分钟', desc)
    if m:
        cook_time = int(m.group(1))

    # 食材列表
    ingredients = []
    in_ingredients = False
    for line in lines:
        if '必备原料' in line or '原料和工具' in line:
            in_ingredients = True
            continue
        if in_ingredients:
            if line.startswith('## '):
                break
            s = line.strip()
            if s.startswith('- ') or s.startswith('* '):
                ing = s[2:].strip()
                # 去掉括号注释
                ing = re.sub(r'[（(].*?[)）]', '', ing).strip()
                if ing and ing not in ['水']:
                    ingredients.append(ing)

    # 步骤
    steps = []
    in_steps = False
    for line in lines:
        if line.strip() == '## 操作' or line.strip().startswith('## 操作'):
            in_steps = True
            continue
        if in_steps:
            if line.startswith('## ') and '操作' not in line:
                break
            s = line.strip()
            # 编号步骤: "1. xxx" 或 "### 子标题" 忽略
            m = re.match(r'^\d+[\.、]\s*(.+)', s)
            if m:
                step_text = m.group(1).strip()
                # 去掉加粗标记
                step_text = step_text.replace('**', '')
                if step_text:
                    steps.append(step_text)

    return {
        'name': name,
        'desc': desc[:200],
        'difficulty': difficulty,
        'cook_time': cook_time,
        'ingredients': ingredients,
        'steps': steps,
    }

# 遍历所有md
recipes = []
for cat_dir in BASE.iterdir():
    if not cat_dir.is_dir() or cat_dir.name == 'template':
        continue
    cat_name = CAT_MAP.get(cat_dir.name, cat_dir.name)
    for md_file in cat_dir.rglob('*.md'):
        try:
            r = parse_md(md_file)
            if r['name'] and len(r['steps']) >= 2 and len(r['ingredients']) >= 2:
                r['category'] = cat_name
                recipes.append(r)
        except Exception as e:
            print(f"跳过 {md_file}: {e}")

print(f"共解析 {len(recipes)} 道菜")

# 生成SQL
sql_lines = []
sql_lines.append("-- HowToCook 菜谱导入 (MIT License)")
sql_lines.append("SET NAMES utf8mb4;")
sql_lines.append("")

for i, r in enumerate(recipes):
    name = r['name'].replace("'", "''")
    desc = r['desc'].replace("'", "''")
    ingredients_json = json.dumps(r['ingredients'], ensure_ascii=False)
    steps_json = json.dumps(r['steps'], ensure_ascii=False)
    mood_tags = r['category']
    source = 'howtocook'

    sql = (f"INSERT INTO recipes (name, description, ingredients, steps, cooking_time, difficulty, mood_tags, source, meal_type, taste, tags, created_at) "
           f"VALUES ('{name}', '{desc}', '{ingredients_json}', '{steps_json}', "
           f"{r['cook_time']}, '{r['difficulty']}', '{mood_tags}', '{source}', 'all', NULL, '{r['category']}', NOW());")
    sql_lines.append(sql)

out = pathlib.Path(r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\import_howtocook.sql")
out.write_text('\n'.join(sql_lines), encoding='utf-8')
print(f"SQL写入: {out}")
print(f"\n前3道菜:")
for r in recipes[:3]:
    print(f"  - {r['name']} ({r['category']}, {r['cook_time']}分钟, {len(r['steps'])}步, {len(r['ingredients'])}食材)")
