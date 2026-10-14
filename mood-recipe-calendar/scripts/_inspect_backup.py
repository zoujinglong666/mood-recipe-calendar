import json
from pathlib import Path

QF = Path(__file__).resolve().parent.parent / "db-export" / "quality-fix"

for fn in ["descriptions_original.json", "steps_asterisk_backup.json", "steps_ingredients_backup.json"]:
    p = QF / fn
    if not p.exists():
        print(fn, "-> 不存在")
        continue
    d = json.loads(p.read_text(encoding="utf-8"))
    keys = list(d)
    print(f"{fn}: 类型={type(d).__name__} 条数={len(d)}")
    print("  首个 key:", keys[0], "| 值类型:", type(d[keys[0]]).__name__)
    v = d[keys[0]]
    if isinstance(v, dict):
        print("  值的字段:", list(v))
    else:
        print("  值示例:", str(v)[:80])
    print()
