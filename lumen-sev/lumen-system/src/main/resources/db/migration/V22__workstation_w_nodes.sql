-- V22__workstation_w_nodes.sql - the workstation W-node correction
-- (workstation-w-nodes-api). The per-user direct-grant machinery built by V21 was
-- ruled wrong (user 2026-11-06: menu permissions flow ONLY through roles - the
-- workstation IS a menu node, type W, granted through the existing role_menu flow).
-- This migration tears the machinery down and seeds the W nodes:
--   1. DROP the user_workstation table (the direct user-grant store)
--   2. DELETE the assign-ws button node + its role_menu grants
--   3. DROP the menu_info.workstation column (the V21 root-marker, superseded)
--   4. DELETE the V21 placeholder L1s (ids 298-300 - test-era rows, no children)
--   5. INSERT three type-W L1 nodes (放射工作站/超声工作站/内镜工作站) + admin
--      auto-grant (content-guarded, the V15/V21 discipline)
-- Idempotence: guarded statements throughout, re-run is a row-count no-op (proven by
-- AuthWorkstationSeedTests). Comment lines carry NO semicolons (the re-execution
-- test splits statements on them).

-- 1) drop the direct-grant table
DROP TABLE IF EXISTS user_workstation;

-- 2) delete the per-user assignment button and its grants
DELETE FROM role_menu WHERE menu_id IN (SELECT id FROM menu_info WHERE perms = 'system:user:assign-ws');
DELETE FROM menu_info WHERE perms = 'system:user:assign-ws';

-- 3) drop the V21 root-marker column
ALTER TABLE menu_info DROP COLUMN IF EXISTS workstation;

-- 4) delete the V21 placeholder L1s (test-era rows with no children, their grants go
--    first to keep the FK happy)
DELETE FROM role_menu WHERE menu_id IN (298, 299, 300);
DELETE FROM menu_info WHERE id IN (298, 299, 300);

-- 5) the W nodes: type W at L1, one per department station, admin auto-granted
INSERT INTO menu_info (up_id, type, title, path, sort, visible)
SELECT 0, 'W', '放射工作站', 'radiology', 10, 1
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND type = 'W' AND title = '放射工作站' AND deleted = 0);
INSERT INTO menu_info (up_id, type, title, path, sort, visible)
SELECT 0, 'W', '超声工作站', 'us', 20, 1
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND type = 'W' AND title = '超声工作站' AND deleted = 0);
INSERT INTO menu_info (up_id, type, title, path, sort, visible)
SELECT 0, 'W', '内镜工作站', 'endo', 30, 1
 WHERE NOT EXISTS (SELECT 1 FROM menu_info
                    WHERE up_id = 0 AND type = 'W' AND title = '内镜工作站' AND deleted = 0);

INSERT INTO role_menu (role_id, menu_id)
SELECT 1, m.id FROM menu_info m
 WHERE m.type = 'W' AND m.up_id = 0 AND m.deleted = 0
   AND NOT EXISTS (SELECT 1 FROM role_menu rm
                    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0);
