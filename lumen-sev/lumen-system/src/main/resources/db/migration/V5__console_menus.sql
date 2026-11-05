-- V5__console_menus.sql - console menu sync + mock/test-era residue cleanup.
--
-- The backend follow-up recorded by web-foundation-auth-console (design D6 and
-- the batch-5b console-menu decision, the deleted mock fixture SEED_MENUS rev.7):
-- the console sidebar is the user-confirmed 3-level structure - 系统管理 gains
-- three group directories (组织架构 / 用户权限 / 系统配置) and the six remaining
-- console pages hang off them; 用户管理 re-parents under 用户权限. The frontend
-- (`router.tsx` + `src/routes/menu-paths.ts`) registers /system/{orgs,deps,users,
-- roles,menus,dicts,logs} and resolves each leaf through its `component` column.
-- This migration also fixes the two department paths the real router renders
-- (V2's /us /endo were seed-time placeholders; router.tsx registers
-- /ultrasound /endoscopy) and purges the shared test server's probe residue
-- (b2*/ut.* rows from the auth/data-layer round-trip tests) that displayed on
-- the console pages.
--
-- IDEMPOTENCE DISCIPLINE (deliberate deviation from the V2/V3 plain-INSERT
-- style): this migration was applied to the live shared test database by hand
-- (psql) BEFORE the next lumen-system boot, so every statement is guarded and a
-- Flyway re-run is a no-op - inserts use ON CONFLICT DO NOTHING, updates carry
-- WHERE guards, and the cleanup patterns match only probe naming (no-ops on a
-- fresh database, where the inserts below are first-time inserts).

-- 1) Group directories under 系统管理 (menu 1): path NULL - a group never
--    navigates; BasicLayout renders type 'M' children as group titles.
INSERT INTO system.menu_info (id, up_id, type, title, perms, path, component, icon, sort) VALUES
    (13, 1, 'M', '组织架构', NULL, NULL, NULL, 'bank',    1),
    (14, 1, 'M', '用户权限', NULL, NULL, NULL, 'safety',  2),
    (15, 1, 'M', '系统配置', NULL, NULL, NULL, 'sliders', 3)
ON CONFLICT (id) DO NOTHING;

-- 2) Console pages (perms = the frontend pages' documented gating strings,
--    matching the deleted mock fixture verbatim).
INSERT INTO system.menu_info (id, up_id, type, title, perms, path, component, icon, sort) VALUES
    (7,  13, 'C', '机构管理', 'system:org:list',  'org',  'system/org/index',  NULL, 1),
    (8,  13, 'C', '科室管理', 'system:dep:list',  'dep',  'system/dep/index',  NULL, 2),
    (9,  14, 'C', '角色管理', 'system:role:list', 'role', 'system/role/index', NULL, 1),
    (10, 14, 'C', '菜单管理', 'system:menu:list', 'menu', 'system/menu/index', NULL, 2),
    (11, 15, 'C', '字典管理', 'system:dict:list', 'dict', 'system/dict/index', NULL, 1),
    (12, 15, 'C', '日志查询', 'system:log:list',  'log',  'system/log/index',  NULL, 2)
ON CONFLICT (id) DO NOTHING;

-- 3) 用户管理 re-parents under 用户权限 (the confirmed structure; its sort 1,
--    path and component stay as seeded).
UPDATE system.menu_info SET up_id = 14, update_time = CURRENT_TIMESTAMP
 WHERE id = 2 AND up_id <> 14;

-- 4) Department paths follow the registered routes (an absolute path navigates
--    directly); components follow the same router alignment (display-only for
--    department leaves).
UPDATE system.menu_info SET path = '/ultrasound', component = 'ultrasound/index', update_time = CURRENT_TIMESTAMP
 WHERE id = 4 AND (path <> '/ultrasound' OR component <> 'ultrasound/index');
UPDATE system.menu_info SET path = '/endoscopy', component = 'endoscopy/index', update_time = CURRENT_TIMESTAMP
 WHERE id = 5 AND (path <> '/endoscopy' OR component <> 'endoscopy/index');

-- 5) The administrator role owns the console additions (no explicit ids here:
--    the shared test DB holds deleted probe rows past role_menu id 6, so the
--    serial advances naturally).
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.id BETWEEN 7 AND 15
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- 6) Advance menu_info's sequence past the explicit group/page ids (V2
--    discipline - without this the first application-side insert would collide
--    with the seeded ids).
SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));

-- 7) Mock/test-era residue purge: probe rows created by the API round-trip test
--    runs on the shared test server (2026-11-03..05). Guarded patterns match
--    only the probe naming, so they are no-ops on a fresh database. Real usage
--    logs (admin operations) are untouched.
UPDATE system.user_info SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE deleted = 0 AND username ~ '^b2(sign|out)_[0-9]+$';
UPDATE system.user_role SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE deleted = 0 AND user_id IN (SELECT id FROM system.user_info
                                    WHERE username ~ '^b2(sign|out)_[0-9]+$');
UPDATE system.org_info SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE deleted = 0 AND name ~ '^(日志机构|UA探针机构)_[0-9]{13}$';
UPDATE public.dict_catal SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE deleted = 0 AND (code LIKE 'ut.dictcatal.%' OR code LIKE 'b2.%');
UPDATE public.log_sign SET deleted = 1
 WHERE deleted = 0 AND account ~ '^b2(sign|out)_[0-9]+$';
UPDATE public.log_info SET deleted = 1
 WHERE deleted = 0 AND account ~ '^b2(sign|out)_[0-9]+$';
