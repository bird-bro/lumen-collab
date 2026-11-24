-- V51__slot_config_menu.sql - the 预约设置 console page (拍板 06-A, us-registration-redesign-api
-- task 5.3, design D6): ONE additive C leaf under the EXISTING L2 「检查配置」 group +
-- the three slot F buttons + the admin auto-grant. NO directory rename - the
-- 检查设置→检查配置 rename ALREADY shipped with menu-settings-root (V30-V34,
-- 2026-11-13; the main spec is authoritative) - this migration is additive-only.
-- Every row is addressed BY CONTENT (the V32 contract keys: the L1 设置 row by
-- up_id 0 + type M + title 设置; the 检查配置 group by its title under that row;
-- the leaf/buttons keyed by (up_id, perms) among LIVE rows - the V15 discipline).
-- IDEMPOTENCE: every statement re-runs as a row-count no-op once applied (proven by
-- AuthSlotConfigMenuSeedTests). Comment lines carry NO semicolons.

-- ============ 1) the 预约设置 C leaf under the L2 「检查配置」 group =============

INSERT INTO system.menu_info (up_id, type, title, perms, path, component, icon, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'M' AND title = '检查配置' AND deleted = 0
           AND up_id = (SELECT id FROM system.menu_info
                         WHERE up_id = 0 AND type = 'M' AND title = '设置'
                           AND deleted = 0)),
       'C', '预约设置', 'setting:slot:list', 'exam-slot', 'setting/exam-slot/index', 'calendar', 4
 WHERE EXISTS (SELECT 1 FROM system.menu_info
                WHERE type = 'M' AND title = '检查配置' AND deleted = 0
                  AND up_id = (SELECT id FROM system.menu_info
                                WHERE up_id = 0 AND type = 'M' AND title = '设置'
                                  AND deleted = 0))
   AND NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'C' AND perms = 'setting:slot:list' AND deleted = 0);

-- ============ 2) the three slot F buttons (the V15 CONTENT-guard family) ========

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:slot:list' AND deleted = 0),
       'F', '新建', 'setting:slot:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:slot:create' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:slot:list' AND deleted = 0),
       'F', '编辑', 'setting:slot:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:slot:update' AND deleted = 0);

INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT (SELECT id FROM system.menu_info
         WHERE type = 'C' AND perms = 'setting:slot:list' AND deleted = 0),
       'F', '删除', 'setting:slot:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE type = 'F' AND perms = 'setting:slot:delete' AND deleted = 0);

-- ============ 3) the admin auto-grant (the V27/V28 NOT-EXISTS shape) ============

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id
  FROM system.menu_info m
 WHERE m.perms IN ('setting:slot:list', 'setting:slot:create',
                   'setting:slot:update', 'setting:slot:delete')
   AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);
