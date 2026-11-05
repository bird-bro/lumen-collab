-- V13__log_tables_partitioned.sql - public.log_info / public.log_sign become
-- RANGE (date_ym) partitioned tables (sign-log-query task 1.1, design D1/D2/D3).
--
-- WHY: the OPS reference database (rcp-test, inspected live 2026-11-05) keeps both log
-- tables partitioned as RANGE (date_ym) with yearly children (FROM (202500) TO (202599)
-- style) - partitioning exists for yearly detach-and-archive maintenance, not query
-- performance. PostgreSQL cannot ALTER a plain heap into a partitioned one, so each
-- table converts by copy-swap (design D1): ACCESS EXCLUSIVE lock -> <name>_new
-- partitioned parent (V3 columns verbatim, composite PK) -> INSERT SELECT carry-over
-- -> drop old -> rename -> fix sequence ownership and index/constraint names.
--
-- Layout deltas vs the V3 plain heaps (all intentional, design D2):
--   PK        (id, date_ym) composite - PG requires the partition key inside unique
--             constraints. id stays globally unique through the existing sequence, so
--             MyBatis-Plus AUTO identity and the append path are untouched. id keeps
--             V3's LEADING position (expanded from the serial shorthand in place):
--             the repo's shape contract (AuthSystemDataMigrationTests) pins "must
--             lead with its serial id" and the copied entities align with V3 order.
--   children  log_info_2026 / log_info_2027 (FOR VALUES FROM (202600) TO (202699) /
--             (202700) TO (202799)) PLUS one DEFAULT partition per table
--             (log_info_def / log_sign_def) - the user-ruled deviation from rcp-test,
--             which silently ERRORS on an un-pre-created year; the default catches
--             rows of years nobody pre-created instead of failing the write.
--   indexes   idx_*_date_ym / idx_*_uid are recreated at parent level as partitioned
--             indexes (child indexes follow automatically). They are built under temp
--             *_p names - the old names are still taken until the swap - and renamed
--             back afterwards, so the external index names are unchanged.
--
-- Ordering note: the partitions (including the DEFAULT) are created BEFORE the
-- carry-over INSERT. A partitioned table with no matching partition rejects every
-- INSERT ("no partition of relation found for row"), and the live log tables are NOT
-- empty - the insert-first order only works on empty tables. Copying after the
-- partitions exist is correct for both empty and populated databases.
--
-- CONVERGENCE preamble (the V6/V7/V12 shared-DB discipline): the DO block makes this
-- migration converge on BOTH starting shapes. Fresh database: the V3 heap is renamed
-- to <name>_parked and rebuilt partitioned. A database that already carries an
-- earlier application of this very conversion (the shared test server, whose
-- 2026-11-05 pre-review draft left the tables partitioned with a different column
-- order): the rows are PARKED into <name>_parked, the old shape is torn down, and the
-- corrected shape is rebuilt with every row carried through - no row is ever dropped
-- with its table. The parked table is dropped only AFTER the copy. There is no
-- guarded pre-drop of <name>_parked on purpose: a stale parked table (reachable only
-- through an out-of-transaction hand run) must fail LOUDLY at the rename, never
-- silently destroy the only copy of the rows.
--
-- Sequence dance (design D3): the serial sequence is OWNED BY the current table's id,
-- so dropping that table would drop it mid-swap. OWNED BY NONE before the teardown,
-- OWNED BY <new>.id after the rename, then setval past MAX(id).
--
-- TRANSACTIONS / IDEMPOTENCE: no explicit BEGIN/COMMIT on purpose - Flyway runs this
-- script inside ONE transaction (PostgreSQL DDL is transactional), which makes each
-- table's copy-swap atomic AND the whole file all-or-nothing: a failure in the
-- log_sign half rolls the log_info half back too, so a retry always starts from the
-- pre-migration shape (an explicit mid-file COMMIT would leave a half-converted
-- database a retry cannot fix). Convergent rather than one-shot: re-running against
-- either starting shape (plain heap or already partitioned) rebuilds cleanly with
-- rows preserved. V3-V12 are never rewritten; fresh databases reach this layout as
-- V3 plain heaps + this conversion.
--
-- Maintenance caveat: BEFORE attaching a new yearly child, a non-empty DEFAULT
-- partition must be drained first (CREATE TABLE ... PARTITION OF fails while any
-- default row would satisfy the new bound) - move the rows into the new child before
-- attaching it. Yearly children ship one year ahead (2027 pre-created here at
-- 2026-11); automated pre-creation is a future ops change, out of scope.

