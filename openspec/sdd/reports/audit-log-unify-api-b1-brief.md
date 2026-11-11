# Batch 1 Brief - audit-log-unify-api / schema + code vocabulary (tasks 1.1–1.3)

## Task text (openspec/changes/audit-log-unify-api/tasks.md)

- 1.1 Red: migration test asserts post-V25 state — log_sign family absent,
  log_info.type integer, menu 12 (审计日志, group 16, sort 3), menu 17 deleted +
  grants gone, dict catalog 1030 with 8 details
- 1.2 Write `V25__audit_log_unify.sql` (drop log_sign family; re-type type; menu
  re-parent + 17 retirement; dict seed 1030) — green
- 1.3 Add `common/Constants/LogTypes.java` (codes 1–5 + `codeOf(String)`; unit test:
  known labels map, null/blank/unknown → null)

## Read first

1. `openspec/changes/audit-log-unify-api/design.md` — D1/D2/D7 are the authoritative
   shape; `## Decisions` section restates all eight.
2. `openspec/changes/audit-log-unify-api/execution-contract.md` — Batch 1 scope +
   review gates (post-Batch-1 gate: migration applied on shared test PG, no residue).
3. `openspec/coordination/audit-log-unify.md` — D1–D8 + adaptation ledger.
4. `lumen-sev/lumen-system/src/main/resources/db/migration/V13__log_tables_partitioned.sql`
   — the log_sign family you are DROPPING (parents/children/parked/sequence names).
5. `lumen-sev/lumen-system/src/main/resources/db/migration/V23__role_type_dict_seed.sql`
   — the dict seed house style (ON CONFLICT guards, explicit ids, setval resync).
6. `lumen-sev/lumen-system/src/main/resources/db/migration/V14__sign_log_menu.sql` —
   menu seed style + the exact menu 17 tuple you are retiring (up_id 16, perms
   system:signlog:list, path signlog, component system/log/sign).
7. `lumen-sev/lumen-system/src/main/resources/db/migration/V22__workstation_w_nodes.sql`
   — the node-retirement precedent (DELETE role_menu WHERE menu_id IN (...), DELETE
   menu_info WHERE perms = ...).
8. `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthSystemDataMigrationTests.java`
   — the migration-test house style (assert against the live shared test DB post-Flyway).

## V25 requirements (design D1/D2/D7, restated as build steps)

One migration file `lumen-sev/lumen-system/src/main/resources/db/migration/V25__audit_log_unify.sql`,
four ordered sections, header comment style matching V13/V23 (English, WHY +
IDEMPOTENCE notes):

1. **Drop the log_sign family** — `DROP TABLE IF EXISTS public.log_sign CASCADE`
   (parent; cascades children log_sign_2026/2027/def);
   `DROP TABLE IF EXISTS public.log_sign_parked` (V13 conversion residue — guarded);
   drop sequence `public.log_sign_id_seq` (it may still be OWNED BY the dropped
   table's column or orphaned — handle both: `ALTER SEQUENCE ... OWNED BY NONE` is
   invalid if the sequence is gone, so use a DO block checking pg_class first).
2. **Re-type log_info.type** — `ALTER TABLE public.log_info ALTER COLUMN type TYPE
   integer USING NULLIF(trim(type), '')::integer` (V3 column is varchar(255); the
   USING makes the intent explicit; the table holds no data worth keeping).
3. **Menu convergence** — UPDATE menu_info id 12: title 日志查询→审计日志, up_id
   15→16, sort→3, update_time=CURRENT_TIMESTAMP (guarded WHERE up_id <> 16 OR title
   <> '审计日志' for idempotence, V5 §3 style); soft-delete menu 17
   (`deleted = 1`, guarded WHERE deleted = 0); hard-DELETE its role_menu grants
   (V22 precedent: DELETE FROM role_menu WHERE menu_id IN (SELECT id FROM menu_info
   WHERE perms = 'system:signlog:list')).
4. **Dict seed 「审计日志类型」** — catalog id 1030 + details 1030001–1030008:
   code 1=新增 2=修改 3=删除 4=授权 5=重置密码 10=登录成功 11=登录失败 12=登出
   (8 items; code column numeric int per V9/V12 model, value = display text, sort
   1–8, enable = 1). ON CONFLICT (id) DO NOTHING on both catal + detail with
   deterministic ids; final `setval(pg_get_serial_sequence(...))` resyncs for both
   tables (V23 discipline). Catalog `name` = '审计日志类型', cls follows V23's
   convention (copy whatever cls/sort values V23 uses for its catalog).

