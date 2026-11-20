-- V3__us_menu_seed.sql - the US workstation 登记 page seed (us-station-registration-sev
-- task 1.3, design D2: the menu seed rides the US module's Flyway ledger and writes
-- CROSS-SCHEMA into system.menu_info/role_menu - the ruled V22/V32 CONTENT-guard
-- discipline, the lumen-patient V2 precedent: ids are data, perms/titles are the
-- contract, every statement re-runs as a row-count no-op once applied). Structure:
--   1. the 登记 C page (path registration, component us/registration/index, perms
--      us:order:list, sort 1) under the EXISTING US workstation W node - anchored by
--      CONTENT ONLY (up_id 0 + type W + title 超声工作站, the V22 contract keys; the
--      node was seeded by lumen-system V22, icon converged by V36, ids never assumed)
--   2. the three F buttons 新建/登记/取消 (perms us:order:{create,update,cancel},
--      sorts 1/2/3) under the page
--   3. grant carry-over: any role holding the page or a button receives the W
--      ancestor (the V32 step-2 shape - TreeUtils drops subtrees whose ancestor chain
--      is broken, defensive for future grants, a no-op on fresh seed)
--   4. the admin auto-grant (role 1, NOT-EXISTS, the V22/V27 shape) for the page +
--      buttons + the W ancestor (the whole chain the console needs)
--   5. the setval tails on system.menu_info + system.role_menu (V14 discipline)
-- PRODUCTION GRANT NOTE: the us service's connection role needs SELECT/INSERT/UPDATE/
-- DELETE on system.menu_info + system.role_menu (+ their sequences) for this migration
-- to run cross-schema - the commented GRANT block in docs/deployment/pg-init.sql
-- documents the patient twin of it (documentation-only, decision 7).
-- Statement guards key only on NON-NULL columns (up_id/type/title/perms/deleted),
-- the NULL-anchor case (the W node missing) is handled by the outer EXISTS guards
-- (the V32 shape), and every system table is qualified EXPLICITLY (the us ledger's
-- default schema is us), us tables are never qualified. Comment lines carry NO
-- semicolons (the re-execution test splits statements on semicolons - a semicolon
-- inside a comment truncates a statement mid-body). The US workstation W node is a
-- REQUIRED anchor: if it is missing the EXISTS guards no-op the whole seed (a content
-- guard never invents the parent).

-- ============ 1) the 登记 C page under the US workstation W node ================

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0),
       'C', '登记', 'us:order:list', 'registration', 'us/registration/index', NULL, 1
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = (SELECT id FROM system.menu_info
                                    WHERE up_id = 0 AND type = 'W'
                                      AND title = '超声工作站' AND deleted = 0)
                      AND type = 'C' AND perms = 'us:order:list' AND deleted = 0);

-- ============ 2) the three F buttons under the page ============================

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0),
       'F', '新建', 'us:order:create', NULL, NULL, NULL, 1
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'us:order:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0),
       'F', '登记', 'us:order:update', NULL, NULL, NULL, 2
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'us:order:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0),
       'F', '取消', 'us:order:cancel', NULL, NULL, NULL, 3
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'us:order:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'us:order:cancel' AND deleted = 0);

-- ============ 3) grant carry-over: page/button holders get the W ancestor ========

INSERT INTO system.role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id,
       (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0)
  FROM system.role_menu rm
  JOIN system.menu_info m ON m.id = rm.menu_id
 WHERE m.type IN ('C', 'F')
   AND m.perms IN ('us:order:list', 'us:order:create',
                   'us:order:update', 'us:order:cancel')
   AND m.deleted = 0 AND rm.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2
                    WHERE rm2.role_id = rm.role_id
                      AND rm2.menu_id = (SELECT id FROM system.menu_info
                                          WHERE up_id = 0 AND type = 'W'
                                            AND title = '超声工作站' AND deleted = 0)
                      AND rm2.deleted = 0);

-- ============ 4) the admin auto-grant (role 1) =================================

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.deleted = 0
   AND ((m.type = 'W' AND m.title = '超声工作站' AND m.up_id = 0)
        OR m.perms IN ('us:order:list', 'us:order:create',
                       'us:order:update', 'us:order:cancel'))
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- ============ 5) the setval tails (V14 discipline) =============================

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
SELECT setval(pg_get_serial_sequence('system.role_menu', 'id'),
              (SELECT MAX(id) FROM system.role_menu));
