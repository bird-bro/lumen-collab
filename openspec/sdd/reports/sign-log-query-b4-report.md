# Batch 4 Report - sign-log-query / web sign-in log page (tasks 4.1-4.4)

Status: **DONE** (strict TDD RED -> GREEN -> wire; full suite + build clean)

## Evidence

### RED (task 4.2, before any implementation)

`src/test/sign-logs-page.test.tsx` was written FIRST and run against the tree with
no page module:

```
pnpm vitest run src/test/sign-logs-page.test.tsx
Error: Failed to resolve import "@/pages/system/logs/sign" from "src/test/sign-logs-page.test.tsx". Does the file exist?
 Test Files  1 failed (1)
      Tests  no tests
```

Baseline before the batch: `pnpm test` -> **20 files / 159 tests, all green**.

### GREEN (tasks 4.1 + 4.3)

After adding `LogSignItem`/`ListSignLogsQuery`, `listSignLogs()`, and
`src/pages/system/logs/sign.tsx`:

```
pnpm vitest run src/test/sign-logs-page.test.tsx src/test/api-clients.test.ts
 Test Files  2 passed (2)
      Tests  35 passed (35)
```

### Full verification (task 4.4 wired)

```
pnpm test   -> Test Files 21 passed (21) / Tests 167 passed (167)   [159 baseline + 6 page + 2 client]
pnpm build  -> tsc + vite build clean; new lazy chunk dist/assets/pages/system/sign-BQFXhte6.js
```

(Re-run post-commit with identical results; the >500 kB main-chunk warning is
pre-existing, unrelated to this batch.)

## Discovery: the global-gitignore `logs` trap (fix commit f04f29f)

While staging, `git add` refused `src/pages/system/logs/sign.tsx`: the USER'S
GLOBAL gitignore (`~/.gitignore_global`) carries a bare `logs` entry that
excludes every `logs/` directory repo-wide. Audit of the blast radius: batch 6b
had committed the logs page's TEST (`logs-page.test.tsx`), the router import,
and `api/logs.ts` - but `src/pages/system/logs/index.tsx` itself was silently
never tracked, so **a fresh clone of dev could not build before this batch**
(the committed router + test import the missing module). Rescue follows the
repo's own precedent (root `.gitignore` `!docs/deployment/*.sql` for the same
class of machine-global-ignore casualty): a scoped negation in
`lumen-web/.gitignore` re-includes the page directory and contents, and the
orphaned batch-6b `index.tsx` (content unchanged - it is the exact file the
committed tests run against) is now tracked in fix commit `f04f29f`, committed
BEFORE the b4 feature commit so the tree is buildable at every new commit.
Recommendation for the user: consider removing the bare `logs` line from
`~/.gitignore_global` (it is a Java-server logging artifact pattern, not a web
source pattern) - the scoped negation covers this one directory only.

## Files changed (all additive; no existing component behavior touched)

- `lumen-web/src/api/types.ts` - +`LogSignItem` (id/dateYm/msg/descr/uid/account/actionTime/
  mobile/ip/os/browser/engine/session/app + optional `type`/`deleted`/`createTime` per the
  brief; **no `token` field** - design D7), +`ListSignLogsQuery` type alias
  (page/pageSize/account/uid/dateYm; alias not interface, for the transport client's
  Record-typed query option, same as `ListLogsQuery`).
- `lumen-web/src/api/logs.ts` - +`listSignLogs()` -> `GET /system/logs/sign`, page/pageSize
  convention; existing `listLogs` untouched.
- `lumen-web/src/test/sign-logs-page.test.tsx` - NEW, mirrors `logs-page.test.tsx`:
  `vi.mock('@/api/logs')` stubs at the api seam only; asserts the 8 audit columns
  (操作时间/事件/描述/账号/IP/操作系统/浏览器/入库时间) render newest-first, filters
  account/uid/dateYm flow into `listSignLogs` on 查询, reset re-queries, server-side paging
  (page key), read-only surface (no 新建/删除, no 操作 column), and the empty-perms
  read-hides-nothing case.
