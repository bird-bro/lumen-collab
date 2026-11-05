-- V14__sign_log_menu.sql - sign-log console page menu seed (sign-log-query design D8,
-- task 3.1; b2-review Important #1).
--
-- The 安全 group (16, seeded by V8) gains its second page: 登录日志 (menu 17, type 'C',
-- perm system:signlog:list - the LogController /sign method-level gate, path 'signlog',
-- component 'system/log/sign' - the route the web router registers, sort 3 right after
-- 日志查询 (12)), granted to the administrator role 1, with menu_info's sequence resynced
-- past the explicit id (V5/V8 discipline).
--
-- IDEMPOTENCE - deliberate deviation from V8's ON CONFLICT (id) DO NOTHING, documented
-- per the b2 review: the shared test DB accumulated soft-deleted probe menus (the b2
-- fixture seeded a per-run synthetic menu path LIKE 'b2signlogmenu_%' and only
-- logical-deleted it on cleanup); every suite run consumed sequence ids, and one probe
-- landed exactly on id 17. A DO NOTHING would SILENTLY NO-OP the real menu on that
-- probed database (no 登录日志 row, a dead console entry) while passing on fresh
-- databases - a masking discrepancy. The DO UPDATE below has REVIVE semantics instead:
-- on a fresh database it inserts the row first-time; on a probed database it rewrites
-- the occupied id 17 in place to the real menu tuple (deleted = 0), so both starting
-- points converge. Paired with the batch-3 fixture hard-delete + one-time probe purge
-- (task 3.2), the insert path is the live one on every current database; the update
-- path is the safety net for any database still carrying the probe residue.
--
-- The role_menu grant keeps V8's guarded INSERT SELECT (NOT EXISTS on the live row) -
-- it never duplicates a binding and never un-deletes one blindly.

INSERT INTO system.menu_info (id, up_id, type, title, perms, path, component, icon, sort) VALUES
    (17, 16, 'C', '登录日志', 'system:signlog:list', 'signlog', 'system/log/sign', NULL, 3)
ON CONFLICT (id) DO UPDATE SET
    up_id = 16,
    title = '登录日志',
    perms = 'system:signlog:list',
    path = 'signlog',
    component = 'system/log/sign',
    icon = NULL,
    sort = 3,
    deleted = 0,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO system.role_menu (role_id, menu_id)
SELECT 1, 17 WHERE NOT EXISTS
    (SELECT 1 FROM system.role_menu WHERE role_id = 1 AND menu_id = 17 AND deleted = 0);

SELECT setval(pg_get_serial_sequence('system.menu_info', 'id'),
              (SELECT MAX(id) FROM system.menu_info));
