-- V30__menu_settings_root.sql - the menu IA restructure (menu-settings-root, user
-- ruling 2026-11-13): the exam-setting console group becomes a top-level 「设置」
-- directory beside the system root, and 系统管理 renames to 系统. V28 stays frozen
-- (never rewrite an applied migration) - this migration is the forward fix, shaped
-- so the V28→V30 tail re-runs converge (design D2):
--   1. Rename the system root (pinned id 1, the V2 seed) - content-guarded.
--   2. Insert the L1 「设置」 directory (content-guarded serial insert, the V28
--      discipline - NO explicit id: the perms/title are the contract), icon kept
--      from the V28 group, sort 2 (between 系统 sort 1 and the workstation W nodes
--      sort 10+).
--   3. Carry every live role_menu binding of the old L2 row over to the new row -
--      without it a non-admin role holding the group loses the ancestor chain and
--      TreeUtils drops the whole subtree from its tree (orphan rule).
--   4. Soft-clean the old bindings and soft-delete the L2 「检查设置」 row (the
--      MenuService.delete cascade shape in raw SQL). A V28 re-run re-inserts a
--      fresh L2 dup (its guard keys on the moved row) and the V30 re-run deletes it
--      again - live state converges under BOTH re-run orders.
--   5. Re-parent the four C pages BY PERMS (the V28 contract strings, never serial
--      ids). The twelve F nodes hang off the page ids and follow untouched.
--   6. The admin auto-grant (role 1) - redundant after a carry-over, the NOT-EXISTS
--      guard makes it free (V27/V28 shape).
--   7. The setval tails (V14 discipline).
--
-- IDEMPOTENCE: every statement re-runs as a row-count no-op once applied. Comment
-- lines carry NO semicolons (the re-execution test splits statements on semicolons
-- - a semicolon inside a comment truncates a statement mid-body). Proven by
-- AuthExamSettingSeedTests.

-- ================= 1) the system root renames (menu 1, the V2 seed) =============
UPDATE system.menu_info SET title = '系统', update_time = CURRENT_TIMESTAMP
 WHERE id = 1 AND type = 'M' AND title = '系统管理' AND deleted = 0;

-- ================= 2) the L1 「设置」 directory ==================================
INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT 0, 'M', '设置', NULL, NULL, NULL, 'medicine-box', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 0 AND type = 'M' AND title = '设置'
                      AND deleted = 0);

-- ================= 3) grant carry-over to the new row ===========================
INSERT INTO system.role_menu (role_id, menu_id)
SELECT rm.role_id,
       (SELECT id FROM system.menu_info
         WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0)
  FROM system.role_menu rm
  JOIN system.menu_info m ON m.id = rm.menu_id
 WHERE m.type = 'M' AND m.title = '检查设置' AND m.deleted = 0 AND rm.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm2
                    WHERE rm2.role_id = rm.role_id
                      AND rm2.menu_id = (SELECT id FROM system.menu_info
                                          WHERE up_id = 0 AND type = 'M'
                                            AND title = '设置' AND deleted = 0)
                      AND rm2.deleted = 0);

-- ================= 4) soft-clean the old bindings, soft-delete the old row ======
UPDATE system.role_menu rm SET deleted = 1, update_time = CURRENT_TIMESTAMP
 FROM system.menu_info m
 WHERE rm.menu_id = m.id AND m.type = 'M' AND m.title = '检查设置' AND m.deleted = 0
   AND rm.deleted = 0;

UPDATE system.menu_info SET deleted = 1, update_time = CURRENT_TIMESTAMP
 WHERE type = 'M' AND title = '检查设置' AND deleted = 0;

-- ================= 5) the four pages re-parent BY PERMS =========================
UPDATE system.menu_info c
   SET up_id = (SELECT id FROM system.menu_info
                 WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0),
       update_time = CURRENT_TIMESTAMP
 WHERE c.type = 'C'
   AND c.perms IN ('setting:item:list', 'setting:body:list',
                   'setting:method:list', 'setting:temp:list')
   AND c.deleted = 0
   AND EXISTS (SELECT 1 FROM system.menu_info
                WHERE up_id = 0 AND type = 'M' AND title = '设置' AND deleted = 0);

-- ================= 6) the admin auto-grant (role 1) =============================
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.type = 'M' AND m.title = '设置' AND m.up_id = 0 AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);

-- ================= 7) the setval tails ==========================================
SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
SELECT setval(pg_get_serial_sequence('system.role_menu', 'id'),
              (SELECT MAX(id) FROM system.role_menu));
