-- ============================================================
-- 7 日留存基线脚本（v2 口径 · 幂等，可反复执行）
--
-- 用法：
--   mysql -uroot -p --default-character-set=utf8mb4 mood_recipe < sql/retention-baseline.sql
--
-- 口径定义：
--   cohort_day = users.first_use_date（首访日）
--   day K 留存 = 首访日之后第 K 个自然日（本地时区），该用户在
--                user_records 有 >= 1 条记录
--   分母       = 该 cohort 的全部用户（含从未记录过的用户）
--
-- 输出：
--   第一段：每日 cohort 明细（new_users / d0..d7 / d7%）
--   第二段：全部 cohort 汇总的 d1..d7 总体留存
--   第三段：用户级打点明细（active_offsets 一眼看出断在第几天）
-- ============================================================
USE mood_recipe;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---------- 0) 回填 first_use_date（只处理 NULL，幂等） ----------
-- 优先级：最早记录日 > created_at > 今天
UPDATE users u
LEFT JOIN (
  SELECT openid, MIN(DATE(record_date)) AS first_record
  FROM user_records
  WHERE record_date IS NOT NULL AND record_date <> ''
  GROUP BY openid
) fr ON fr.openid = u.openid
SET u.first_use_date = COALESCE(fr.first_record, DATE(u.created_at), CURDATE())
WHERE u.first_use_date IS NULL;

-- ---------- 1) 每日 cohort 明细 ----------
WITH offsets AS (
  SELECT r.openid,
         u.first_use_date,
         DATEDIFF(DATE(r.record_date), u.first_use_date) AS day_offset
  FROM user_records r
  JOIN users u ON u.openid = r.openid
  WHERE u.first_use_date IS NOT NULL
    AND r.record_date IS NOT NULL AND r.record_date <> ''
),
flags AS (
  SELECT openid,
         first_use_date,
         MAX(day_offset = 0) AS d0,
         MAX(day_offset = 1) AS d1,
         MAX(day_offset = 2) AS d2,
         MAX(day_offset = 3) AS d3,
         MAX(day_offset = 4) AS d4,
         MAX(day_offset = 5) AS d5,
         MAX(day_offset = 6) AS d6,
         MAX(day_offset = 7) AS d7
  FROM offsets
  GROUP BY openid, first_use_date
)
SELECT u.first_use_date                                   AS cohort_day,
       COUNT(u.id)                                        AS new_users,
       COALESCE(SUM(f.d0), 0)                             AS d0,
       COALESCE(SUM(f.d1), 0)                             AS d1,
       COALESCE(SUM(f.d2), 0)                             AS d2,
       COALESCE(SUM(f.d3), 0)                             AS d3,
       COALESCE(SUM(f.d4), 0)                             AS d4,
       COALESCE(SUM(f.d5), 0)                             AS d5,
       COALESCE(SUM(f.d6), 0)                             AS d6,
       COALESCE(SUM(f.d7), 0)                             AS d7,
       ROUND(100 * COALESCE(SUM(f.d7), 0) / COUNT(u.id), 1) AS d7_pct
FROM users u
LEFT JOIN flags f ON f.openid = u.openid
WHERE u.first_use_date IS NOT NULL
GROUP BY u.first_use_date
ORDER BY u.first_use_date;

-- ---------- 2) 全部 cohort 汇总 ----------
WITH offsets AS (
  SELECT r.openid,
         DATEDIFF(DATE(r.record_date), u.first_use_date) AS day_offset
  FROM user_records r
  JOIN users u ON u.openid = r.openid
  WHERE u.first_use_date IS NOT NULL
    AND r.record_date IS NOT NULL AND r.record_date <> ''
),
flags AS (
  SELECT openid,
         MAX(day_offset = 1) AS d1,
         MAX(day_offset = 2) AS d2,
         MAX(day_offset = 3) AS d3,
         MAX(day_offset = 5) AS d5,
         MAX(day_offset = 7) AS d7
  FROM offsets
  GROUP BY openid
)
SELECT COUNT(*)                                          AS total_users,
       COALESCE(SUM(f.d1), 0)                            AS d1_users,
       ROUND(100 * COALESCE(SUM(f.d1), 0) / COUNT(*), 1) AS d1_pct,
       COALESCE(SUM(f.d3), 0)                            AS d3_users,
       ROUND(100 * COALESCE(SUM(f.d3), 0) / COUNT(*), 1) AS d3_pct,
       COALESCE(SUM(f.d7), 0)                            AS d7_users,
       ROUND(100 * COALESCE(SUM(f.d7), 0) / COUNT(*), 1) AS d7_pct
FROM users u
LEFT JOIN flags f ON f.openid = u.openid;

-- ---------- 3) 用户级打点明细 ----------
-- active_offsets：该用户相对首访日的活跃日偏移，如 "0,1,2,5" = 第 3、4 天断档
SELECT u.openid,
       u.first_use_date,
       COUNT(DISTINCT DATE(r.record_date))                AS record_days,
       MIN(DATE(r.record_date))                           AS first_record,
       MAX(DATE(r.record_date))                           AS last_record,
       GROUP_CONCAT(DISTINCT DATEDIFF(DATE(r.record_date), u.first_use_date)
                    ORDER BY DATEDIFF(DATE(r.record_date), u.first_use_date)) AS active_offsets
FROM users u
LEFT JOIN user_records r
       ON r.openid = u.openid AND r.record_date IS NOT NULL AND r.record_date <> ''
GROUP BY u.openid, u.first_use_date
ORDER BY u.first_use_date, u.openid;
