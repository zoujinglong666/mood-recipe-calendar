# -*- coding: utf-8 -*-
"""清理 recipes 表 steps / ingredients 字段中残留的 Markdown 图片语法。

背景：从 GitHub（HowToCook / CookLikeHOC）批量导入时，把原文的图片行一并写进了
步骤和食材数组，形成两种残留：
  1. 独立数组项，如 "![油焖大虾-预览图-1](./油焖大虾.jpg)"
  2. 内联在文字尾部，如 "准备一个空碗……搅拌均匀。![料汁](./酱汁.jpeg)"
  3. 不完整语法，如 "泡发好的海参![海参]"

处理策略：剔除图片语法本身，保留同一条目里的文字；整条只有图片的条目直接删除。

用法：
  python scripts/fix_recipe_field_images.py --dry-run   # 只看改动
  python scripts/fix_recipe_field_images.py --apply     # 写回数据库
"""
import argparse
import json
import os
import re
import datetime

import pymysql

DB = dict(
    host="127.0.0.1",
    port=3306,
    user="root",
    password="12345678",
    database="mood_recipe",
    charset="utf8mb4",
)

# 目标：扫描出的 6 条含图片残留的菜谱
TARGET_IDS = [582, 584, 586, 589, 872, 905]

IMG_FULL = re.compile(r"!\[[^\]]*\]\([^)]*\)")   # ![alt](url)
IMG_BARE = re.compile(r"!\[[^\]]*\]")            # ![alt]

BACKUP_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "db-export", "quality-fix")


def clean_text(s):
    """剔除一条文案里的 Markdown 图片语法，返回清理后的文本。"""
    s = IMG_FULL.sub("", s)
    s = IMG_BARE.sub("", s)
    return s.strip()


def clean_array(raw):
    """清理 JSON 数组字符串，返回 (新JSON, 原列表, 新列表, 变更描述列表)。"""
    if raw is None or raw.strip() == "":
        return raw, [], [], []
    try:
        items = json.loads(raw)
    except json.JSONDecodeError:
        return raw, [], [], []
    if not isinstance(items, list):
        return raw, [], [], []

    new_items, changes = [], []
    for it in items:
        if not isinstance(it, str):
            new_items.append(it)
            continue
        cleaned = clean_text(it)
        if cleaned != it:
            if cleaned:
                changes.append({"type": "remove_image", "before": it, "after": cleaned})
            else:
                changes.append({"type": "drop_item", "before": it, "after": None})
        if cleaned:
            new_items.append(cleaned)
    new_raw = json.dumps(new_items, ensure_ascii=False)
    return new_raw, items, new_items, changes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="写回数据库")
    ap.add_argument("--dry-run", action="store_true", help="仅预览，不写库")
    args = ap.parse_args()
    write = args.apply and not args.dry_run

    conn = pymysql.connect(**DB)
    cur = conn.cursor(pymysql.cursors.DictCursor)
    fmt = ",".join(["%s"] * len(TARGET_IDS))
    cur.execute(f"SELECT id, name, steps, ingredients FROM recipes WHERE id IN ({fmt})", TARGET_IDS)
    rows = cur.fetchall()

    backup = {
        "created_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "table": "recipes",
        "fields": ["steps", "ingredients"],
        "rows": [],
    }

    total_changes = 0
    for r in sorted(rows, key=lambda x: x["id"]):
        rid, name = r["id"], r["name"]
        print("=" * 68)
        print(f"[{rid}] {name}")

        updates = {}
        for field in ("steps", "ingredients"):
            raw = r[field]
            new_raw, items, new_items, changes = clean_array(raw)
            if changes:
                updates[field] = new_raw
                total_changes += len(changes)
                print(f"  -- {field}：剔除 {len(changes)} 处图片残留，"
                      f"条目 {len(items)} -> {len(new_items)}")
                for c in changes:
                    if c["type"] == "drop_item":
                        print(f"     [删整条] {c['before']}")
                    else:
                        print(f"     [删图片] {c['before']}")
                        print(f"      -> {c['after']}")

        backup["rows"].append({"id": rid, "name": name,
                               "steps": r["steps"], "ingredients": r["ingredients"]})

        if updates and write:
            sets = ", ".join([f"{k}=%s" for k in updates])
            cur.execute(f"UPDATE recipes SET {sets} WHERE id=%s",
                        list(updates.values()) + [rid])
            print(f"  => 已写回：{', '.join(updates.keys())}")
        elif updates:
            print("  => 预览模式，未写库")

    if write:
        conn.commit()
        os.makedirs(BACKUP_DIR, exist_ok=True)
        path = os.path.join(BACKUP_DIR, "steps_ingredients_backup.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(backup, f, ensure_ascii=False, indent=2)
        print("\n原始值已备份到：", path)

    print(f"\n合计剔除 {total_changes} 处图片残留；模式：{'写库' if write else '预览'}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
