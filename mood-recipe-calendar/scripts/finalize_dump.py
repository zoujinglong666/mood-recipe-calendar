"""清理验证环境，并给两份导出文件加使用说明头。"""
import pymysql
from datetime import datetime
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
D = BASE / "db-export" / "sql"

# 1. 清理临时库与临时副本
conn = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                       password="12345678", charset="utf8mb4", autocommit=True)
cur = conn.cursor()
cur.execute("DROP DATABASE IF EXISTS mood_recipe_verify")
print("临时库 mood_recipe_verify 已删除")
conn.close()

tmp = D / "_verify_tmp.sql"
if tmp.exists():
    tmp.unlink()
    print("临时副本 _verify_tmp.sql 已删除")

# 2. 加说明头
HEAD_FULL = f"""-- ============================================================
-- 心情菜谱 · 本地库全量同步到线上（完整版）
-- 导出时间：{datetime.now():%Y-%m-%d %H:%M:%S}
-- 来源     ：本地 mood_recipe（36 张表，1457 行数据）
-- 内容     ：DROP TABLE + CREATE TABLE + REPLACE INTO
--
-- 【执行方式】文件内不含 USE / CREATE DATABASE 语句，
--   必须在命令或客户端里指定目标库：
--   mysql -u<用户> -p -D <线上库名> < sync_all_full.sql
--
-- 【重要】本脚本会先 DROP 再重建这 36 张表，执行后线上这
--   36 张表的数据将与本地完全一致，线上原有的对应数据全部
--   被覆盖。如果线上已有真实用户、订单等数据，请勿直接执行，
--   改用 sync_all_data_only.sql 或先做好备份。
-- ============================================================

"""

HEAD_DATA = f"""-- ============================================================
-- 心情菜谱 · 本地库全量同步到线上（纯数据版）
-- 导出时间：{datetime.now():%Y-%m-%d %H:%M:%S}
-- 来源     ：本地 mood_recipe（21 张有数据的表）
-- 内容     ：仅 REPLACE INTO，不包含任何 DROP / CREATE
--
-- 【执行方式】文件内不含 USE 语句，需指定目标库：
--   mysql -u<用户> -p -D <线上库名> < sync_all_data_only.sql
--
-- 【适用前提】线上表结构必须已与本地一致（字段齐全）。
--   REPLACE 按主键覆盖，已存在的行会被整行替换；线上多出来
--   的行不会被删除。如果线上结构缺少本地新增的字段，会报错，
--   此时请改用 sync_all_full.sql。
-- ============================================================

"""

for fn, head in [("sync_all_full.sql", HEAD_FULL), ("sync_all_data_only.sql", HEAD_DATA)]:
    p = D / fn
    body = p.read_text(encoding="utf-8")
    if not body.startswith("-- ===="):
        p.write_text(head + body, encoding="utf-8")
    print(f"{fn} 已加说明头，当前 {p.stat().st_size / 1024:.1f} KB")
