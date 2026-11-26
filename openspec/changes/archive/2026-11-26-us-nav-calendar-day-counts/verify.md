---
verify:
  change: us-nav-calendar-day-counts
  date: 2026-11-26
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "4/4"
      L3: PASS
---

# Verification: us-nav-calendar-day-counts

## lumen-web

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| L2 spec | 4/4 | all scenarios IMPLEMENTED (evidence below) |
| L3 tests | PASS | `pnpm test`; exit=0; logs/test-lumen-web.log — **537/537 (36 files)**; the first full run hit the recorded vitest-popconfirm full-run tooltip-duplicate flake (exam-body-page delete test) — isolated rerun 14/14 green + this clean full rerun, per the flake family's procedure |

## Scenario alignment

### web-us-station — Registration Page Shell (specs/web-us-station/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| The datenav popover calendar carries per-day counts | IMPLEMENTED | index.tsx:382-404 (the prefetch effect: one `pageOrders` per displayed-month day, `pageSize: 1` + the day's exact range bounds, `Promise.allSettled` — a failed day answers no badge); index.tsx:1218-1246 (the badge render: count > 0 in the primary tint under the day number, the selected cell reads white-on-primary, cells 26 → 34); us-registration-page.test.tsx 日期弹层日历 describe (the per-day call's range bounds + pageSize asserted; the counted day renders `.nav-count-badge` = 3; zero days bare; the pick jumps the rail) |
| Page renders under the activated US workstation (carried) | IMPLEMENTED | the suite's shell tests green |
| Actions hide without perms (carried) | IMPLEMENTED | the suite's perm tests green |
| The edge toggle switches the two states (carried) | IMPLEMENTED | the suite's toggle tests green |

## Issues

None blocking. Notes:

- The popover-close animation is NOT asserted (jsdom never completes rc-motion — the
  overlay keeps its visible band; the house convention asserts behavior, not
  animation — the onClick sets `datePopOpen(false)` beside the jump).
- The prefetch runs only while the popover is open (the effect gates on
  `datePopOpen`), so the rail's own load cycle is unaffected.

Overall: **PASS** — ready for `/opsx:archive us-nav-calendar-day-counts`.
