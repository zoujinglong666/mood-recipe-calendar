"""端到端验证：把 sync_all_full.sql 导入临时库，再与源库逐表比对。"""
import pymysql
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
SRC, TMP = "mood_recipe", "mood_recipe_verify"
SQL = BASE / "db-export" / "sql" / "sync_all_full.sql"

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", charset="utf8mb4", autocommit=True)
cur = conn.cursor()

# 生成库名替换后的临时副本
sql = SQL.read_text(encoding="utf-8")
sql = sql.replace(f"`{SRC}`", f"`{TMP}`")
TMP_SQL = BASE / "db-export" / "sql" / "_verify_tmp.sql"
TMP_SQL.write_text(sql, encoding="utf-8")
print(f"临时副本已生成：{TMP_SQL.name}，{len(sql) / 1024:.1f} KB")


def tables(db):
    cur.execute("""SELECT table_name FROM information_schema.tables
                   WHERE table_schema=%s ORDER BY table_name""", (db,))
    return [r[0] for r in cur.fetchall()]


def counts(db, ts):
    out = {}
    for t in ts:
        cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{t}`")
        out[t] = cur.fetchone()[0]
    return out


src_tables = tables(SRC)
print(f"源库表数：{len(src_tables)}")
