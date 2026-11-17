-- V2__patient_menu.sql - the L2 「患者配置」 console group (patient-domain task 4.3,
-- design D5: the menu seed rides the PATIENT module's Flyway ledger and writes
-- CROSS-SCHEMA into system.menu_info/role_menu - the ruled V22/V32 CONTENT-guard
-- discipline: ids are data, perms/titles are the contract, every statement re-runs
-- as a row-count no-op once applied). Structure:
--   1. the L2 「患者配置」 group (type M, NULL perms, sort 3) under the L1 「设置」
--      row (content-addressed: up_id 0 + type M + title 设置, the V30 contract keys),
--      carrying the family-resident icon `team` (the console's MENU_ICON_BY_NAME
--      already maps team -> UserAccountIcon - zero frontend change)
--   2. the 号码规则 C page (path number-rule, component patient/number-rule/index,
--      perms patient:rule:list, sort 1) under the group
--   3. the three F buttons 新建/编辑/删除 (perms patient:rule:{create,update,delete},
--      sorts 1/2/3) under the page
--   4. grant carry-over: any role holding the page or a button receives the group
--      ancestor (the V32 step-2 shape - TreeUtils drops subtrees whose ancestor chain
--      is broken, defensive for future grants, a no-op on fresh seed)
--   5. the admin auto-grant (role 1, NOT-EXISTS, the V27/V28 shape) for the group +
--      page + buttons (the whole ancestor chain the console needs)
--   6. an icon-convergence UPDATE on the group row (the V36 shape): the guard is
--      IS DISTINCT FROM because icon may be NULL (plain `icon <>` evaluates unknown
--      on NULL and would silently skip) - a drifted or icon-less group converges to
--      team, an already-team row is a true no-op
--   7. the setval tails on system.menu_info + system.role_menu (V14 discipline)
-- PRODUCTION GRANT NOTE: the patient service's connection role needs
-- SELECT/INSERT/UPDATE/DELETE on system.menu_info + system.role_menu (+ their
-- sequences) for this migration to run cross-schema - the commented GRANT block in
-- docs/deployment/pg-init.sql documents it (documentation-only, decision 7).
-- Statement guards key only on NON-NULL columns (up_id/type/title/perms/deleted),
-- the NULL-anchor case (the 设置 row missing) is handled by the outer EXISTS guards
-- (the V32 shape), and the one nullable comparison (icon) uses IS DISTINCT FROM -
-- the recorded `= NULL`/`<>`-on-NULL footguns avoided by shape. Comment lines carry
-- NO semicolons (the re-execution test splits statements on semicolons - a
-- semicolon inside a comment truncates a statement mid-body). Every system table is
-- qualified EXPLICITLY (the patient ledger's default schema is patient), patient
-- tables are never qualified.

-- ============ 1) the L2 「患者配置」 group under the L1 「设置」 row ============

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0),
       'M', '患者配置', NULL, NULL, NULL, 'team', 3
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = (SELECT id FROM system.menu_info
                                    WHERE up_id = 0 AND type = 'M'
                                      AND title = '设置' AND deleted = 0)
                      AND type = 'M' AND title = '患者配置' AND deleted = 0);

-- ============ 2) the 号码规则 page under the group =============================

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'M' AND title = '患者配置' AND deleted = 0
           AND up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0)),
       'C', '号码规则', 'patient:rule:list', 'number-rule', 'patient/number-rule/index', NULL, 1
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '患者配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0))
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0
                      AND up_id = (SELECT id FROM system.menu_info
                                    WHERE type = 'M' AND title = '患者配置'
                                      AND deleted = 0
                                      AND up_id = (SELECT id FROM system.menu_info
                                                    WHERE up_id = 0 AND type = 'M'
                                                      AND title = '设置' AND deleted = 0)));

-- ============ 3) the three F buttons under the page ============================

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0),
       'F', '新建', 'patient:rule:create', NULL, NULL, NULL, 1
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'patient:rule:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0),
       'F', '编辑', 'patient:rule:update', NULL, NULL, NULL, 2
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'patient:rule:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0),
       'F', '删除', 'patient:rule:delete', NULL, NULL, NULL, 3
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'C' AND perms = 'patient:rule:list' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'patient:rule:delete' AND deleted = 0);

-- ============ 4) grant carry-over: page/button holders get the group ============

INSERT INTO system.role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id,
       (SELECT id FROM system.menu_info
         WHERE type = 'M' AND title = '患者配置' AND deleted = 0
           AND up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0))
  FROM system.role_menu rm
  JOIN system.menu_info m ON m.id = rm.menu_id
 WHERE m.type IN ('C', 'F')
   AND m.perms IN ('patient:rule:list', 'patient:rule:create',
                   'patient:rule:update', 'patient:rule:delete')
   AND m.deleted = 0 AND rm.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '患者配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0))
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2
                    WHERE rm2.role_id = rm.role_id
                      AND rm2.menu_id = (SELECT id FROM system.menu_info
                                          WHERE type = 'M' AND title = '患者配置'
                                            AND deleted = 0
                                            AND up_id = (SELECT id FROM system.menu_info
                                                          WHERE up_id = 0 AND type = 'M'
                                                            AND title = '设置'
                                                            AND deleted = 0))
                      AND rm2.deleted = 0);

-- ============ 5) the admin auto-grant (role 1) =================================

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.deleted = 0
   AND ((m.type = 'M' AND m.title = '患者配置'
         AND m.up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0))
        OR m.perms IN ('patient:rule:list', 'patient:rule:create',
                       'patient:rule:update', 'patient:rule:delete'))
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- ============ 6) the group icon convergence (V36 shape) ========================

UPDATE system.menu_info SET icon = 'team', update_time = CURRENT_TIMESTAMP
 WHERE type = 'M' AND title = '患者配置' AND deleted = 0
   AND up_id = (SELECT id FROM system.menu_info
                 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND icon IS DISTINCT FROM 'team';

-- ============ 7) the setval tails (V14 discipline) =============================

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
SELECT setval(pg_get_serial_sequence('system.role_menu', 'id'),
              (SELECT MAX(id) FROM system.role_menu));
