-- V25__audit_log_unify.sql - the audit-trail unification (audit-log-unify-api b1,
-- coordination audit-log-unify.md D1/D2/D4/D6/D7, design D1): ONE audit table
-- (public.log_info), one page (审计日志), one permission, Integer type codes with the
-- 「审计日志类型」 dict driving the labels.
--
-- Four ordered sections:
--   1. DROP the log_sign family - the partitioned parent cascades its 2026/2027/def
--      children (V13's own shape); the parked conversion residue of any pre-V13 heap
--      goes guarded; the id sequence dies last. No data migration (D1: the table held
--      only smoke-test residue; the sign write path retargets to log_info in b2).
--   2. Re-type log_info.type varchar(255) -> integer (D2: String at the @Log
--      annotation edge, Integer code at rest; the code vocabulary is LogTypes 1~5 +
--      Constants.SUCCESS/FAIL/SIGN_OUT = 10/11/12 verbatim).
--   3. Menu convergence (D6 one page): menu 12 日志查询 -> 审计日志, re-parented from
--      系统配置 (15) into 安全 (16), sort 3; menu 17 登录日志 soft-deletes and its
--      role_menu grants hard-DELETE (V22 node-retirement precedent - a grant pointing
--      at a dead menu must never be holdable again).
--   4. Seed the 「审计日志类型」 dict catalog (D7, mirrors role-type-dict's V23):
--      catalog id 1030, details 1030001-1030008 - codes 1/2/3/4/5 + 10/11/12 verbatim
--      (the segments the write path already emits), value = display text, sort 1-8.
--      Consumed by-name via the existing ungated GET /dicts/items/{catalogName}.
--
-- IDEMPOTENCE (V5/V6/V12 shared-DB discipline): guarded statements throughout - a
-- re-run (or a hand-applied hot run followed by the Flyway run) is a no-op, and fresh
-- databases converge on the same shape. V3-V24 are NEVER rewritten (the standing
-- never-rewrite ruling) - this script rolls forward.
--
-- TYPE-CARRY DEVIATION (recorded): the design's literal USING
-- NULLIF(trim(type),'')::integer ERRORS on any non-numeric label string, and the
-- shared test DB's log_info carries 18 real label rows (新增/修改/删除/授权 - all
-- inside the known five-label vocabulary, verified live 2026-11-10; NO value outside
-- it). The brief requires the USING to not fail; D1 rules no data migration. The
-- guarded CASE therefore re-homes the KNOWN labels to their ruled codes (the same
-- translation LogTypes.codeOf performs for every future row) and degrades anything
-- else to NULL - a label without a mapping degrades, never breaks the migration.
-- Numeric strings (probe-era rows) carry through as their numbers; NULL/empty stay
-- NULL. On a fresh empty table the CASE is a no-op and the column simply re-types.

-- ================= 1) drop the log_sign family (coordination D1) =================
-- The parent drop cascades the partitioned children (log_sign_2026/2027/def) and any
-- indexes attached to them; the parked table (V13's conversion residue shape) and the
-- sequence are handled explicitly - the sequence may still be OWNED BY the dropped
-- table (dies with it) or already orphaned (dies here); the DO block checks pg_class
-- so neither state errors.

DROP TABLE IF EXISTS public.log_sign CASCADE;
DROP TABLE IF EXISTS public.log_sign_parked;

DO $drop_log_sign_seq$
BEGIN
    IF EXISTS (SELECT 1
               FROM pg_class c
               JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'public' AND c.relname = 'log_sign_id_seq'
                 AND c.relkind = 'S') THEN
        ALTER SEQUENCE public.log_sign_id_seq OWNED BY NONE;
        DROP SEQUENCE public.log_sign_id_seq;
    END IF;
END
$drop_log_sign_seq$;

-- ================= 2) re-type log_info.type to integer (coordination D2) =================
ALTER TABLE public.log_info ALTER COLUMN type TYPE integer
    USING CASE
        WHEN trim(type) ~ '^[0-9]{1,9}$' THEN trim(type)::integer   -- numeric rows carry through
        WHEN type = '新增'     THEN 1
        WHEN type = '修改'     THEN 2
        WHEN type = '删除'     THEN 3
        WHEN type = '授权'     THEN 4
        WHEN type = '重置密码' THEN 5
        ELSE NULL                                                  -- unmapped label degrades, never fails
    END;

-- ================= 3) menu convergence (coordination D4/D6) =================
-- The unified page: menu 12 retitles, re-parents into 安全 (16), and takes the
-- retiring 17's sort slot. Guarded WHERE per the V5 §3/V8 idempotence style.

UPDATE system.menu_info
   SET title = '审计日志',
       up_id = 16,
       sort = 3,
       update_time = CURRENT_TIMESTAMP
 WHERE id = 12
   AND (up_id <> 16 OR title <> '审计日志' OR sort <> 3);

-- The retiring page: its grants go first (V22 precedent), then the row soft-deletes -
-- deleted = 1 keeps the id reserved in menu history while the perm string
-- system:signlog:list leaves the live vocabulary WITH the row.

DELETE FROM system.role_menu
 WHERE menu_id IN (SELECT id FROM system.menu_info WHERE perms = 'system:signlog:list');

UPDATE system.menu_info
   SET deleted = 1,
       update_time = CURRENT_TIMESTAMP
 WHERE id = 17
   AND deleted = 0;

-- ================= 4) seed 「审计日志类型」 (coordination D7, V23 style) =================
-- Deterministic ids guarded ON CONFLICT (id) DO NOTHING: catalog 1030 (clear of V6's
-- 1001-1007 band and V23's 1020), details 1030001-1030008 (the V23 +100000 offset
-- band convention). The catalog anchors BY NAME (V12 dropped the code column): the
-- by-name lookup endpoint and the frontend dict hook resolve 「审计日志类型」.
-- cls stays NULL and sort 99 copies V23's catalog convention (top of the dict-admin
-- listing, out of business sorts' way).

INSERT INTO public.dict_catal (id, name, enable, sort, remark) VALUES
    (1030, '审计日志类型', 1, 99, 'log_info.type 的取值词表(audit-log-unify-api;操作 1~5 + 登录事件 10~12)')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO public.dict_detail (id, ca_id, code, value, sort, enable) VALUES
    (1030001, 1030,  1, '新增',     1, 1),
    (1030002, 1030,  2, '修改',     2, 1),
    (1030003, 1030,  3, '删除',     3, 1),
    (1030004, 1030,  4, '授权',     4, 1),
    (1030005, 1030,  5, '重置密码', 5, 1),
    (1030006, 1030, 10, '登录成功', 6, 1),
    (1030007, 1030, 11, '登录失败', 7, 1),
    (1030008, 1030, 12, '登出',     8, 1)
    ON CONFLICT (id) DO NOTHING;

-- V23 shipped without its setval tail - a gap this seed does not repeat: both
-- sequences advance past the explicit ids so the next application-side dict insert
-- never collides with a seeded id (V6/V14 discipline).

SELECT setval(pg_get_serial_sequence('public.dict_catal', 'id'),
              (SELECT MAX(id) FROM public.dict_catal));
SELECT setval(pg_get_serial_sequence('public.dict_detail', 'id'),
              (SELECT MAX(id) FROM public.dict_detail));
