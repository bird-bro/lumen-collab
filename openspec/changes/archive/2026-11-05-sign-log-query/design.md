# Design

## Context

`public.log_info`/`log_sign` are plain heaps created by V3 (serial single-column PKs,
`idx_*_date_ym` + `idx_*_uid`); the framework append-write path (`LogAspect` +
`AsyncFactory.recordForSign` → `insertInfo`/`insertSign` XML) and `AuthServiceImpl` already
produce rows. The query side exists only for `log_info` (`GET /system/logs`, class-level
`@PreAuthorize("hasAuthority('system:log:list')")`, `ILogInfoService.getPage` QueryWrapper
pattern). The OPS reference database (rcp-test, inspected live 2026-11-05) partitions both
log tables as `RANGE (date_ym)` with yearly children (`FROM (202500) TO (202599)` style,
~49k rows / 22 MB in the busiest year - partitioning is for yearly detach-and-archive
maintenance, not query performance). Spring Boot 2.5 / MyBatis-Plus / PG 15.

## Goals / Non-Goals

**Goals:**
- Partitioned log tables matching the rcp-test layout, plus the user-ruled default-partition
  safety net (`log_info_def` / `log_sign_def`)
- A sign-log query endpoint + console page with its own permission string
- Zero changes to the append-write path or the existing operation-log endpoint contract

**Non-Goals:**
- Automated partition pre-creation (pg_partman / scheduled jobs) - future ops change
- Log retention / archival enforcement (detach策略 out of scope, as in system-data)
- Operation-log page or `GET /system/logs` behavior changes
- Button-level permission granularity (recorded follow-up in auth-rbac)

## Decisions

**D1 - Copy-swap conversion, one transaction per table.** PostgreSQL cannot ALTER a plain
table into a partitioned one. V13, per table: single tx with `ACCESS EXCLUSIVE` lock →
create `<name>_new` partitioned parent (same columns, composite PK) → `INSERT SELECT` carry
over → drop old → rename → fix sequence ownership and index/constraint names. Dev-scale row
counts make the lock window milliseconds; the tx guarantees no lost rows even with the
backend hot. Alternative rejected: trigger-based dual-write with backfill - complexity the
data volume does not justify. **Applied amendment (b1 review)**: the implemented file relies
on Flyway's file-wide transaction (one tx for BOTH tables - stronger than per-table) and
omits explicit BEGIN/COMMIT; the ACCESS EXCLUSIVE window therefore covers both tables
(trivial at dev row counts, relevant only if a future ops change reuses the skeleton at
production scale).

**D2 - Yearly partitions + default, composite PK.** Key is the existing `date_ym`
(yyyymm int); children 2026/2027 pre-created, `log_info_def`/`log_sign_def` DEFAULT catch
un-pre-created years (the ruled deviation from rcp-test, which silently errors there).
PK becomes `(id, date_ym)` - PG requires the partition key in unique constraints; `id`
remains globally unique via the existing sequence, so MyBatis-Plus `updateById`-style
identity semantics and the append path are untouched. Indexes `idx_*_date_ym` / `idx_*_uid`
are recreated as partitioned indexes (parent-level, child indexes auto-created).

**D3 - Sequence ownership dance.** The old serial sequence is OWNED BY the old table's id;
dropping the old table would drop it. V13 re-points ownership: `ALTER SEQUENCE ... OWNED BY
NONE` before the drop, `OWNED BY <new>.id` after the rename, then `setval` to `MAX(id)`.

**D4 - Sign endpoint inside LogController with a method-level gate.** `GET /sign` on the
existing controller (mirrors 随访's one-log-controller shape); method-level
`@PreAuthorize("hasAuthority('system:signlog:list')")` takes precedence over the class-level
`system:log:list` for that method (Spring method-security rule), making the two page
permissions independent. Alternative rejected: separate `SignLogController` - splits one log
family across files for no behavioral gain.

**D5 - Sign query filters: `account` / `uid` / `dateYm`.** Mirrors 随访's login-log filter
set (account + time) plus the lumen query convention (uid/dateYm). No `type` filter: the
copied `log_sign.type` semantics (login/logout/success/failure integers) are not documented
enough to expose as a text filter; `msg` already carries the outcome in prose.

**D6 - Two pages, not tabs** (user ruling 2026-11-05). The column sets, filter semantics,
and audit audiences of the two tables genuinely differ; any "one page" would be frontend
tabs over two endpoints - a two-page implementation wearing one menu entry. Separate pages
keep the menu-permission boundary (one menu row = one gate), give each page a deep-linkable
route, and align with the 随访 permission-model reference.

**D7 - Token never leaves the service.** `log_sign` stores the session `token` (copied
lineage). The query path selects all columns except `token` (MyBatis-Plus select exclusion);
the typed frontend model has no token field. The copied `getLogSignPage(LogRequest)` XML
keeps returning tokens for non-console callers - unchanged, out of scope.

**D8 - Menu seed as V14, separate from the DDL migration.** V13 = table conversion (one
concern), V14 = menu row 17 (up_id 16 安全, title 登录日志, perm `system:signlog:list`, path
`signlog`, component `system/log/sign`, sort after 日志查询) + role_menu grant to role 1 +
`setval`, following the V5/V8 idempotence discipline (guarded WHERE, explicit id +
sequence resync). **Applied amendment (b3)**: the conflict clause is
`ON CONFLICT (id) DO UPDATE ... deleted = 0` (revive semantics) instead of V8's
DO NOTHING - b2-review found the shared test DB's probe rows occupying id 17, where
DO NOTHING would silently no-op; the implemented form converges on fresh AND polluted
DBs (live-exercised in a rolled-back transaction by the b3 review).

## Risks / Trade-offs

- [Hot backend blocks during copy-swap] → ACCESS EXCLUSIVE window is ms at dev scale; the
  tx makes it all-or-nothing; deployed via a normal backend restart.
- [Rows land in default partitions and pile up] → default is a safety net; the yearly
  pre-create step ships one year ahead (2027 seeded at 2026-11); future ops change adds
  pre-creation automation. Detaching a non-empty default requires moving rows first -
  documented in V13 header comments.
- [Method-level @PreAuthorize precedence subtlety] → pinned by a dedicated test: no-`signlog`
  caller gets forbidden on `/system/logs/sign` even with `system:log:list`, and vice versa.
- [Flyway discipline] → V13/V14 are new files; no applied migration is rewritten
  (standing ruling 2026-11-03).

## Migration Plan

1. Backend deploy: Flyway runs V13 (partition conversion) then V14 (menu seed) on boot.
2. Frontend deploy: new page + route + menu-path mapping ride the normal web build; the
   menu row appears after re-login (menus load at login).
3. Rollback: forward-only - the conversion preserves all rows in one tx; at this stage no
   PITR dance is warranted. A defect in the sign endpoint/page is additive and reversible
   by redeploy.

## Open Questions

(none - partition naming, page split, and default-partition policy were ruled by the user
in session 2026-11-05)
