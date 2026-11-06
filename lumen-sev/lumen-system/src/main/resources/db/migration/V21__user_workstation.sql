-- V19__user_workstation.sql - the workstation layer seed (workstation-context-api).
-- Three parts, all idempotent (the V15 content-guard discipline: NOT EXISTS among
-- live rows, no explicit ids, re-run is a row-count no-op - proven by
-- AuthWorkstationSeedTests): the grant table, the L1 station markers, and the
-- assign-ws button permission.
--
-- Station semantics (user rulings 2026-11-06): the workstation enum is
-- admin/radiology/us/endo (closed product enum) - menu_info.workstation is set ONLY
-- on L1 menus (系统管理=admin, plus 放射科/超声科/内镜科 placeholder L1s) - a child's
-- station = its ancestor L1's value (the tree is the mapping) - zero-workstation
-- users fall back to ["admin"] in the payload assembler (no backfill).

-- 1) The grant table. Partial unique index: one live is_default=1 row per user.
CREATE TABLE IF NOT EXISTS user_workstation (
    id            serial PRIMARY KEY,
    user_id       integer NOT NULL REFERENCES system.user_info (id),
    workstation   varchar(16) NOT NULL,
    is_default    integer NOT NULL DEFAULT 0,
    deleted       integer NOT NULL DEFAULT 0,
    create_by     integer,
    create_time   timestamp,
    update_by     integer,
    update_time   timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_workstation_default
    ON user_workstation (user_id) WHERE is_default = 1 AND deleted = 0;

-- 2) L1 station markers. 系统管理 (the seeded root id 1) carries admin - the three
--    department placeholder L1s are content-guarded inserts (NOT EXISTS by
--    up_id + title + workstation among live rows).
ALTER TABLE menu_info ADD COLUMN IF NOT EXISTS workstation varchar(16);
UPDATE menu_info SET workstation = 'admin', update_time = CURRENT_TIMESTAMP
 WHERE id = 1 AND deleted = 0 AND (workstation IS NULL OR workstation <> 'admin');
INSERT INTO menu_info (up_id, type, title, path, sort, visible, workstation)
SELECT 0, 'M', '放射科', 'radiology', 10, 1, 'radiology'
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND title = '放射科' AND workstation = 'radiology' AND deleted = 0);
INSERT INTO menu_info (up_id, type, title, path, sort, visible, workstation)
SELECT 0, 'M', '超声科', 'us', 20, 1, 'us'
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND title = '超声科' AND workstation = 'us' AND deleted = 0);
INSERT INTO menu_info (up_id, type, title, path, sort, visible, workstation)
SELECT 0, 'M', '内镜科', 'endo', 30, 1, 'endo'
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND title = '内镜科' AND workstation = 'endo' AND deleted = 0);

-- 3) The assignment button permission (F node under 用户管理 id 2) + admin auto-grant
--    (NOT-EXISTS live-binding guard - role 1 owns every new node without a manual step).
INSERT INTO menu_info (up_id, type, title, perms, sort, visible, workstation)
SELECT 2, 'F', '工作站授权', 'system:user:assign-ws', 4, 1, NULL
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 2 AND type = 'F' AND perms = 'system:user:assign-ws' AND deleted = 0);
INSERT INTO role_menu (role_id, menu_id)
SELECT 1, m.id FROM menu_info m
 WHERE m.type = 'F' AND m.perms = 'system:user:assign-ws' AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);
