# Batch 1 Brief - sign-log-query / V13 partitioned log tables (tasks 1.1 + 1.2)

## Task text (openspec/changes/sign-log-query/tasks.md)

- 1.1 Write `lumen-sev/lumen-system/src/main/resources/db/migration/V13__log_tables_partitioned.sql`
  (exact column lists are copied from V3 - see below). Verify: backend tests pass against the
  team test server; partition layout check (SQL below) shows RANGE (date_ym) with 2026/2027/def
  children and `(id, date_ym)` primary keys; rows preserved.
- 1.2 Regression: `./mvnw test -q` from `lumen-sev/` green (AuthLogQueryTests,
  AuthSignLogTests, AuthOperationLogTests all resolve the partitioned parents unchanged).

## Read first

1. `openspec/changes/sign-log-query/design.md` - decisions D1-D3 are the authoritative shape
2. `openspec/changes/sign-log-query/execution-contract.md` - Batch 1 + review gates
3. `lumen-sev/lumen-system/src/main/resources/db/migration/V3__system_data.sql` - the
   `CREATE TABLE public.log_info` / `public.log_sign` blocks: copy column lists VERBATIM from
   there (do not retype - order matters for INSERT SELECT if you use positional form; prefer
   explicit column lists)
4. `lumen-sev/CLAUDE.md` - build commands; JDK 11 export required

## V13 requirements (design D1-D3, restated as build steps)

One copy-swap transaction PER TABLE (log_info first, then log_sign), same skeleton:

```sql
BEGIN;
LOCK TABLE public.log_info IN ACCESS EXCLUSIVE MODE;
ALTER SEQUENCE public.log_info_id_seq OWNED BY NONE;          -- D3: survive the drop
CREATE TABLE public.log_info_new ( <V3 columns verbatim>,
    id integer NOT NULL DEFAULT nextval('public.log_info_id_seq'),   -- replaces the serial shorthand
    CONSTRAINT log_info_new_pkey PRIMARY KEY (id, date_ym) )         -- D2: composite PK
    PARTITION BY RANGE (date_ym);
INSERT INTO public.log_info_new (<explicit column list>) SELECT <same list> FROM public.log_info;
CREATE INDEX idx_log_info_date_ym_p ON public.log_info_new (date_ym);  -- temp names: old names still taken
CREATE INDEX idx_log_info_uid_p    ON public.log_info_new (uid);
CREATE TABLE public.log_info_2026 PARTITION OF public.log_info_new FOR VALUES FROM (202600) TO (202699);
CREATE TABLE public.log_info_2027 PARTITION OF public.log_info_new FOR VALUES FROM (202700) TO (202799);
CREATE TABLE public.log_info_def  PARTITION OF public.log_info_new DEFAULT;
DROP TABLE public.log_info;
ALTER TABLE public.log_info_new RENAME TO log_info;
ALTER TABLE public.log_info RENAME CONSTRAINT log_info_new_pkey TO log_info_pkey;
ALTER INDEX public.idx_log_info_date_ym_p RENAME TO idx_log_info_date_ym;
ALTER INDEX public.idx_log_info_uid_p     RENAME TO idx_log_info_uid;
ALTER SEQUENCE public.log_info_id_seq OWNED BY public.log_info.id;     -- D3: re-point
SELECT setval('public.log_info_id_seq', (SELECT COALESCE(MAX(id), 1) FROM public.log_info));
COMMIT;
```

- log_sign mirror: sequence `log_sign_id_seq`, PK `log_sign_pkey`, indexes
  `idx_log_sign_date_ym` / `idx_log_sign_uid`, children `log_sign_2026`/`log_sign_2027`/
  `log_sign_def`.
- Header comment: purpose, rcp-test reference layout, the user-ruled `*_def` deviation, and
  the detach caveat (a non-empty DEFAULT partition must be drained before attaching a new
  yearly child).
- File header style: match V3/V5 comment style (English, explains WHY + IDEMPOTENCE/one-shot
  note). `@author birdbro` convention applies to code files, not SQL - follow V3's header.
- NEVER touch V3-V12 (standing ruling: applied migrations are never rewritten).
- Do NOT modify anything else: no entity/service/controller changes in this batch.

## Environment / commands

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q      # applies V13 via Flyway on the team test server, then runs suites
```

- The test server is configured via gitignored `lumen-sev/.env-test.local` (AUTH_TEST_* vars)
  - already present on this machine. NEVER print its values into the report or commit them.
- The shared test DB is LIVE - V13 applies for real on first test-context boot. It is
  designed to be safe (single tx, ms-scale lock) - do not "test it manually" against any
  other database. NEVER point anything at rcp-test.
- After green: verify partition layout on the test server (psql is available; read connection
  params from `.env-test.local` for the command only):

```sql
SELECT c.relname, c.relkind, pg_get_partkeydef(c.oid)
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relname IN ('log_info','log_sign');   -- expect relkind 'p', 'RANGE (date_ym)'

SELECT p.relname AS parent, c.relname AS child, pg_get_expr(c.relpartbound, c.oid) AS bound
FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent JOIN pg_class c ON c.oid = i.inhrelid
WHERE p.relname IN ('log_info','log_sign') ORDER BY 1, 2;             -- expect 6 rows
```

- If psql is unavailable: report DONE_WITH_CONCERNS with the suite-green evidence; the layout
  gets re-checked at verify stage. Do NOT improvise other DB access paths.

## TDD note for migrations

The regression suite (task 1.2) IS the test: run `./mvnw test -q` BEFORE writing V13 to
capture the pre-change green baseline (characterization), then write V13, then re-run -
green again = the write/query paths resolve the partitioned parents unchanged. Record both
runs in the report.

## Report + commit

- Write the full report to `openspec/sdd/reports/sign-log-query-b1-report.md`
  (what/TDD evidence baseline+after/files/layout-check output/self-review/concerns).
- Commit on the current branch (dev): one commit for the migration, message style follows
  `git log --oneline -8` (emoji prefix, e.g. `✨ feat(system): ...`); mention sign-log-query b1.
- Then report back ≤15 lines: Status DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT,
  one-line summary, report path, commit sha.