- `lumen-web/src/test/api-clients.test.ts` - +`sign-logs client` describe block (additive):
  URL/method/query serialization + IPage unwrap + `not.toHaveProperty('token')` payload pin.
- `lumen-web/src/pages/system/logs/sign.tsx` - NEW page mirroring `logs/index.tsx` chrome
  exactly (ConsolePanel/ConsoleToolRow/ConsoleQueryButton/ConsoleTable/ConsolePagination,
  the explicit-reload-on-page-1 trick, read-only comment header, `usePerm()` no-op gate for
  future write affordances, ApiError-aware message fallback 登录日志加载失败). Filters:
  账号(Input) / 操作人ID(InputNumber) / 月份 yyyyMM(InputNumber). No mutation controls
  anywhere.
- `lumen-web/src/router.tsx` - +lazy `SignLogsPage` at `system/logs/sign` (+ doc-comment
  enumeration touch-up).
- `lumen-web/src/routes/menu-paths.ts` - +`'system/log/sign': '/system/logs/sign'` next to
  the existing `system/log/index` line (+ doc-comment touch-up).
- `lumen-web/.gitignore` (+ fix commit f04f29f) - scoped negation rescuing
  `src/pages/system/logs/` from the machine-global `logs` ignore; see the Discovery section.

## Commits (dev, staged file-by-file; concurrent batches' files untouched)

- `f04f29f` - 🐛 fix(web): track logs page index.tsx lost to the machine-global `logs`
  ignore (lumen-web/.gitignore negation + the orphaned batch-6b index.tsx, content
  unchanged).
- `afa3a50` - ✨ feat(web): sign-in log page at /system/logs/sign (sign-log-query b4) -
  the 7 b4 files listed above.

## Self-review

- TDD iron law honored: test first, RED observed (module-not-found), then implementation;
  no test was edited after GREEN.
- Zero token leakage: `grep token` over `src/api/types.ts`/`logs.ts` shows only the
  pre-existing auth `LoginData.token` and prose comments; `LogSignItem` carries no token
  field and the client test pins the payload absence.
- Backend-first: no mock layer, no mock server; tests stub `@/api/logs` at the seam only.
  The live dev-server check against the REAL backend (安全 group shows both log entries,
  page loads live rows) is deferred to the verify stage per the brief - the endpoint is
  being built in the parallel backend batch (b2) and the menu seed in b3 (V14), so menu
  visibility for `system:signlog:list` cannot be exercised until those land.
- Concurrent-batch hygiene: staged file-by-file per commit (7 b4 files for `afa3a50`,
  2 rescue files for `f04f29f`); the working tree also
  carries another batch's edits (`src/layouts/BasicLayout.tsx`, `src/layouts/basic-layout.css`,
  `src/test/layout.test.tsx`) and shared ledger files (`openspec/loop-state.yaml`,
  `openspec/sdd/progress.md`) which were left untouched and unstaged. `tasks.md` checkbox
  ticking left to the orchestrator (untracked shared file, concurrent writers).
- Hex discipline / no-dev-mock guard tests pass over the new files (no hex literals, no
  mock plumbing).

## Interpretation decisions (for review)

- Brief: "payload carries `type`, `createTime`, `deleted` too - model them (optional)":
  modeled all three optional (`type?: number | null`, `deleted?: number`, `createTime?:
  string`); the 入库时间 column renders a `—` fallback, so an absent field degrades safely.
  `type` left uninterpreted by the UI (design D5 - `msg` carries the outcome in prose).
- Added 2 client-level tests for `listSignLogs` beyond the minimum (task 4.1 verify says
  "api-client tests green") to pin path/query/unwrap/token-absence at the transport seam,
  mirroring how `listLogs` was introduced.

## Concerns

- None blocking. The 4.4 "pnpm dev against the real backend" verification and the spec-walk
  (task 5.1, including menu-driven visibility of `system:signlog:list`) wait on backend
  b2/b3 landing - recorded here for the verify stage.
