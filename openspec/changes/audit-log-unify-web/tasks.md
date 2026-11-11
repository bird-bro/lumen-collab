# Tasks — audit-log-unify-web

> Frontend half of audit-log-unify. Precondition: `audit-log-unify-api` applied —
> `GET /system/logs` carries Integer type + account filter; `/sign` retired.
> Existing-page edit: UI-prototype gate skipped (frontend workflow).

## Batch 1 — Client convergence

- [x] 1.1 Red: `api-clients.test.ts` — `listLogs` sends numeric `type` + `account` in the query; sign-client tests removed
- [x] 1.2 `types.ts` (`LogItem.type: number`, drop `LogSignItem`) + `logs.ts` (query widened, `listSignLogs` deleted) — green

## Batch 2 — Unified page

- [x] 2.1 Red: logs-page test — 类别 Select from `useDictItems('审计日志类型')` sends numeric type; account filter wired; sign-fixture row renders dict label + — in 方法/URL; dict failure → raw-code fallback + one message.error; no mutation controls
- [x] 2.2 Rebuild `pages/system/logs/index.tsx` as 审计日志 (merged columns + filter bar; dict Select; em-dash cells) — green
- [x] 2.3 Delete `pages/system/logs/sign.tsx`, its test, the `/sign` route in `router.tsx`, and the `system/log/sign` menu-paths mapping

## Batch 3 — Verify

- [ ] 3.1 `pnpm test` + `pnpm build` green; smoke against the real backend (both event families visible; menu shows 审计日志 under 安全)
- [ ] 3.2 User UI confirmation → coordination registry checkmarks
