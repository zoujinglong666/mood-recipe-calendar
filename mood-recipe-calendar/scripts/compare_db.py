"""比对源库与验证库的表、行数和内容哈希。"""
import hashlib

import pymysql

SRC, TMP = "mood_recipe", "mood_recipe_verify"
conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", charset="utf8mb4", autocommit=True)
cur = conn.cursor()


def tables(db):
    cur.execute("""SELECT table_name FROM information_schema.tables
                   WHERE table_schema=%s ORDER BY table_name""", (db,))
    return [r[0] for r in cur.fetchall()]


def snapshot(db, ts):
    out = {}
    for t in ts:
        cur.execute(f"SELECT COUNT(*) FROM `{db}`.`{t}`")
        n = cur.fetchone()[0]
        cur.execute(f"SELECT * FROM `{db}`.`{t}` ORDER BY 1")
        h = hashlib.md5()
        for row in cur.fetchall():
            h.update(repr(row).encode("utf-8", "replace"))
        out[t] = (n, h.hexdigest()[:12])
    return out


src_t, tmp_t = tables(SRC), tables(TMP)
print(f"源库表数 {len(src_t)}，验证库表数 {len(tmp_t)}")
missing = set(src_t) - set(tmp_t)
extra = set(tmp_t) - set(src_t)
print(f"缺失表：{sorted(missing) or '无'}")
print(f"多余表：{sorted(extra) or '无'}")

a, b = snapshot(SRC, src_t), snapshot(TMP, src_t)
diff_row, diff_hash = [], []
for t in src_t:
    if a[t][0] != b[t][0]:
        diff_row.append((t, a[t][0], b[t][0]))
    elif a[t][1] != b[t][1]:
        diff_hash.append((t, a[t][1], b[t][1]))

print(f"\n行数不一致：{len(diff_row)}")
for d in diff_row:
    print("  ", d)
print(f"行数相同但内容哈希不同：{len(diff_hash)}")
for d in diff_hash:
    print("  ", d)

total = sum(v[0] for v in a.values())
print(f"\n源库总行数：{total}")
if not missing and not extra and not diff_row and not diff_hash:
    print("验证通过：验证库与源库的表结构、行数、内容完全一致。")
conn.close()
