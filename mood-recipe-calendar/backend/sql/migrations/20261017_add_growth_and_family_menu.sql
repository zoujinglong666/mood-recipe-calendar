-- P1 增长引擎：锅仔养成（成长值状态 + 流水）与家庭菜单分工

CREATE TABLE IF NOT EXISTS growth_states (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64) NOT NULL,
  points      INT         NOT NULL DEFAULT 0 COMMENT '累计成长值',
  level       INT         NOT NULL DEFAULT 1 COMMENT '当前等级缓存（1 起）',
  updated_at  DATETIME    NOT NULL,
  UNIQUE KEY uk_growth_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锅仔养成状态';

CREATE TABLE IF NOT EXISTS growth_events (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid      VARCHAR(64) NOT NULL,
  type        VARCHAR(32) NOT NULL COMMENT 'RECORD_MEAL/CHECKIN/WEEKLY_PLAN/EXPIRING_CONSUMED/ACHIEVEMENT',
  points      INT         NOT NULL,
  created_at  DATETIME    NOT NULL,
  KEY idx_growth_event_openid_time (openid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锅仔养成成长值流水';

CREATE TABLE IF NOT EXISTS family_menu_assignments (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id     BIGINT      NOT NULL COMMENT '家庭组',
  plan_id      BIGINT      NOT NULL COMMENT '周计划 id（分工挂在计划上）',
  owner_openid VARCHAR(64) NOT NULL COMMENT '计划归属者',
  day_index    INT         NOT NULL COMMENT '第几天的做饭日（0-6）',
  cook_openid  VARCHAR(64) COMMENT '掌勺人 openid；NULL=未定',
  updated_at   DATETIME    NOT NULL,
  UNIQUE KEY uk_family_menu_plan_day (plan_id, day_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭菜单分工（谁掌勺）';
