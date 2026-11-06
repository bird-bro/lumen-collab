-- V15__button_perm_nodes.sql - the button-level permission seed
-- (button-perm-enforcement-api, spec delta auth-rbac ADDED "Button-Level Permission
-- Enforcement"). 22 button (F) nodes under the console C pages, one per action
-- affordance, with the perms strings from the coordination inventory
-- (openspec/coordination/button-perm-enforcement.md - the single source of truth:
-- 用户 3 / 角色 4 / 菜单 3 / 机构 3 / 科室 3 / 字典 6). The admin role (role 1) is
-- auto-granted every node: lumen keeps the explicit role_menu model (no wildcard), so
-- an ungranted button would be invisible to the super administrator. Existing
-- non-admin roles keep their page grants but see no buttons until an operator grants
-- the F nodes - the intended tightening.
--
-- IDEMPOTENCE DISCIPLINE: CONTENT-guarded inserts, NOT explicit ids. The first draft
-- used explicit ids 40-61 + ON CONFLICT (id) DO NOTHING and silently inserted ZERO
-- rows on the shared test server: soft-deleted ut-probe rows from earlier test runs
-- occupied that id block, and ON CONFLICT yields to dead rows as readily as live ones
-- (flyway reported success with nothing to show). The seed therefore keys on CONTENT -
-- (up_id, type, perms) among LIVE rows - via NOT EXISTS: idempotent by content
-- (re-runs insert nothing), indifferent to dead-row history, and immune to future
-- id-block collisions. Sequence-driven ids need no setval. Proven by
-- AuthButtonSeedTests' re-execution test - the history row of the aborted explicit-id
-- draft was removed before this version was applied (never repair-and-diverge).

-- 1) Button nodes. type 'F' = leaf action node - path/component/icon stay NULL
--    (buttons navigate nothing - the perms string is their payload). Each insert is
--    guarded by NOT EXISTS among LIVE rows with the same (up_id, perms): the run is a
--    no-op once the node exists, whatever dead rows share its former id.
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 2, 'F', '新建用户', 'system:user:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 2, 'F', '重置密码', 'system:user:reset-pwd', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:reset-pwd' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 2, 'F', '启停', 'system:user:enable', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:enable' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 9, 'F', '新建角色', 'system:role:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 9 AND type = 'F' AND perms = 'system:role:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 9, 'F', '编辑角色', 'system:role:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 9 AND type = 'F' AND perms = 'system:role:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 9, 'F', '删除角色', 'system:role:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 9 AND type = 'F' AND perms = 'system:role:delete' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 9, 'F', '菜单授权', 'system:role:assign', 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 9 AND type = 'F' AND perms = 'system:role:assign' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 10, 'F', '新建节点', 'system:menu:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 10 AND type = 'F' AND perms = 'system:menu:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 10, 'F', '编辑节点', 'system:menu:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 10 AND type = 'F' AND perms = 'system:menu:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 10, 'F', '删除节点', 'system:menu:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 10 AND type = 'F' AND perms = 'system:menu:delete' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 7, 'F', '新建机构', 'system:org:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 7 AND type = 'F' AND perms = 'system:org:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 7, 'F', '编辑机构', 'system:org:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 7 AND type = 'F' AND perms = 'system:org:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 7, 'F', '删除机构', 'system:org:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 7 AND type = 'F' AND perms = 'system:org:delete' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 8, 'F', '新建科室', 'system:dep:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 8 AND type = 'F' AND perms = 'system:dep:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 8, 'F', '编辑科室', 'system:dep:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 8 AND type = 'F' AND perms = 'system:dep:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 8, 'F', '删除科室', 'system:dep:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 8 AND type = 'F' AND perms = 'system:dep:delete' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '新建目录', 'system:dict-catal:create', 1
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-catal:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '编辑目录', 'system:dict-catal:update', 2
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-catal:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '删除目录', 'system:dict-catal:delete', 3
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-catal:delete' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '新建明细', 'system:dict-detail:create', 4
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-detail:create' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '编辑明细', 'system:dict-detail:update', 5
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-detail:update' AND deleted = 0);
INSERT INTO system.menu_info (up_id, type, title, perms, sort)
SELECT 11, 'F', '删除明细', 'system:dict-detail:delete', 6
 WHERE NOT EXISTS (SELECT 1 FROM system.menu_info
                    WHERE up_id = 11 AND type = 'F' AND perms = 'system:dict-detail:delete' AND deleted = 0);

-- 2) Admin auto-grant (NOT-EXISTS live-binding guard - re-run safe - role 1 owns every
--    new node without a manual assignment step).
INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, m.id FROM system.menu_info m
 WHERE m.type = 'F'
   AND m.perms IN ('system:user:create', 'system:user:reset-pwd', 'system:user:enable',
                   'system:role:create', 'system:role:update', 'system:role:delete',
                   'system:role:assign', 'system:menu:create', 'system:menu:update',
                   'system:menu:delete', 'system:org:create', 'system:org:update',
                   'system:org:delete', 'system:dep:create', 'system:dep:update',
                   'system:dep:delete', 'system:dict-catal:create',
                   'system:dict-catal:update', 'system:dict-catal:delete',
                   'system:dict-detail:create', 'system:dict-detail:update',
                   'system:dict-detail:delete')
   AND NOT EXISTS (SELECT 1 FROM system.role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);
