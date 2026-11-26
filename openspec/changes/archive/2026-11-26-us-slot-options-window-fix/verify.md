<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-slot-options-window-fix
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "8/8"
      L3: PASS
---

# Verification: us-slot-options-window-fix

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 8/8 | PASS | logs/build-backend.log, logs/test-backend.log, logs/test-backend-rerun.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| The configured grid carries live occupancy | IMPLEMENTED | UsRegistrationApiTests.java:280 (regression guard, green) + the one-span bucket RegistrationOrderServiceImpl.java:335 (abs-minute floor = the create's count predicate) |
| Unconfigured modality fails soft | IMPLEMENTED | RegistrationOrderServiceImpl.java:268 (configured=false branch, first in the method) |
| Invalid date refuses | IMPLEMENTED | RegistrationOrderServiceImpl.java:259 (A0400 预约日期格式无效) |
| Wrap-around granularity cannot pin the enumeration | IMPLEMENTED | day-minute ints + TreeSet loop RegistrationOrderServiceImpl.java:298-306 (structural termination); UsRegistrationApiTests.java:333 (g=1440 → 0 slots, no hang — the 1.1a red was a 180s hang) |
| Off-grid windows offer exactly the bookable grid points | IMPLEMENTED | midnight-grid ceil RegistrationOrderServiceImpl.java:301 + the cell [t,t+g) create-acceptance proof UsRegistrationApiTests.java:354 (create at 08:15 lands in the picker's cell, booked 1→2) |
| Malformed windows dead-end the enumeration | IMPLEMENTED | strict parse + break RegistrationOrderServiceImpl.java:286-288 (lazy dead-end — valid windows before it stay offered); UsRegistrationApiTests.java:409 ([valid→malformed] offers 8) + :429 ([malformed-first] configured=true, empty) |
| A past date offers nothing | IMPLEMENTED | selectDbNow strictly-after filter RegistrationOrderServiceImpl.java:342-348 (mirrors requireFutureBooking's `after`); UsRegistrationApiTests.java:452 |
| Cross-midnight cells count their tails | IMPLEMENTED | absolute-minute bucketing + maxCellEnd-extended span RegistrationOrderServiceImpl.java:329-341; UsRegistrationApiTests.java:497 (g=50, next-day 00:02-00:08 → 23:20 cell booked=3) |

Plus the review-round-2 hardening (sorted/deduped union + empty-before-probe):
UsRegistrationApiTests.java:472 (out-of-order + overlapping windows → 20 cells
ascending, no duplicate keys).

## L3 Notes (rerun)

- Full-reactor run: lumen-us **83/83** ✓ + lumen-core **63/63** ✓; lumen-system
  320/322 with 2 ERRORS = transient shared-DB connection drops
  (`PSQLException 尝试连线已失败 / EOF` — the documented 公网断连 family, zero
  overlap with this diff).
- Solo rerun of the two classes: AuthOrgDepTests 8/8 + NumberRuleApiTests 16/16
  green (exit 0, fresh 10:47 reports). L3 recorded PASS.
- Two review rounds ran during apply (4 findings → 2c rewind → corrections;
  3 findings → sort/dedup + revert + early return); the kernel validation path
  is byte-identical to its pre-change state.
