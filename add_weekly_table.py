# -*- coding: utf-8 -*-
f = open(r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\init.sql", "r", encoding="utf-8")
content = f.read()
f.close()

# 检查是否已经有 weekly_meal_plans 表
if "weekly_meal_plans" in content:
    print("weekly_meal_plans 表已存在，无需添加")
else:
    # 在 user_food_preferences 表结束后插入
    marker = ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户口味偏好';"
    insert_sql = """

-- ---------- 每周备餐计划 ----------
CREATE TABLE IF NOT EXISTS weekly_meal_plans (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid        VARCHAR(64) NOT NULL COMMENT '微信 openid',
  plan_json     TEXT NOT NULL COMMENT '一周菜单计划JSON',
  shopping_json TEXT NOT NULL COMMENT '买菜清单JSON',
  created_at    DATETIME,
  updated_at    DATETIME,
  INDEX idx_weekly_plan_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='每周备餐计划';
"""
    
    if marker in content:
        content = content.replace(marker, marker + insert_sql, 1)
        f = open(r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\init.sql", "w", encoding="utf-8")
        f.write(content)
        f.close()
        print("weekly_meal_plans 表已添加到 init.sql")
    else:
        print("未找到插入位置标记")

# 验证
f = open(r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\init.sql", "r", encoding="utf-8")
content = f.read()
f.close()
print("包含 weekly_meal_plans:", "weekly_meal_plans" in content)
print("括号匹配:", content.count("(") == content.count(")"))
