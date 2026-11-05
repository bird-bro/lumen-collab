-- V8__menu_security_group.sql - console menu IA adjustment (user ruling 2026-11-05).
--
-- Two moves on the confirmed console tree (V5 seeded the 3-level structure):
--   1. 菜单管理 (menu 10) re-parents from 用户权限 (14) under 系统配置 (15),
--      joining 字典管理 - menu CRUD is a configuration concern.
--   2. 日志查询 (menu 12) leaves 系统配置 and hangs under a NEW level-2 group
--      安全 (16, icon lock, sort 4) - security-audit pages get their own group
--      (the OPS reference nests 审计日志/登录日志/API凭证 under the same concept;
--      future security pages land here).
--
-- Final 系统管理 subtree: 组织架构(机构/科室) · 用户权限(用户/角色) ·
-- 系统配置(字典/菜单) · 安全(日志).
--
-- IDEMPOTENCE: same guarded discipline as V5 - inserts ON CONFLICT DO NOTHING,
-- updates carry WHERE guards, so the hand-applied hot run and a later Flyway
-- re-run both converge.

INSERT INTO system.menu_info (id, up_id, type, title, perms, path, component, icon, sort) VALUES
    (16, 1, 'M', '安全', NULL, NULL, NULL, 'lock', 4)
ON CONFLICT (id) DO NOTHING;

UPDATE system.menu_info SET up_id = 15, update_time = CURRENT_TIMESTAMP
 WHERE id = 10 AND up_id <> 15;

UPDATE system.menu_info SET up_id = 16, update_time = CURRENT_TIMESTAMP
 WHERE id = 12 AND up_id <> 16;

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, 16 WHERE NOT EXISTS
    (SELECT 1 FROM system.role_menu WHERE role_id = 1 AND menu_id = 16 AND deleted = 0);

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
