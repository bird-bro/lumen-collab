# Proposal: audit-log-unify-web

## Why

The console splits one audit trail across two pages (操作日志 + 登录日志) in two menu
groups. The backend sibling `audit-log-unify-api` collapses them to one table, one
endpoint (`GET /system/logs`, Integer `type` codes, `account` filter), one permission
(`system:log:list`), and retires `GET /system/logs/sign` — the frontend must converge
to the same single-page shape (审计日志) against the REAL backend (no mock layer,
user ruling 2026-11-05).

Cross-stack feature — coordination doc: `openspec/coordination/audit-log-unify.md`.
Sibling change: `audit-log-unify-api` (lands first; this change builds against it).

## What Changes

- `pages/system/logs/index.tsx` + `pages/system/logs/sign.tsx` merge into ONE
  审计日志 page: single table, newest-first, with a 类别 column and dict-driven
  type Select filter (audit type codes 1–5 + 10/11/12); sign-family rows render —
  for the method/URL columns, operation rows render — for the IP-tail columns
  they lack... (both families share ip/os/browser; only method/url are
  operation-only and msg-only columns unify into 描述/标题).
- `api/logs.ts` + `api/types.ts`: `listSignLogs`/`LogSignItem` removed;
  `listLogs` gains `account` filter, `type` becomes number; `LogItem.session`
  never appears in the payload (server-excluded) — the typed client reflects it.
- Type labels resolve via the existing `useDictItems('审计日志类型')` hook
  (role-type-dict pattern) with raw-code fallback — never blocks the page.
- Router: `/system/logs/sign` route removed; menu-paths mapping drops the
  `signlog` entry (menu 17 retired backend-side).
- Existing-page edit — per the frontend workflow this SKIPS the UI-prototype
  gate (existing pages, no new page).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-system-console`: the two log-page requirements (Operation Log Query +
  Sign-in Log Query) merge into one 审计日志 requirement — single route
  `/system/logs` in the 安全 group, dict-driven 类别 filter, read-only surface
  preserved, visibility follows `system:log:list`.

## Impact

- **Code**: `src/pages/system/logs/` (merge), `src/api/logs.ts`/`types.ts`
  (client convergence), `src/router.tsx` (route removal), `src/routes/menu-paths.ts`
  (mapping drop); tests `api-clients.test.ts`, `sign-logs-page.test.tsx`,
  logs-page test converge into the unified page test.
- **API dependency**: builds against the merged backend (`audit-log-unify-api`
  applied first) — `/sign` gone, `/logs` widened.
- **Specs**: web-system-console delta (one requirement swap).
