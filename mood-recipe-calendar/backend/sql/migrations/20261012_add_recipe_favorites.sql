-- 已部署数据库执行一次；新环境使用 sql/init.sql 即可。
CREATE TABLE IF NOT EXISTS recipe_favorites (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid     VARCHAR(64) NOT NULL COMMENT '微信 openid',
  recipe_id  BIGINT NOT NULL COMMENT '菜谱ID',
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_recipe_favorite_user_recipe (openid, recipe_id),
  INDEX idx_recipe_favorite_user_time (openid, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户想吃清单（菜谱收藏）';
