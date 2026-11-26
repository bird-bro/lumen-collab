# Design: registration-order-status-dict-web

> Shared decisions live in `openspec/coordination/registration-order-status-dict.md`;
> this file holds the frontend-specific mechanics only.

## D1 — Type narrowing

`UsOrderStatus` (`src/api/types.ts`) becomes the six-name union
`'REGISTERED' | 'IN_EXAM' | 'IN_DIAGNOSIS' | 'COMPLETED' | 'PRINTED' | 'CANCELLED'`;
`src/api/us-orders.ts` comment updated. `tsc` drives the consumer fallout (every
removed member surfaces as a compile error — the page's `STATUS_LABELS` map, the
`BOOKED` hue branches, the wide filter options).

## D2 — The bridge and the resolution seam

`src/utils/dict-items.ts` gains `US_ORDER_STATUS_DICT_ID = 1031` (id-anchored,
dict-lookup-by-id ruling) beside the other catalog constants. The enum-name→code
bridge lives beside it as the recorded cross-stack contract:

```ts
export const US_ORDER_STATUS_BRIDGE: Record<UsOrderStatus, { code: number; fallback: string }>
// REGISTERED→103101 已登记 · IN_EXAM→103102 检查中 · IN_DIAGNOSIS→103103 诊断中
// COMPLETED→103104 已完成 · PRINTED→103105 已打印 · CANCELLED→103106 已取消
// (codes = the catalog-id-prefixed family, the user's 2026-11-26 correction;
//  the 100401 性别 anchor shape — NOT the draft's bare 1-6)
```

A resolver fetches `dictItemsById(1031)` once per session (the existing cache), maps
each chip status through its bridge code to the dict `value` (fallback = the bridge's
中文 label when the item is missing/disabled or the fetch failed) AND the dict `tag`
(the DB-configured color token, user ruling 2026-11-26), and orders the statuses by
the dict `sort`. The bridge constants are contract, not a vocabulary option set (the
Dict-Resolved Vocabularies carve-out in the delta).

## D3 — Page surfaces

- **Hue→token map** (the color rendering): `{ blue: colorPrimary/colorPrimaryBg,
  green: colorSuccess/colorSuccessBg, amber: colorWarning/colorWarningBg,
  red: colorError/colorErrorBg, gray: colorTextSecondary/colorFillSecondary }` —
  design tokens only, never ad-hoc hex (the house rule); an unknown/absent tag
  degrades to the neutral gray pair. One helper drives chips + pills.
- `RAIL_HEAD_STATUSES` → the six states; chips render in dict-1031 `sort` order with
  resolved labels + colors (default selection 已登记 unchanged; multi-select toggles
  unchanged).
- Status pills (compact `.us-ocard` rows, wide-table tags) resolve through the same
  seam — one helper, both surfaces.
- The wide status-filter options = the six states with dict labels.
- `LIVE_STATES` (cancel affordance gating) → `['REGISTERED', 'IN_EXAM', 'IN_DIAGNOSIS']`
  (the machine's cancelable set); `DEFAULT_STATUS_FILTER` stays `['REGISTERED']`.
- The `BOOKED` hue branch and `BOOKING_CANCELLED` references die with the union.

## D4 — Test seams

`us-registration-page.test.tsx`: the dict-1031 fetch joins the existing `vi.mock`
seam (`@/api/dicts` is already module-mocked for the other catalogs) — seed the mock
with the six 1031 items and assert chip labels/order/count rendering, the fallback
rendering on dict failure (one error message + bridge labels, meter intact), and the
cancel affordance appearing on exactly the three live states. jsdom picker idioms per
the house patterns (`.us-ocard` anchor).
