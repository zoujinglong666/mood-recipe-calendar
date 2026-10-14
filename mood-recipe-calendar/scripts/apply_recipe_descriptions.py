# -*- coding: utf-8 -*-
"""按 JSON 映射批量重写 recipes.description，并保留原始值备份。

JSON 格式：{"580": "新描述", "587": "新描述", ...}

用法：
  python scripts/apply_recipe_descriptions.py --data <json路径> --dry-run
  python scripts/apply_recipe_descriptions.py --data <json路径> --apply

备份文件（原始描述，累积保留最早一版）：
  db-export/quality-fix/descriptions_original.json
"""
import argparse
import json
import os
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

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKUP = os.path.join(ROOT, "db-export", "quality-fix", "descriptions_original.json")
MAX_LEN = 255


def load_backup():
    if os.path.exists(BACKUP):
        with open(BACKUP, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_backup(data):
    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    with open(BACKUP, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def validate(rid, text):
    if not text or not text.strip():
        return f"id={rid} 描述为空"
    if len(text) > MAX_LEN:
        return f"id={rid} 描述 {len(text)} 字，超过字段上限 {MAX_LEN}"
    if "![" in text or "](" in text:
        return f"id={rid} 描述仍含 Markdown 图片语法"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="id -> 新描述 的 JSON 文件")
    ap.add_argument("--apply", action="store_true", help="写回数据库")
    ap.add_argument("--dry-run", action="store_true", help="仅预览")
    args = ap.parse_args()
    write = args.apply and not args.dry_run

    with open(args.data, encoding="utf-8") as f:
        patch = json.load(f)

    errors = []
    for k, v in patch.items():
        err = validate(k, v)
        if err:
            errors.append(err)
    if errors:
        print("校验未通过，未做任何改动：")
        for e in errors:
            print("  -", e)
        return

    conn = pymysql.connect(**DB)
    cur = conn.cursor(pymysql.cursors.DictCursor)
    ids = [int(k) for k in patch]
    fmt = ",".join(["%s"] * len(ids))
    cur.execute(f"SELECT id, name, description FROM recipes WHERE id IN ({fmt})", ids)
    rows = {r["id"]: r for r in cur.fetchall()}

    backup = load_backup()
    changed = 0
    for k in sorted(patch, key=lambda x: int(x)):
        rid = int(k)
        new = patch[k].strip()
        r = rows.get(rid)
        if not r:
            print(f"[{rid}] !! 库中不存在，跳过")
            continue
        old = r["description"] or ""
        same = old == new
        print("=" * 68)
        print(f"[{rid}] {r['name']}  {'（内容一致）' if same else '（待更新）'}")
        print(f"   旧：{old}")
        print(f"   新：{new}")
        if same:
            continue
        changed += 1
        if str(rid) not in backup:
            backup[str(rid)] = {"name": r["name"], "description": old}
        if write:
            cur.execute("UPDATE recipes SET description=%s WHERE id=%s", (new, rid))

    if write:
        conn.commit()
        save_backup(backup)
        print(f"\n原始描述已备份到：{BACKUP}")

    print(f"\n共 {len(patch)} 条，实际更新 {changed} 条；模式："
          f"{'写库' if write else '预览'}；时间：{datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
