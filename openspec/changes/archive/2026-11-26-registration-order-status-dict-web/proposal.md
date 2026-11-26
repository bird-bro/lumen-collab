# Proposal: registration-order-status-dict-web

> Cross-stack feature `registration-order-status-dict` — frontend half. The backend
> half is `registration-order-status-dict-sev`; shared decisions (the six statuses,
> the dict-1031 bridge contract) live in
> `openspec/coordination/registration-order-status-dict.md`.

## Why

The US registration desk's status chips carry hard-coded `STATUS_LABELS` over the
retired six-state enum (`BOOKED`…`BOOKING_CANCELLED`), which no longer matches the
kernel's six-state machine (user ruling 2026-11-26: 已登记/检查中/诊断中/已完成/已打印/
已取消), and the labels are not operator-maintainable — every other desk vocabulary
moved into the dict system (患者来源 1023, 妊娠状态 1002).

## What Changes

- `UsOrderStatus` union narrows to the six backend enum names:
  `REGISTERED` / `IN_EXAM` / `IN_DIAGNOSIS` / `COMPLETED` / `PRINTED` / `CANCELLED`
  (**BREAKING** for the status string family — rides the backend wire break).
- The rail-head chips render dict-1031 labels AND colors through the id-anchored
  session-cached lookup (`US_ORDER_STATUS_DICT_ID = 1031` + the enum-name→code bridge,
  the catalog-id-prefixed family 103101–103106), chip order following the dict `sort`,
  the DB-configured `tag` color mapping through the design-token hue map; the
  hard-coded `STATUS_LABELS` map is retired.
- Chip set = exactly the six statuses (no zero-rendering `BOOKED` chip — the state no
  longer exists); the default selection stays 已登记; multi-select-toggle semantics
  unchanged.
- Status pills (compact card rows, wide table tags) resolve labels through the same
  dict lookup with the bridge's 中文 labels as the failure fallback (the desk meter
  must survive a dict outage — a deliberate deviation from the form vocabularies'
  empty-options degrade, recorded in the coordination doc).
- `LIVE_STATES` and the cancel affordance follow the machine: cancelable states are
  已登记/检查中/诊断中; 已完成/已打印/已取消 rows render stateless.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-us-station`: Order List — the chips clause (dict-1031 labels, the six states,
  no 预登记 zero chip) and the state-tag rendering; Registration Workbench
  Dict-Resolved Vocabularies — the status-label catalog 1031 joins the resolved
  vocabularies with its own failure semantics.

## Impact

- `src/api/types.ts`: `UsOrderStatus` union narrowed.
- `src/pages/us/registration/index.tsx`: chips, status pills, `RAIL_HEAD_STATUSES`,
  `LIVE_STATES`, wide status filter options, dict-1031 constant + bridge.
- `src/utils/dict-items.ts`: the `US_ORDER_STATUS_DICT_ID` constant (the bridge map
  lives beside the page's other dict constants per the existing pattern).
- Backend dependency: the sev change's six-state enum and `/orders/status-counts`
  keys — the web builds against the REAL backend after the sev change lands.
