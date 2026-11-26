---
verify:
  change: us-order-exam-room-web
  date: 2026-11-26
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "17/17"
      L3: PASS
    backend:
      L1: SKIP
      L2: SKIP
      L3: SKIP
---

# Verification: us-order-exam-room-web

> Web-only half of the cross-stack pair (the backend half archived as
> 2026-11-26-us-order-exam-room-sev, commit 74eace9). Stacking resolved during
> verify: the rail-quick-search archive (b7d3d14) landed AFTER this delta was
> authored — its new Order List scenario joined this delta's MODIFIED block
> verbatim (the superset completion), and the main spec layering re-validated
> clean.

## lumen-web

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `pnpm build` (tsc --noEmit ×2 + vite build); exit=0; logs/build-lumen-web.log |
| L2 spec | 17/17 | all scenarios IMPLEMENTED (evidence below) |
| L3 tests | PASS | `pnpm test`; exit=0; logs/test-lumen-web.log — **534/534 (36 files)** |

## Scenario alignment

### web-us-station — Order Form and Actions (specs/web-us-station/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| The room is required and rides the payload | IMPLEMENTED | index.tsx:854 (the client-side gate `检查室不能为空` before anything posts); :905 (us-last-room written back on success); :1941 (the section-3 Select, standard control height); UsRegistrationRoomTests-equivalents in us-registration-page.test.tsx 检查室 describe (4 cases: refusal/pre-select+roomId/switch+persist/failure band) |
| Create posts identity, items, and extension (carried) | IMPLEMENTED | the suite's submit tests green (534/534); the room retrofits keep every submit path carrying roomId |
| Pre-registration time posts the slot anchor (carried) | IMPLEMENTED | the suite's pre-registration tests green |
| Item modality rides the create payload (carried) | IMPLEMENTED | the suite's modality test green |
| 登记 transitions the order — none exists (carried) | IMPLEMENTED | the suite's two-state tests green |
| Cancel requires a reason and confirmation (carried) | IMPLEMENTED | the suite's cancel tests green |
| Illegal transition surfaces verbatim (carried) | IMPLEMENTED | the suite's refusal tests green |

### web-us-station — Order List (specs/web-us-station/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| List renders with state and identity (now with the 检查室 renders) | IMPLEMENTED | index.tsx:1459 (the compact card's room on the exam-time meta line — no fifth row, the card-flow ruling); :1536 (the wide 检查室 column, ellipsis+title, `roomName ?? '—'`); us-registration-page.test.tsx 登记单检查室展示 describe (card line + column-indexed '—' band) |
| Status chips carry the meter and multi-select (carried, dict-1031) | IMPLEMENTED | the suite's chip tests green |
| The rail quick-search narrows the list and the meter (carried — superset completion) | IMPLEMENTED | the rail-quick-search tests green in the suite; the scenario carried verbatim into this delta at verify (the b7d3d14 stacking) |
| Wide conditions compose through the query row (carried) | IMPLEMENTED | the suite's wide-state tests green |
| 我的查询 persists and auto-applies the default (carried) | IMPLEMENTED | the suite's saved-search tests green |

### web-us-station — Booking Slot Dialog (specs/web-us-station/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Confirm requires slot AND room | IMPLEMENTED | index.tsx:2338 (`disabled={!dlgSlot \|\| roomId == null}`); :2325 (the footer Select, same source + last-choice default); us-registration-page.test.tsx 预约弹窗检查室 describe (gating / bookingTime+roomId 组合提交 / last-choice persistence) |
| The dialog opens on tomorrow with the grouped grid (carried) | IMPLEMENTED | the suite's dialog tests green |
| The full slot is grayed and the tight slot emphasized (carried) | IMPLEMENTED | the suite's slot tests green |
| Confirming a slot writes the date+slot pair (carried) | IMPLEMENTED | the suite's confirm tests green |
| Blank or cleared keeps the walk-in path (+ the room choice SURVIVES the clear) | IMPLEMENTED | us-registration-page.test.tsx (the clearing test: the × affordance gone, the room selection item persists, bookingTime null + roomId posted) |
| Unconfigured or failed lookup degrades without blocking (carried) | IMPLEMENTED | the suite's degradation tests green |

## Issues

None blocking. Notes:

- Two verify-stage stacking repairs (both artifact-level, no source change): the
  delta's Order List MODIFIED block gained the rail-quick-search scenario verbatim
  (b7d3d14 landed after authoring — the archive would otherwise drop it), and the
  orderView test helper gained the `roomId/roomName` null defaults (the type now
  requires them).
- jsdom ghost idioms (the closing animation / the non-unmounting Modal content)
  resolved with the suite's established patterns; noted in the progress ledger.

Overall: **PASS** — ready for `/opsx:archive us-order-exam-room-web`.
