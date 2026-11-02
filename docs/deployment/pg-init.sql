-- LumenCollab 数据库初始化（DBA 交付物）
-- 模型：单库 `lumen` + 每服务一个 schema + 每服务一个连接角色（GRANT 隔离，跨服务查询在库层被拒）
-- 执行者：院方 DBA 或实施工程师（一次性）；幂等可重复执行。
-- 对应规格：openspec/specs/data/spec.md § Schema-per-Service Isolation（auth-rbac-foundation 起）
SELECT 'CREATE DATABASE lumen' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'lumen')\gexec
\connect lumen

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS patient;
CREATE SCHEMA IF NOT EXISTS radiology;
CREATE SCHEMA IF NOT EXISTS us;
CREATE SCHEMA IF NOT EXISTS endo;
CREATE SCHEMA IF NOT EXISTS integration;

-- 每服务连接角色（口令由 DBA 设定；服务连接串只配自己的角色 → 跨 schema 不可见）
-- DO $$ BEGIN
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'lumen_auth_role') THEN
--     CREATE ROLE lumen_auth_role LOGIN PASSWORD '<DBA-SET>';
--   END IF;
-- END $$;
-- GRANT USAGE ON SCHEMA auth TO lumen_auth_role;
-- GRANT ALL ON ALL TABLES IN SCHEMA auth TO lumen_auth_role;
-- GRANT ALL ON ALL SEQUENCES IN SCHEMA auth TO lumen_auth_role;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON TABLES TO lumen_auth_role;
