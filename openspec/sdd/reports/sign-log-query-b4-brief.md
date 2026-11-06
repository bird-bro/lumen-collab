# Batch 4 Brief - sign-log-query / web sign-in log page (tasks 4.1-4.4, TDD)

## Task text (openspec/changes/sign-log-query/tasks.md)

- 4.1 Types + client: `src/api/types.ts` adds `LogSignItem` (id/dateYm/msg/descr/uid/account/
  actionTime/mobile/ip/os/browser/engine/session/app - NO token) and `ListSignLogsQuery`;
  `src/api/logs.ts` adds `listSignLogs()` → `GET /system/logs/sign` (page/pageSize convention).
  Verify: `pnpm build` type-checks; api-client tests green.
- 4.2 RED: `src/test/sign-logs-page.test.tsx` (vi.mock('@/api/logs'), mirror logs-page tests) -
  renders the audit columns, issues listSignLogs with the draft filters on query, read-only
  surface (no mutation controls). Verify: fails before the page exists.
- 4.3 GREEN: `src/pages/system/logs/sign.tsx` - same console-shell chrome as
  `logs/index.tsx` (ConsolePanel/ConsoleToolRow/ConsoleTable/ConsolePagination), filters
  账号(Input)/操作人ID(InputNumber)/月份(InputNumber yyyyMM), columns 操作时间/事件/描述/
  账号/IP/操作系统/浏览器/入库时间. Verify: 4.2 green.
- 4.4 Routing: `src/router.tsx` lazy SignLogsPage at `system/logs/sign`; menu-paths.ts maps
  `'system/log/sign': '/system/logs/sign'`. Verify: pnpm build + tests green.

## Read first

1. `openspec/changes/sign-log-query/design.md` - D6 (two pages not tabs), D7 (token excluded)
2. `openspec/changes/sign-log-query/specs/web-system-console/spec.md` - the page contract
3. `lumen-web/CLAUDE.md` - backend-first (NO mock layer; page requests go through the real
   client; tests stub at the `@/api/...` seam only), `usePerm` usage, AntD 5 first
4. `src/pages/system/logs/index.tsx` - the chrome/pattern to mirror (filters state machine,
   ConsoleQueryButton explicit-reload trick, read-only comment header style)
5. `src/test/logs-page.test.tsx` - the test pattern to mirror
6. `src/api/types.ts` (LogItem/Page), `src/api/logs.ts`, `src/router.tsx`, `src/routes/menu-paths.ts`

## Implementation notes

- `LogSignItem` type: the endpoint serializes the copied `LogSignEntity`, so the payload
  carries `type`, `createTime`, `deleted` too - model them (optional) alongside the task list;
  `token` MUST NOT appear in the type. `ListSignLogsQuery`: page/pageSize/account/uid/dateYm.
- Columns (spec): 操作时间(actionTime)/事件(msg)/描述(descr)/账号(account)/IP(ip)/
  操作系统(os)/浏览器(browser)/入库时间(createTime). Match the logs page's render conventions
  (tabular-nums timestamps, secondary URLs, `—` fallbacks).
- Read-only surface: no mutation controls anywhere; comment header like the logs page.
- Route: `/system/logs/sign`, lazy import; menu-paths.ts adds the component-key mapping
  `'system/log/sign': '/system/logs/sign'` next to the existing `'system/log/index'` line.
- New page file = new page (no edits to existing components; compose console-shell).

## Environment / commands

```bash
cd lumen-web && pnpm test    # Vitest
pnpm build                   # tsc + vite build
```

The backend endpoint is being built in a parallel batch - do NOT wait for it and do NOT point
anything at a mock server. Unit tests stub `@/api/logs` at the seam; the live dev-server check
against the real backend happens at the verify stage (record that in the report).

## TDD discipline (iron law)

1. Write `sign-logs-page.test.tsx` FIRST (4.2 assertions: columns render, filters flow into
   listSignLogs on query, no mutation controls) → run → confirm RED (page missing)
2. Implement 4.1 + 4.3 → GREEN
3. Wire 4.4 (router + menu-paths) → `pnpm build` + full `pnpm test` green (155+ existing
   cases stay green)

## Report + commit

- Full report: `openspec/sdd/reports/sign-log-query-b4-report.md`
  (RED evidence, GREEN evidence, files changed, self-review, concerns)
- Commit on dev, style per `git log --oneline -8` (emoji prefix), mention sign-log-query b4.
  Note: other batches are committing in `lumen-sev/` concurrently - stage ONLY your web files.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha.