-- ============================== log_info ==============================
LOCK TABLE public.log_info IN ACCESS EXCLUSIVE MODE;
ALTER SEQUENCE public.log_info_id_seq OWNED BY NONE;

-- Park the rows, then vacate the name: the DO block ends with NO public.log_info
-- left and ALL its rows in public.log_info_parked (plain heap, both branches).
DO $conv_log_info$
BEGIN
    IF EXISTS (SELECT 1
               FROM pg_class c
               JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'public' AND c.relname = 'log_info'
                 AND c.relkind = 'p') THEN
        -- already partitioned by an earlier application of this very conversion:
        -- park every row, then tear the partitioned shape down
        CREATE TABLE public.log_info_parked AS
            SELECT id, date_ym, title, descr, type, uid, account, action_time, spent,
                   method, name, pack, url, session, mobile, ip, os, browser, engine,
                   record, param, result, exception, app, deleted, create_time
            FROM public.log_info;
        DROP TABLE public.log_info CASCADE;
    ELSE
        -- plain V3 heap: rename it aside and rebuild from it
        ALTER TABLE public.log_info RENAME TO log_info_parked;
    END IF;
END
$conv_log_info$;

CREATE TABLE public.log_info_new (
    id          integer      NOT NULL DEFAULT nextval('public.log_info_id_seq'), -- V3 serial shorthand, expanded in place
    date_ym     integer      NOT NULL,
    title       varchar(255),
    descr       varchar(255),
    type        varchar(255),
    uid         integer,
    account     varchar(128),
    action_time timestamp,
    spent       integer,
    method      varchar(16),
    name        varchar(128),
    pack        varchar(255),
    url         varchar(255),
    session     varchar(128),
    mobile      boolean,
    ip          varchar(64),
    os          varchar(64),
    browser     varchar(64),
    engine      varchar(64),
    record      integer,                          -- 0 INFO / 1 ERROR
    param       text,
    result      text,
    exception   text,
    app         varchar(32),
    deleted     integer      NOT NULL DEFAULT 0,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT log_info_new_pkey PRIMARY KEY (id, date_ym)
) PARTITION BY RANGE (date_ym);

CREATE TABLE public.log_info_2026 PARTITION OF public.log_info_new FOR VALUES FROM (202600) TO (202699);
CREATE TABLE public.log_info_2027 PARTITION OF public.log_info_new FOR VALUES FROM (202700) TO (202799);
CREATE TABLE public.log_info_def  PARTITION OF public.log_info_new DEFAULT;

-- Carry-over: explicit column lists on BOTH sides - never positional form.
INSERT INTO public.log_info_new
    (id, date_ym, title, descr, type, uid, account, action_time, spent, method, name,
     pack, url, session, mobile, ip, os, browser, engine, record, param, result,
     exception, app, deleted, create_time)
SELECT id, date_ym, title, descr, type, uid, account, action_time, spent, method, name,
       pack, url, session, mobile, ip, os, browser, engine, record, param, result,
       exception, app, deleted, create_time
FROM public.log_info_parked;

CREATE INDEX idx_log_info_date_ym_p ON public.log_info_new (date_ym);
CREATE INDEX idx_log_info_uid_p     ON public.log_info_new (uid);

