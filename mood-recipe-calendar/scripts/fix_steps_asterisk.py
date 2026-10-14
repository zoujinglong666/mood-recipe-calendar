"""清理 steps 字段里 Markdown 强调星号残留。

规则：仅当 *...* 内是纯中文/中文标点/空格时删除星号；
`4cm*4cm` 这类含字母数字的尺寸表达保留原样。
用法：python fix_steps_asterisk.py --dry-run | --apply
"""
import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path

import pymysql

DB = dict(host="127.0.0.1", port=3306, user="root", password="12345678",
          database="mood_recipe", charset="utf8mb4")

PAT = re.compile(r"\*([^*]{1,30}?)\*")
CN_ONLY = re.compile(r"^[\u4e00-\u9fff，。、；：？！（）「」“”‘’·\s]+$")
# 尺寸写法：数字+单位 后面跟 * 再接数字，属于乘号，改成 ×（如 4cm*4cm*4cm）
DIM = re.compile(r"(\d+(?:\.\d+)?\s*(?:cm|mm|m|g|kg|ml|L|℃)\s*)\*(?=\s*\d)")

BASE = Path(__file__).resolve().parent.parent
BACKUP = BASE / "db-export" / "quality-fix" / "steps_asterisk_backup.json"


def clean(text: str):
    if not text or "*" not in text:
        return text, 0
    # 先把尺寸乘号规范化
    text = DIM.sub(r"\1×", text)
    if "*" not in text:
        return text, 1
    count = 0

    def repl(m):
        nonlocal count
        inner = m.group(1)
        if "*" in inner:
            return m.group(0)
        count += 1
        return inner

    return PAT.sub(repl, text), count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=None)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute("SELECT id, name, steps FROM recipes WHERE steps LIKE '%%*%%' ORDER BY id")
    rows = cur.fetchall()

    backup = {}
    if BACKUP.exists():
        backup = json.loads(BACKUP.read_text(encoding="utf-8"))

    changed = 0
    total_stars = 0
    for rid, name, steps in rows:
        new, cnt = clean(steps)
        if cnt == 0 or new == steps:
            continue
        changed += 1
        total_stars += cnt
        if args.dry_run:
            print(f"[{rid}] {name}")
            i = steps.find("*")
            print("  旧:", steps[max(0, i - 30):i + 40].replace("\n", " "))
            j = new.find("*")
            if j >= 0:
                print("  新:", new[max(0, j - 30):j + 40].replace("\n", " "))
            else:
                print("  新:（已无星号）")
        else:
            key = str(rid)
            if key not in backup:
                backup[key] = {"name": name, "steps": steps}
            cur.execute("UPDATE recipes SET steps=%s WHERE id=%s", (new, rid))

    mode = "预演" if args.dry_run else "写库"
    if args.apply:
        if not backup:
            print("没有需要修改的记录。")
            conn.close()
            return
        BACKUP.write_text(json.dumps(backup, ensure_ascii=False, indent=2), encoding="utf-8")
        conn.commit()
        print(f"原始 steps 已备份到：{BACKUP}")

    print(f"\n共 {len(rows)} 条含星号，其中 {changed} 条需要清理，累计删除强调星号 {total_stars} 处；模式：{mode}；时间：{datetime.now():%Y-%m-%d %H:%M:%S}")
    conn.close()


if __name__ == "__main__":
    main()