- NEVER touch V3–V24 (standing ruling: applied migrations are never rewritten).
- No entity/service/controller changes in this batch — schema + seed only.
- Note: existing rows in log_info.type are NULL/empty (suite residue is swept) —
  if the shared test DB happens to hold non-empty type strings, the USING clause
  must not fail; report what you find (the suite data uses 新增/修改/删除/授权/
  重置密码 labels only — if any other value appears, DONE_WITH_CONCERNS).

## LogTypes.java (task 1.3)

`lumen-sev/common/src/main/java/org/birdbro/common/Constants/LogTypes.java`
(absolute package `org.birdbro.common.Constants`, matching the sibling
`Constants/Constants.java`):

- `public static final int CREATE = 1; UPDATE = 2; DELETE = 3; GRANT = 4;
  RESET_PWD = 5;` (sign codes 10/11/12 already live in Constants — do NOT duplicate
  them here; reference Constants.SUCCESS/FAIL/SIGN_OUT where needed).
- `public static Integer codeOf(String label)` — maps the five `@Log` Chinese labels
  (新增/修改/删除/授权/重置密码) to their codes; null/blank → null; unknown label →
  null (NEVER throw — a future annotation label without a mapping degrades to null
  type, never breaks the write path). Hutool `StrUtil.isBlank` is the house tool.
- `@author birdbro` header per convention.

## TDD discipline

- Task 1.1 RED first: write the post-V25 assertions in
  `AuthSystemDataMigrationTests` (new @Test methods, following the file's existing
  style — Flyway runs before the context boots, so the assertions run post-V25).
  Run that test class alone: `./mvnw -pl lumen-system -am test -q
  -Dtest=AuthSystemDataMigrationTests` — expect FAIL (V25 absent).
- Task 1.2: write V25 → the same test class goes GREEN (Flyway applies V25 on the
  shared test DB at context boot).
- Task 1.3: unit test in common (`common/src/test/java/...`) — RED → GREEN
  (common tests are zero-infrastructure).

## Build/test commands

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q      # full reactor; V25 applies via Flyway on the team test server
```

- The test server is configured via gitignored `lumen-sev/.env-test.local`
  (already present on this machine). NEVER print its values into the report or
  commit them.
- The shared test DB is LIVE — V25 applies for real on first test-context boot.
  It is designed to be safe. NEVER point anything at rcp-test.
- After green, verify the layout with psql (read params from .env-test.local for
  the command only):

```sql
SELECT to_regclass('public.log_sign') AS log_sign, to_regclass('public.log_sign_parked') AS parked,
       to_regclass('public.log_sign_id_seq') AS seq;          -- expect all NULL
SELECT data_type FROM information_schema.columns
WHERE table_schema='public' AND table_name='log_info' AND column_name='type';   -- expect integer
SELECT id, title, up_id, sort, deleted FROM system.menu_info WHERE id IN (12, 17);  -- expect 12=审计日志/16/3/0, 17 deleted=1
SELECT count(*) FROM system.role_menu WHERE menu_id = 17 AND deleted = 0;           -- expect 0
SELECT c.id, c.name, count(d.id) FROM public.dict_catal c
LEFT JOIN public.dict_detail d ON d.ca_id = c.id AND d.deleted = 0
WHERE c.id = 1030 AND c.deleted = 0 GROUP BY c.id, c.name;                          -- expect 1030/审计日志类型/8
```

- If psql is unavailable: report DONE_WITH_CONCERNS with the suite-green evidence.

## Report + commit

- Write the full report to `openspec/sdd/reports/audit-log-unify-api-b1-report.md`
  (what/TDD evidence red+green per task/files/layout-check output/self-review/concerns).
- Commit on the current branch (dev): one commit for the batch, message style follows
  `git log --oneline -8` (emoji prefix, e.g. `✨ feat(sev): ...`); mention
  audit-log-unify-api b1.
- Then report back ≤15 lines: Status DONE | DONE_WITH_CONCERNS | BLOCKED |
  NEEDS_CONTEXT, one-line summary, report path, commit sha.