DROP TABLE public.log_info_parked;
ALTER TABLE public.log_info_new RENAME TO log_info;
ALTER TABLE public.log_info RENAME CONSTRAINT log_info_new_pkey TO log_info_pkey;
ALTER INDEX public.idx_log_info_date_ym_p RENAME TO idx_log_info_date_ym;
ALTER INDEX public.idx_log_info_uid_p     RENAME TO idx_log_info_uid;
ALTER SEQUENCE public.log_info_id_seq OWNED BY public.log_info.id;
SELECT setval('public.log_info_id_seq', (SELECT COALESCE(MAX(id), 1) FROM public.log_info));

-- ============================== log_sign ==============================
LOCK TABLE public.log_sign IN ACCESS EXCLUSIVE MODE;
ALTER SEQUENCE public.log_sign_id_seq OWNED BY NONE;

-- Park the rows, then vacate the name (same two-branch convergence as log_info).
DO $conv_log_sign$
BEGIN
    IF EXISTS (SELECT 1
               FROM pg_class c
               JOIN pg_namespace n ON n.oid = c.relnamespace
               WHERE n.nspname = 'public' AND c.relname = 'log_sign'
                 AND c.relkind = 'p') THEN
        CREATE TABLE public.log_sign_parked AS
            SELECT id, date_ym, msg, descr, type, uid, account, action_time, mobile,
                   ip, os, browser, engine, session, token, app, deleted, create_time
            FROM public.log_sign;
        DROP TABLE public.log_sign CASCADE;
    ELSE
        ALTER TABLE public.log_sign RENAME TO log_sign_parked;
    END IF;
END
$conv_log_sign$;

CREATE TABLE public.log_sign_new (
    id          integer      NOT NULL DEFAULT nextval('public.log_sign_id_seq'), -- V3 serial shorthand, expanded in place
    date_ym     integer      NOT NULL,
    msg         varchar(255),
    descr       varchar(128),
    type        integer,
    uid         integer,
    account     varchar(128),
    action_time timestamp,
    mobile      boolean,
    ip          varchar(64),
    os          varchar(64),
    browser     varchar(64),
    engine      varchar(64),
    session     varchar(128),
    token       varchar(255),
    app         varchar(32),
    deleted     integer      NOT NULL DEFAULT 0,
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT log_sign_new_pkey PRIMARY KEY (id, date_ym)
) PARTITION BY RANGE (date_ym);

CREATE TABLE public.log_sign_2026 PARTITION OF public.log_sign_new FOR VALUES FROM (202600) TO (202699);
CREATE TABLE public.log_sign_2027 PARTITION OF public.log_sign_new FOR VALUES FROM (202700) TO (202799);
CREATE TABLE public.log_sign_def  PARTITION OF public.log_sign_new DEFAULT;

-- Carry-over: explicit column lists on BOTH sides - never positional form.
INSERT INTO public.log_sign_new
    (id, date_ym, msg, descr, type, uid, account, action_time, mobile, ip, os,
     browser, engine, session, token, app, deleted, create_time)
SELECT id, date_ym, msg, descr, type, uid, account, action_time, mobile, ip, os,
       browser, engine, session, token, app, deleted, create_time
FROM public.log_sign_parked;

CREATE INDEX idx_log_sign_date_ym_p ON public.log_sign_new (date_ym);
CREATE INDEX idx_log_sign_uid_p     ON public.log_sign_new (uid);

DROP TABLE public.log_sign_parked;
ALTER TABLE public.log_sign_new RENAME TO log_sign;
ALTER TABLE public.log_sign RENAME CONSTRAINT log_sign_new_pkey TO log_sign_pkey;
ALTER INDEX public.idx_log_sign_date_ym_p RENAME TO idx_log_sign_date_ym;
ALTER INDEX public.idx_log_sign_uid_p     RENAME TO idx_log_sign_uid;
ALTER SEQUENCE public.log_sign_id_seq OWNED BY public.log_sign.id;
SELECT setval('public.log_sign_id_seq', (SELECT COALESCE(MAX(id), 1) FROM public.log_sign));
