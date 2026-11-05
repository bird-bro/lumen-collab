# Proposal: sign-log-query

## Why

The console has exactly one log page (操作日志, `GET /system/logs`) while the 随访
permission-model reference ships two (操作日志 + 登录日志). The `log_sign` data is already
being written on every login/logout (system-data task 3.4) but has no query endpoint and no
page - the audit data is dark. At the same time the log tables are plain heaps; the OPS
reference database (rcp-test) partitions `log_info`/`log_sign` by `RANGE (date_ym)` so old
years can be detached and archived without DELETE storms, and lumen copied the log machinery
1:1 - the table shape should follow.

## What Changes

- **`public.log_info` / `public.log_sign` become partitioned tables** (new migration
  `V13__log_tables_partitioned.sql`): declarative `RANGE (date_ym)` with yearly child
  partitions (2026, 2027) mirroring the rcp-test layout, plus a **default-partition safety
  net named `log_info_def` / `log_sign_def`** (user decision 2026-11-05 - a deliberate
  deviation from rcp-test, which has no default partition). Primary keys become composite
  `(id, date_ym)` (PG requires the partition key in unique constraints); existing row data
  is carried over in the same transaction.
- **Backend**: `LogController` gains a `GET /api/v1/system/logs/sign` paged sign-log query
  (new method, no existing method modified) gated by a NEW page permission
  `system:signlog:list` (method-level `@PreAuthorize` overrides the class-level
  `system:log:list`); `ILogSignService` gains an additive `getPage(...)` mirroring the
  info-side pattern. Responses exclude the `token` column (credential material must not
  reach admin screens).
- **Menu seed** (`V14__sign_log_menu.sql`): 登录日志 menu row (id 17) under the 安全 group
  (16) next to 日志查询, perm `system:signlog:list`, granted to role 1.
- **Frontend**: new read-only 登录日志 page at `/system/logs/sign` (columns 账号/事件/描述/
  IP/OS/浏览器/操作时间, filters 账号/操作人ID/月份), same console-shell chrome as the
  operation-log page, no mock layer - real backend only.
- **Two pages, not tabs** (user decision 2026-11-05): separate menu entries and permission
  strings; the page-vs-tabs analysis is recorded in design.md.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system-data`: MODIFIED "System Log Tables" (partitioned end-state) + ADDED
  "Sign Log Query Endpoint" (paged `log_sign` query, token excluded, `system:signlog:list`).
- `web-system-console`: ADDED "Sign-in Log Query" (console 登录日志 page under 安全, sibling
  of Operation Log Query).

## Impact

- **lumen-sev**: `framework` (ILogSignService/LogSignServiceImpl - additive methods only),
  `lumen-system` (LogController new method, V13/V14 migrations).
- **lumen-web**: `src/api/types.ts`, `src/api/logs.ts` (additive), `src/pages/system/logs/sign.tsx`
  (new), `src/router.tsx`, `src/routes/menu-paths.ts`.
- **Database**: `public.log_info`/`log_sign` rebuilt as partitioned parents in one
  transaction each (ACCESS EXCLUSIVE lock for the copy-swap; dev-scale row counts make this
  a millisecond window). PG 15 declarative partitioning.
- **No breaking API change**: the existing `GET /system/logs` contract is untouched; the
  sign endpoint is additive.
