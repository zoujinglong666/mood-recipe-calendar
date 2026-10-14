import pathlib
f = pathlib.Path(r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\sql\init.sql')
c = f.read_text(encoding='utf-8')

# 1. recipes表加新字段
old = """  source       VARCHAR(16) DEFAULT 'LOCAL' COMMENT '菜谱来源：AI=锅仔智能体生成 LOCAL=初始化种子数据',
  created_at   DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜谱库';"""
new = """  source       VARCHAR(16) DEFAULT 'LOCAL' COMMENT '菜谱来源：AI=锅仔智能体生成 LOCAL=初始化种子数据',
  taste        VARCHAR(64) COMMENT '口味：清淡/辣/甜/酸/鲜',
  meal_type    VARCHAR(16) DEFAULT 'all' COMMENT '餐别：lunch/dinner/all',
  tags         VARCHAR(255) COMMENT '标签数组，逗号分隔',
  seasonings   VARCHAR(255) COMMENT '调料，逗号分隔',
  created_at   DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜谱库';

-- ---------- 用户成就 ----------
CREATE TABLE IF NOT EXISTS user_achievements (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid          VARCHAR(64) NOT NULL COMMENT '微信 openid',
  achievement_code VARCHAR(64) NOT NULL COMMENT '成就编码',
  unlocked_at     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
  UNIQUE KEY uk_openid_code (openid, achievement_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户成就';"""
c = c.replace(old, new)

f.write_text(c, encoding='utf-8')
print('init.sql updated')
