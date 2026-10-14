"""核对同名菜代表版本的信息完整度：id 最小那条 vs 同组最优那条。"""
import pymysql
import json

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", database="mood_recipe",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute("""SELECT id, name, description, ingredients, steps, source
               FROM recipes ORDER BY id""")
rows = cur.fetchall()
conn.close()


def cnt(v):
    """数组型字段的条目数"""
    if not v:
        return 0
    try:
        d = json.loads(v)
        return len(d) if isinstance(d, list) else 0
    except Exception:
        return len([x for x in str(v).split(",") if x.strip()])


def score(r):
    """信息完整度：食材数 + 步骤数，描述长度作次要因素"""
    return cnt(r[3]) + cnt(r[4]) * 1.5 + min(len(r[2] or ""), 60) / 20


groups = {}
for r in rows:
    groups.setdefault(r[1], []).append(r)

multi = {k: v for k, v in groups.items() if len(v) > 1}
print(f"同名菜组数：{len(multi)}\n")

same = diff = 0
worse = []
for name, g in multi.items():
    picked = g[0]                      # 当前规则：id 最小
    best = max(g, key=score)           # 信息最全
    if best[0] == picked[0]:
        same += 1
    else:
        diff += 1
        if score(picked) < score(best) * 0.8:   # 明显更差才记录
            worse.append((name, picked, best))

print(f"id 最小 = 信息最全 的组：{same}")
print(f"两者不同的组：{diff}")
print(f"其中 id 最小那版明显更差的组：{len(worse)}\n")

for name, p, b in worse[:12]:
    print(f"《{name}》")
    print(f"   现取 id={p[0]} src={p[5]}  食材{cnt(p[3])} 步骤{cnt(p[4])} | {(p[2] or '')[:34]}")
    print(f"   较优 id={b[0]} src={b[5]}  食材{cnt(b[3])} 步骤{cnt(b[4])} | {(b[2] or '')[:34]}")
