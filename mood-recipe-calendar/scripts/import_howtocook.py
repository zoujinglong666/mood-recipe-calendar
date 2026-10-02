# -*- coding: utf-8 -*-
"""
import_howtocook.py — 解析 HowToCook（程序员做饭指南）菜谱并灌入 recipes 表

数据源：https://github.com/Anduin2017/HowToCook （Unlicense 公共领域，可商用）
先浅克隆到 corpus/HowToCook：
  git clone --depth 1 https://github.com/Anduin2017/HowToCook corpus/HowToCook

解析规则（实测两种排版均覆盖）：
  1) 荤菜式：dishes/meat_dish/宫保鸡丁/宫保鸡丁.md，`计算` 用 `名称 = 用量`，
     `操作` 分 `### 简易版本` / `### 稍加复杂...` 两个版本
  2) 早餐式：dishes/breakfast/牛奶燕麦.md 平铺，`计算` 用 `emoji 名称 用量/per`，
     `操作` 按组件分多个小节（燕麦常规/快速/煎蛋）

步骤选择策略：操作节存在「简易/简单」小节 → 只取该小节（避免简易+进阶重复）；
否则合并全部小节步骤（组件式菜谱合并才是完整做法）。

食材：优先 `计算` 节（名称+用量），清洗 emoji / “ = ” / “/per”；
      无计算节时回退 `必备原料和工具` 节（仅名称）。

落库：按菜名 upsert——已有同名菜（含之前 AI 灌的脏数据）就地修复真实食材/步骤，
      保留其 source/image；新菜插入 source='LOCAL'。mood_tags 不编造，留空。

用法：
  python3 scripts/import_howtocook.py --dry-run            # 只解析统计
  python3 scripts/import_howtocook.py --json parsed.json   # 导出解析产物供检查
  python3 scripts/import_howtocook.py                      # 落库（默认本地库）
  DB_HOST=.. DB_PASSWORD=.. python3 scripts/import_howtocook.py   # 指向线上库
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

import pymysql

# 工作区根 = 项目目录(mood-recipe-calendar)的上一层；corpus/HowToCook 克隆在工作区根
REPO = Path(__file__).resolve().parent.parent.parent / "corpus" / "HowToCook"
DISH_DIR = REPO / "dishes"

CATEGORY = {
    "meat_dish": "荤菜", "vegetable_dish": "素菜", "aquatic": "水产",
    "breakfast": "早餐", "staple": "主食", "soup": "汤羹",
    "drink": "饮品", "dessert": "甜品", "condiment": "酱料",
    "semi-finished": "半成品",
}

FALLBACK_IMAGE = "/static/dish_tomato_beef.png"

# ---------- 章节切分 ----------

def sections(text):
    """把 markdown 按 '## ' 切成 {标题: 正文}，标题去空白。"""
    result, current = {}, "HEAD"
    result[current] = []
    for line in text.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
            result[current] = []
        else:
            result.setdefault(current, []).append(line)
    return {k: "\n".join(v) for k, v in result.items()}


def subsections(body):
    """把章节正文按 '### ' 切成 [(小节标题, 正文)]。"""
    groups, current = [], ("", [])
    for line in body.splitlines():
        if line.startswith("### "):
            if current[0] or current[1]:
                groups.append((current[0], "\n".join(current[1])))
            current = (line[4:].strip(), [])
        else:
            current[1].append(line)
    if current[0] or current[1]:
        groups.append((current[0], "\n".join(current[1])))
    return groups


def bullets(body):
    """取章节里的无序/有序列表项原文。"""
    items = []
    for line in body.splitlines():
        s = line.strip()
        if re.match(r"^[-*]\s+", s) or re.match(r"^\d+[.、)．]\s*", s):
            items.append(re.sub(r"^([-*]|\d+[.、)．])\s*", "", s).strip())
    return items

# ---------- 清洗 ----------

EMOJI_LEAD = re.compile(r"^[^\w\u4e00-\u9fff\u3000-\u303f（(【\[]+")


def clean_item(item):
    s = item.strip()
    s = EMOJI_LEAD.sub("", s)              # 去行首 emoji / 装饰符号
    s = s.replace(" = ", " ")              # “名称 = 用量” → “名称 用量”
    s = re.sub(r"\s*/per\s*$", "", s)      # 去 “/per”
    s = re.sub(r"\s*\*?\s*份数\s*$", "", s)  # 去 “* 份数” 乘数标记
    return s.strip()


def is_noise(item):
    if not item or item.startswith("![") or "计算出计划" in item or item.startswith("使用上述"):
        return True
    # “- 必须配料 / 进阶配料 / 可选原料”这类小节引导列表项，不是食材
    return bool(re.fullmatch(r"[必须进可选]{0,2}(配料|原料|工具|食材|调料)", item))

# ---------- 字段解析 ----------

def parse_name(text, fallback):
    m = re.search(r"^#\s+(.+?)\s*$", text, re.M)
    if not m:
        return fallback
    return re.sub(r"的做法$", "", m.group(1)).strip() or fallback


def parse_description(text, category):
    # 标题后第一段非空、非元数据行作为简介
    after_head = re.split(r"^#\s+.+$", text, maxsplit=1, flags=re.M)[-1]
    for para in after_head.split("\n\n"):
        p = para.strip()
        if p and not p.startswith(("#", "预估", ">", "![", "---")):
            p = re.sub(r"\s+", " ", p)
            return p[:120]
    return f"HowToCook 收录的{category}家常做法。"


def parse_difficulty(text):
    m = re.search(r"预估(?:烹饪)?难度[：:]\s*(★+)", text)
    if not m:
        return "简单"
    stars = len(m.group(1))
    if stars <= 2:
        return "简单"
    return "普通" if stars == 3 else "中等"


CN_NUM = {"一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5,
          "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}


def _cn_to_num(seg):
    """『一/两/十/2.5/半』→ 数值；解析失败返回 None。"""
    seg = seg.strip()
    if seg == "半":
        return 0.5
    if all(c in CN_NUM for c in seg) and seg:
        total, cur = 0, 0
        for c in seg:
            n = CN_NUM[c]
            if n == 10:
                cur = (cur or 1) * 10
            else:
                cur += n
        return total + cur
    try:
        return float(seg)
    except ValueError:
        return None


def parse_cooking_time(text):
    """耗时没有独立元数据行，写在简介里且多为中文数字：『总耗时约一小时』『8 小时 15 分钟』『半小时』。"""
    m = re.search(r"耗时[约]?\s*([^。；;\n]{0,24})", text)
    if not m:
        return None
    seg = m.group(1)
    total = 0
    for unit, mult in (("小时", 60), ("分钟", 1)):
        um = re.search(r"([一二两三四五六七八九十\d.]+)\s*" + unit, seg)
        if um:
            val = _cn_to_num(um.group(1))
            if val is not None:
                total += int(val * mult)
    return total or None


def parse_ingredients(text):
    secs = sections(text)
    items = []
    for title, body in secs.items():
        if title == "计算":
            items = [clean_item(i) for i in bullets(body)]
            break
    items = [i for i in items if not is_noise(i)]
    if items:
        return items
    # 回退：必备原料和工具（仅名称；跳过「可选」小节与工具行）
    for title, body in secs.items():
        if "必备原料" in title:
            for sub_title, sub_body in subsections(body):
                if "可选" in sub_title:
                    continue
                for raw in bullets(sub_body):
                    item = clean_item(raw)
                    if not is_noise(item):
                        items.append(item)
    return items


def parse_steps(text):
    secs = sections(text)
    body = next((v for k, v in secs.items() if k.startswith("操作")), "")
    if not body:
        return []
    subs = [(t, b) for t, b in subsections(body) if t]  # 带标题的小节
    chosen = []
    simple = [(t, b) for t, b in subs if re.search(r"简易|简单", t)]
    if simple:
        chosen = simple                                   # 只取简易版，避免与进阶版重复
    elif subs:
        chosen = subs                                     # 组件式：合并全部小节
    groups = chosen or [("", body)]                       # 无小节：顶层列表
    steps = []
    for title, sub_body in groups:
        for raw in bullets(sub_body):
            item = clean_item(raw)
            if not is_noise(item):
                steps.append(item)
    return steps

# ---------- 主流程 ----------

def iter_dish_files():
    for md in sorted(DISH_DIR.rglob("*.md")):
        rel = md.relative_to(DISH_DIR)
        if rel.parts[0] == "template" or md.stem == "README":
            continue
        yield rel


def parse_file(md):
    rel = md.relative_to(DISH_DIR)
    category = CATEGORY.get(rel.parts[0], "家常菜")
    text = md.read_text(encoding="utf-8")
    name = parse_name(text, md.stem)
    ingredients = parse_ingredients(text)
    steps = parse_steps(text)
    return {
        "file": str(rel), "name": name, "category": category,
        "description": parse_description(text, category),
        "difficulty": parse_difficulty(text),
        "cooking_time": parse_cooking_time(text),
        "ingredients": ingredients, "steps": steps,
    }


def db_connect(args):
    return pymysql.connect(
        host=args.db_host, port=args.db_port, user=args.db_user,
        password=args.db_password, database=args.db_name,
        charset="utf8mb4", autocommit=False)


def upsert(conn, dish):
    """按菜名 upsert：同名（含脏数据菜）就地修复；新菜插入 source=LOCAL。"""
    with conn.cursor() as cur:
        cur.execute("SELECT id, source, image FROM recipes WHERE name = %s", (dish["name"],))
        rows = cur.fetchall()
        ing = json.dumps(dish["ingredients"], ensure_ascii=False)
        stp = json.dumps(dish["steps"], ensure_ascii=False)
        fields = {
            "ingredients": ing, "steps": stp,
            "cooking_time": dish["cooking_time"],
            "difficulty": dish["difficulty"],
            "description": dish["description"],
            "season": "四季",
        }
        if rows:
            sets, params = [], []
            for col, val in fields.items():
                if val is not None:
                    sets.append(f"{col} = %s")
                    params.append(val)
            # 按菜名更新全部同名行（库里存在重复名记录，需一并修复）
            cur.execute(
                f"UPDATE recipes SET {', '.join(sets)} WHERE name = %s",
                (*params, dish["name"]))
            return "updated", len(rows)
        cur.execute(
            "INSERT INTO recipes (name, description, image, ingredients, steps,"
            " cooking_time, difficulty, mood_tags, season, created_at, source)"
            " VALUES (%s,%s,%s,%s,%s,%s,%s,NULL,%s,NOW(),'LOCAL')",
            (dish["name"], dish["description"], FALLBACK_IMAGE, ing, stp,
             dish["cooking_time"], dish["difficulty"], "四季"))
        return "inserted", 1


def main():
    ap = argparse.ArgumentParser(description="Import HowToCook recipes into MySQL")
    ap.add_argument("--repo", default=str(REPO))
    ap.add_argument("--dry-run", action="store_true", help="只解析统计，不写库")
    ap.add_argument("--limit", type=int, default=0, help="最多处理 N 个文件（0=全部）")
    ap.add_argument("--json", dest="json_out", default="", help="导出解析产物 JSON")
    ap.add_argument("--sample", default="", help="只解析菜名含该关键词的文件（调试）")
    ap.add_argument("--db-host", default=os.getenv("DB_HOST", "127.0.0.1"))
    ap.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    ap.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    ap.add_argument("--db-password", default=os.getenv("DB_PASSWORD", "12345678"))
    ap.add_argument("--db-name", default=os.getenv("DB_NAME", "mood_recipe"))
    args = ap.parse_args()

    global DISH_DIR
    DISH_DIR = Path(args.repo) / "dishes"
    dishes, skipped = [], []
    for rel in iter_dish_files():
        if args.sample and args.sample not in rel.stem:
            continue
        dish = parse_file(DISH_DIR / rel)
        if len(dish["steps"]) < 2 or not dish["ingredients"]:
            skipped.append((dish["file"], "steps<2" if len(dish["steps"]) < 2 else "no ingredients"))
            continue
        dishes.append(dish)
        if args.limit and len(dishes) >= args.limit:
            break

    print(f"解析完成：可用 {len(dishes)} 道，跳过 {len(skipped)} 个")
    for f, why in skipped[:20]:
        print(f"  [跳过] {f}: {why}")

    cats = {}
    for d in dishes:
        cats[d["category"]] = cats.get(d["category"], 0) + 1
    print("分类分布：", json.dumps(cats, ensure_ascii=False))

    if args.json_out:
        Path(args.json_out).write_text(
            json.dumps(dishes, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"解析产物已导出：{args.json_out}")

    for d in dishes[:3]:
        print(f"\n===== 样例：{d['name']}（{d['category']}，{d['difficulty']}，"
              f"{d['cooking_time'] or '?'}分钟）=====")
        print("食材：", " | ".join(d["ingredients"][:8]))
        print("步骤：")
        for i, s in enumerate(d["steps"][:5], 1):
            print(f"  {i}. {s}")

    if args.dry_run:
        print("\n[dry-run] 未写库")
        return

    conn = db_connect(args)
    stats = {"inserted": 0, "updated": 0}
    dup_names = []
    try:
        for d in dishes:
            kind, n = upsert(conn, d)
            stats[kind] += 1
            if kind == "updated" and n > 1:
                dup_names.append(d["name"])
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    print(f"\n落库完成：新增 {stats['inserted']}，就地修复 {stats['updated']}")
    if dup_names:
        print(f"注意：{len(dup_names)} 道菜在库中存在多条同名记录，已全部更新："
              f"{dup_names[:10]}")


if __name__ == "__main__":
    sys.exit(main())
