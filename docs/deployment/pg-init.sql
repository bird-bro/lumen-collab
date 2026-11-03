-- LumenCollab 数据库初始化（DBA 交付物）
-- 模型：单库 `lumen` + 每服务一个 schema + 每服务一个连接角色（GRANT 隔离，跨服务查询在库层被拒）
-- 执行者：院方 DBA 或实施工程师（一次性）；幂等可重复执行。
-- 对应规格：openspec/specs/data/spec.md § Schema-per-Service Isolation（auth-rbac-foundation 起）
SELECT 'CREATE DATABASE lumen' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'lumen')\gexec
\connect lumen

CREATE SCHEMA IF NOT EXISTS system;
CREATE SCHEMA IF NOT EXISTS patient;
CREATE SCHEMA IF NOT EXISTS radiology;
CREATE SCHEMA IF NOT EXISTS us;
CREATE SCHEMA IF NOT EXISTS endo;
CREATE SCHEMA IF NOT EXISTS integration;

-- 每服务连接角色（口令由 DBA 设定；服务连接串只配自己的角色 → 跨 schema 不可见）
-- DO $$ BEGIN
--   IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'lumen_system_role') THEN
--     CREATE ROLE lumen_system_role LOGIN PASSWORD '<DBA-SET>';
--   END IF;
-- END $$;
-- GRANT USAGE ON SCHEMA system TO lumen_system_role;
-- GRANT ALL ON ALL TABLES IN SCHEMA system TO lumen_system_role;
-- GRANT ALL ON ALL SEQUENCES IN SCHEMA system TO lumen_system_role;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA system GRANT ALL ON TABLES TO lumen_system_role;

-- =====================================================================
-- 六、public 共享系统表授权（system-data，2026-11-03）
-- =====================================================================
-- 平台共享系统表落在 public schema（四表由 lumen-auth 的 Flyway V3__system_data.sql
-- 一次性创建，其余服务 Flyway 不携带）：字典 dict_catal / dict_detail + 日志
-- log_info / log_sign。六个服务角色均可读写（D1：各服务连接串同构
-- currentSchema=<own>,public）；各服务私有 schema 的表仍互不可见（隔离不受影响）。
-- 注意：GRANT 生效前角色须先存在（见上文角色块）；PG15+ 访问 public 下的表还需
-- 对该 schema 授 USAGE：
-- GRANT USAGE ON SCHEMA public
--   TO lumen_system_role, lumen_patient_role, lumen_radiology_role,
--      lumen_us_role, lumen_endo_role, lumen_integration_role;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON
--   public.dict_catal, public.dict_detail, public.log_info, public.log_sign
--   TO lumen_system_role, lumen_patient_role, lumen_radiology_role,
--      lumen_us_role, lumen_endo_role, lumen_integration_role;
