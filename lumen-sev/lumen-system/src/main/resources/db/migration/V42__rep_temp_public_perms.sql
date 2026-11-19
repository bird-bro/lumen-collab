-- V42__rep_temp_public_perms.sql - the report-template PUBLIC-scope button perms
-- (rep-temp-scope-perms task 1.1 - spec delta system-data "Report Template Scope
-- Button Perms" - numbered V42 because V41 was taken mid-task by the parallel
-- number-rule session's menu-component fix). Four F nodes land under the 报告模板
-- C page (resolved BY PERMS - the V28 discipline, NO fixed ids): the scope-keyed
-- public family
-- setting:temp:public:{list,create,update,delete} at sorts 4-7, beside the page's
-- existing 新建/编辑/删除 buttons (sorts 1-3). public:list is a bindable button bit
-- too (design D3) - the console Segmented 公共/个人 switch needs it as a grantable
-- perm, not just the class gate.
--
-- Sections, all idempotent under re-run (the V28 shape):
--   1. The four F nodes - CONTENT-guarded serial inserts, NOT EXISTS among LIVE rows
--      by perms.
--   2. The admin auto-grant (role 1 = the seeded 系统管理员), CONTENT-guarded per row
--      via role_menu NOT EXISTS. Business roles (科室主任) bind the nodes at
--      deployment time through role authorization - no business-role seed ships.
--   3. The setval tail (V14 discipline) - the sequence advances past the new rows.
--
-- Comment lines carry NO semicolons (the re-execution proof splits statements on
-- semicolons - a semicolon inside a comment truncates a statement mid-body).

-- ================= 1) the four public-family F nodes (spec system-data) =========

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '公共模板可见', 'setting:temp:public:list', 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:public:list'
                      AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '新建公共模板', 'setting:temp:public:create', 5
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:public:create'
                      AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '编辑公共模板', 'setting:temp:public:update', 6
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:public:update'
                      AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:temp:list' AND deleted = 0),
       'F', '删除公共模板', 'setting:temp:public:delete', 7
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:temp:public:delete'
                      AND deleted = 0);

-- ================= 2) the admin auto-grant (role 1, the V27 shape) ==============

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.perms IN ('setting:temp:public:list', 'setting:temp:public:create',
                   'setting:temp:public:update', 'setting:temp:public:delete')
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- ================= 3) the setval tail (V14 discipline) ==========================

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
