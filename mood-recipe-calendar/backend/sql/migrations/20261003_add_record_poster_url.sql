-- 会员服务端海报缓存地址；JPA ddl-auto=update 也会自动补列，迁移脚本供生产显式执行。
ALTER TABLE user_records ADD COLUMN poster_url TEXT NULL;
