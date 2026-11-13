-- V32__menu_settings_groups.sql - the L1 「设置」 console gains two L2 directories
-- (user ruling 2026-11-13, menu-settings-root task 1.4): the four C pages re-group
-- under 检查配置 (icon medicine-box, the catalog trio) and 报告配置 (icon file-text,
-- the template page). Both groups are type M with NULL perms (the V28 group shape).
-- ids are data and every row is addressed BY CONTENT (the L1 设置 row by up_id 0 +
-- type M + title 设置, the V30 contract keys - each group by its title under that
-- row). The web console maps medicine-box -> MedicineBoxOutlined and file-text ->
-- FileTextOutlined, so the icon keys ship with zero frontend route/logic change.
-- Grant chain: every role holding a re-grouped page receives its new L2 parent
-- (the V30 carry-over shape - TreeUtils drops subtrees whose ancestor chain is
-- broken), then the admin auto-grant (NOT-EXISTS, the V27/V28 shape) and the
-- setval tails. V31 stays frozen (never rewrite an applied migration).
-- IDEMPOTENCE: every statement re-runs as a row-count no-op once applied. Comment
-- lines carry NO semicolons (the re-execution test splits statements on
-- semicolons - a semicolon inside a comment truncates a statement mid-body).
-- CONVERGENCE: a V30 re-run re-parents the pages flat under the L1 row (step 5)
-- and this migration re-groups them again - the V28→V32 tail converges under both
-- re-run orders (menu-settings-root design D2 discipline). Proven by
-- AuthExamSettingSeedTests.

-- ============ 1) the two L2 directories under the L1 「设置」 row ==============

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0),
       'M', '检查配置', NULL, NULL, NULL, 'medicine-box', 1
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = (SELECT id FROM system.menu_info
                                    WHERE up_id = 0 AND type = 'M'
                                      AND title = '设置' AND deleted = 0)
                      AND type = 'M' AND title = '检查配置' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0),
       'M', '报告配置', NULL, NULL, NULL, 'file-text', 2
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = (SELECT id FROM system.menu_info
                                    WHERE up_id = 0 AND type = 'M'
                                      AND title = '设置' AND deleted = 0)
                      AND type = 'M' AND title = '报告配置' AND deleted = 0);

-- ============ 2) grant carry-over to the new rows =============================

INSERT INTO system.role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id,
       (SELECT id FROM system.menu_info
         WHERE type = 'M' AND title = '检查配置' AND deleted = 0
           AND up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0))
  FROM system.role_menu rm
  JOIN system.menu_info m ON m.id = rm.menu_id
 WHERE m.type = 'C'
   AND m.perms IN ('setting:item:list', 'setting:body:list', 'setting:method:list')
   AND m.deleted = 0 AND rm.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '检查配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0))
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2
                    WHERE rm2.role_id = rm.role_id
                      AND rm2.menu_id = (SELECT id FROM system.menu_info
                                          WHERE type = 'M' AND title = '检查配置'
                                            AND deleted = 0
                                            AND up_id = (SELECT id FROM system.menu_info
                                                          WHERE up_id = 0 AND type = 'M'
                                                            AND title = '设置'
                                                            AND deleted = 0))
                      AND rm2.deleted = 0);

INSERT INTO system.role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id,
       (SELECT id FROM system.menu_info
         WHERE type = 'M' AND title = '报告配置' AND deleted = 0
           AND up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0))
  FROM system.role_menu rm
  JOIN system.menu_info m ON m.id = rm.menu_id
 WHERE m.type = 'C' AND m.perms = 'setting:temp:list'
   AND m.deleted = 0 AND rm.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '报告配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0))
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2
                    WHERE rm2.role_id = rm.role_id
                      AND rm2.menu_id = (SELECT id FROM system.menu_info
                                          WHERE type = 'M' AND title = '报告配置'
                                            AND deleted = 0
                                            AND up_id = (SELECT id FROM system.menu_info
                                                          WHERE up_id = 0 AND type = 'M'
                                                            AND title = '设置'
                                                            AND deleted = 0))
                      AND rm2.deleted = 0);

-- ============ 3) the four pages re-parent BY PERMS ============================

UPDATE system.menu_info c
   SET up_id = (SELECT id FROM system.menu_info
                 WHERE type = 'M' AND title = '检查配置' AND deleted = 0
                   AND up_id = (SELECT id FROM system.menu_info
                                 WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                   AND deleted = 0)),
       update_time = CURRENT_TIMESTAMP
 WHERE c.type = 'C'
   AND c.perms IN ('setting:item:list', 'setting:body:list', 'setting:method:list')
   AND c.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '检查配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0));

UPDATE system.menu_info c
   SET up_id = (SELECT id FROM system.menu_info
                 WHERE type = 'M' AND title = '报告配置' AND deleted = 0
                   AND up_id = (SELECT id FROM system.menu_info
                                 WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                   AND deleted = 0)),
       update_time = CURRENT_TIMESTAMP
 WHERE c.type = 'C' AND c.perms = 'setting:temp:list'
   AND c.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '报告配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0));

-- ============ 4) the admin auto-grant (role 1) ================================

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.type = 'M' AND m.title IN ('检查配置', '报告配置')
   AND m.up_id = (SELECT id FROM system.menu_info
                   WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- ============ 5) the setval tails (V14 discipline) ============================

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
SELECT setval(pg_get_serial_sequence('system.role_menu', 'id'),
              (SELECT MAX(id) FROM system.role_menu));
