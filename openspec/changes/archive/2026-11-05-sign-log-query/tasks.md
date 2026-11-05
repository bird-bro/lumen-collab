# Tasks

## 1. Backend - partitioned log tables (V13)

- [x] 1.1 Write `lumen-system/src/main/resources/db/migration/V13__log_tables_partitioned.sql`
      per design D1-D3: one copy-swap transaction per table (ACCESS EXCLUSIVE lock → create
      `<name>_new` partitioned parent `RANGE (date_ym)` with composite PK `(id, date_ym)` →
      INSERT SELECT carry-over → drop old → rename → `ALTER SEQUENCE ... OWNED BY NONE/...id`
      + `setval(MAX(id))` → rename pkey/index names back), yearly children 2026/2027 +
      `log_info_def`/`log_sign_def` DEFAULT, partitioned `idx_*_date_ym`/`idx_*_uid`. Header
      comments document the default-partition detach caveat. Verify: backend boots against
      the dev PG; `\d+ public.log_info` / `\d+ public.log_sign` show RANGE (date_ym) with
      2026/2027/def children and `(id, date_ym)` primary keys; pre-existing row counts preserved.
- [x] 1.2 Regression: existing log suites stay green after the conversion -
      `./mvnw test -q` (AuthLogQueryTests, AuthSignLogTests, AuthOperationLogTests) passes,
      proving the write path and query path resolve the partitioned parents unchanged.

## 2. Backend - sign-log query endpoint

- [x] 2.1 RED: `AuthSignLogQueryTests` (extends `AuthIntegrationTestBase`, per-run markers) -
      `GET /api/v1/system/logs/sign` pages `log_sign` newest-first with `account`/`uid`/`dateYm`
      filters AND-ing, and no response row carries a `token` field. Verify: `./mvnw test -q`
      fails on the missing endpoint.
- [x] 2.2 GREEN: additive `ILogSignService.getPage(Integer pageNum, Integer pageSize, String
      account, Integer uid, Integer dateYm)` + impl (QueryWrapper, `deleted = 0`, select
      excludes `token`, `orderByDesc(actionTime, id)` - mirrors LogInfoServiceImpl.getPage);
      `LogController` gains `@GetMapping("/sign")` with method-level
      `@PreAuthorize("hasAuthority('system:signlog:list')")` and the shared MAX_PAGE_SIZE cap.
      Existing methods untouched. Verify: 2.1 green, `./mvnw compile -q` clean.
- [x] 2.3 Perm tests: extend the enforcement suite - a principal with only `system:log:list`
      gets forbidden on `/system/logs/sign`, and a principal with `system:signlog:list` passes
      even without `system:log:list` (method-level override pin, design D4). Verify:
      `./mvnw test -q` green.

## 3. Backend - menu seed (V14)

- [x] 3.1 Write `V14__sign_log_menu.sql`: menu row `(17, 16, 'C', '登录日志',
      'system:signlog:list', 'signlog', 'system/log/sign', NULL, 3)` under 安全 (16) + role_menu
      grant to role 1 + `setval` resync, V5/V8 idempotence discipline (explicit ids, guarded
      WHERE). **Conflict clause = ON CONFLICT (id) DO UPDATE ... deleted = 0 (revive semantics,
      b2-review Important #1)** - converges on fresh AND probe-polluted test DBs. Verify:
      suite green twice in a row (revive idempotent); menu 17 live on the test server.
- [x] 3.2 (b2-review follow-up) `AuthSignLogQueryTests.cleanupMenu` → HARD delete (role_menu
      rows first per FK, then menu_info) + one-time purge of the soft-deleted probe rows
      (`path LIKE 'b2signlogmenu_%'`) from the team test DB. Verify: suite green; polluted
      id set gone (before/after evidence).

## 4. Frontend - sign-in log page

- [x] 4.1 Types + client: `src/api/types.ts` adds `LogSignItem` (id/dateYm/msg/descr/uid/
      account/actionTime/mobile/ip/os/browser/engine/session/app - NO token) and
      `ListSignLogsQuery`; `src/api/logs.ts` adds `listSignLogs()` → `GET /system/logs/sign`
      (page/pageSize convention). Verify: `pnpm build` type-checks; api-client tests green.
- [x] 4.2 RED: `src/test/sign-logs-page.test.tsx` (vi.mock('@/api/logs'), mirror logs-page
      tests) - renders the audit columns, issues listSignLogs with the draft filters on query,
      read-only surface (no mutation controls). Verify: fails before the page exists.
- [x] 4.3 GREEN: `src/pages/system/logs/sign.tsx` - same console-shell chrome as
      `logs/index.tsx` (ConsolePanel/ConsoleToolRow/ConsoleTable/ConsolePagination), filters
      账号(Input)/操作人ID(InputNumber)/月份(InputNumber yyyyMM), columns 操作时间/事件/描述/
      账号/IP/操作系统/浏览器/入库时间. Verify: 4.2 green.
- [x] 4.4 Routing: `src/router.tsx` lazy SignLogsPage at `system/logs/sign`;
      `src/routes/menu-paths.ts` maps `'system/log/sign': '/system/logs/sign'`. Verify:
      `pnpm dev` against the real backend - the 安全 group renders both log entries and the
      page loads live rows (no mock data anywhere).

## 5. Change-level verification

- [x] 5.1 Walk every WHEN/THEN of both delta specs (system-data ×4, web-system-console ×3)
      against the running stack; record evidence in the change notes for /opsx:verify.

## Workflow follow-up

- `/opsx:verify` writes verify.md (L1 build / L2 spec walk / L3 tests) as the archive credential.
- User confirms the 登录日志 page against the REAL backend (backend-first ruling) before archive.
- `/opsx:archive` after verify PASS.
