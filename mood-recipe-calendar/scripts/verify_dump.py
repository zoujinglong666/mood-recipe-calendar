"""校验导出的两份全量同步 SQL。"""
import re
from pathlib import Path

D = Path(__file__).resolve().parent.parent / "db-export" / "sql"

for fn in ["sync_all_full.sql", "sync_all_data_only.sql"]:
    p = D / fn
    t = p.read_text(encoding="utf-8", errors="replace")
    tables = sorted(set(re.findall(r"REPLACE INTO `(\w+)`", t)))
    creates = re.findall(r"CREATE TABLE (?:IF NOT EXISTS )?`(\w+)`", t)
    drops = re.findall(r"DROP TABLE IF EXISTS `(\w+)`", t)
    reps = len(re.findall(r"REPLACE INTO `", t))
    print(f"--- {fn} ({p.stat().st_size / 1024:.1f} KB) ---")
    print(f"  REPLACE 语句 {reps} 条，覆盖 {len(tables)} 张表")
    print(f"  CREATE TABLE {len(creates)} 条，DROP TABLE {len(drops)} 条")
    print(f"  表清单：{tables}")
    print()

# 中文编码抽查
t = (D / "sync_all_full.sql").read_text(encoding="utf-8")
m = re.findall(r"REPLACE INTO `recipes` .*?(?=\nREPLACE INTO|\n-- |\Z)", t, re.S)
print(f"recipes 表 REPLACE 语句 {len(m)} 条")
if m:
    print("首条片段：", m[0][:260])
