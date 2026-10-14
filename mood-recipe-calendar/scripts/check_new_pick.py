"""抽查：新取法下，同名菜选中了哪一版。"""
import json
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute("SELECT id, name, description, ingredients, steps, source FROM recipes ORDER BY id")
rows = cur.fetchall()
conn.close()


def cnt(v):
    if not v:
        return 0
    try:
        d = json.loads(v)
        return len(d) if isinstance(d, list) else 0
    except Exception:
        return len([x for x in str(v).split(",") if x.strip()])


def richness(r):
    return cnt(r[3]) + cnt(r[4]) * 1.5 + min(len(r[2] or ""), 60) / 20


groups = {}
for r in rows:
    groups.setdefault(r[1], []).append(r)

CHECK = ["宫保鸡丁", "回锅肉", "鱼香肉丝", "麻婆豆腐", "可乐鸡翅",
         "水煮牛肉", "蛋炒饭", "梅菜扣肉", "小炒肉", "鸡蛋羹"]

print(f"{'菜名':<8} {'选中id':>6} {'来源':<10} {'食材':>4} {'步骤':>4}   说明")
print("-" * 78)
ok = 0
for name in CHECK:
    g = groups.get(name)
    if not g:
        continue
    picked = max(g, key=lambda r: (richness(r), -r[0]))
    full = max(g, key=richness)
    flag = "✓ 已是同组最全" if picked[0] == full[0] else "✗ 未取到最全"
    if picked[0] == full[0]:
        ok += 1
    print(f"{name:<8} {picked[0]:>6} {str(picked[5]):<10} "
          f"{cnt(picked[3]):>4} {cnt(picked[4]):>4}   {flag}")

print(f"\n抽查 {len(CHECK)} 道，取到同组最全版本的：{ok} 道")

# 全量统计
better = 0
for name, g in groups.items():
    if len(g) < 2:
        continue
    picked = max(g, key=lambda r: (richness(r), -r[0]))
    if picked[0] == max(g, key=richness)[0]:
        better += 1
print(f"全部 {len([1 for v in groups.values() if len(v) > 1])} 组同名菜中，"
      f"取到信息最全版本的：{better} 组")
