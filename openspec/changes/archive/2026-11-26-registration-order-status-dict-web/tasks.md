# Tasks

> Build against the REAL backend AFTER `registration-order-status-dict-sev` lands
> (backend-first ruling 2026-11-05; no mock layer at runtime — tests stub the API
> seam only).

## 1. Type + bridge (TDD)

- [x] 1.1 Narrow `UsOrderStatus` in `src/api/us-orders.ts` (the self-contained module the type actually lives in - the task text guessed types.ts from the proposal Impact) to the six enum names and
  update the `src/api/us-orders.ts` comment — verify: `pnpm build` (or `tsc -p .`)
  FAILS with the page fallout listed (red — the union change drives 2.1/2.2).
- [x] 1.2 Add `US_ORDER_STATUS_DICT_ID = 1031` and the `US_ORDER_STATUS_BRIDGE` map
  (code + fallback label per state; codes = the catalog-id-prefixed family
  103101–103106 per the user's 2026-11-26 correction) to `src/utils/dict-items.ts`,
  plus the session-cached resolver (dict `value` labels + `tag` colors, `sort`
  ordering, bridge fallback) — verify: unit assertions on the resolver against the
  fetch-level seam pass (labels, hues, ordering, fallbacks).

## 2. Page surfaces

- [x] 2.1 Update `us-registration-page.test.tsx` FIRST: seed the dict mock with the
  six 1031 items; assert the six chips in sort order with dict labels, default
  selection 已登记, status-pill labels via the same seam, cancel affordance on exactly
  已登记/检查中/诊断中 rows, and the failure path (one error message + bridge fallback
  labels, meter intact) — verify: the new assertions fail against the current page
  (red).
- [x] 2.2 Rework the page: `RAIL_HEAD_STATUSES` → six states in dict order, chips +
  compact/wide status pills + wide filter options through the resolver with the
  DB-configured colors (the hue→token map per design D3 — chips, card pills, wide
  tags), `LIVE_STATES` → the three live states, remove the `BOOKED` hue branch and
  the hard-coded `STATUS_LABELS` map — verify:
  `pnpm vitest run src/test/us-registration-page.test.tsx` exits 0 (green; counts +
  log path).

## 3. Change-scoped regression

- [x] 3.1 Run the full frontend suite + build once green — verify: `pnpm vitest run`
  exit 0 and `pnpm build` exit 0 (counts + log path reported); confirm no non-test
  source still references the removed statuses
  (`grep -rn "BOOKED\|BOOKING_CANCELLED\|IN_PROGRESS" src --include='*.ts*` | grep -v
  test` comes back empty or annotation-explained).
