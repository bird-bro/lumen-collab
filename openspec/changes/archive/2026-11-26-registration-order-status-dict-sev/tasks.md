# Tasks

## 1. Kernel six-state machine (TDD)

- [x] 1.1 Rewrite the state-machine tests to the six-state contract FIRST
  (`lumen-core` tests): happy path 已登记→检查中→诊断中→已完成→已打印, forward-permissive
  edges (诊断抢占 from 检查中 AND from 已登记), cancel lands `CANCELLED` from the three
  live states and refuses on the issued report, illegal rejections (backward moves,
  exits from `PRINTED`, the removed 已预约 vocabulary) — verify:
  `./mvnw test -q -pl lumen-core -Dtest='*StateMachine*'` exits non-zero (red, report
  counts + log path).
- [x] 1.2 Collapse `ExamOrderStatus` to the six states (javadoc carries the trigger
  annotations + the 2026-11-26 ruling) and rewrite the `EDGES` table per design D1 —
  verify: the 1.1 tests pass (green) and `./mvnw compile -q` exits 0 (the removed
  constants' compile fallout surfaces here, not silently).
- [x] 1.3 Fix the fallout consumers: the cancel fork in `RegistrationOrderServiceImpl`
  collapses to always `CANCELLED`; `notIn(BOOKING_CANCELLED, CANCELLED)` narrows to
  `notIn(CANCELLED)` in `RegistrationOrderServiceImpl` (2 sites) +
  `PatientSnapshotPoller` (2 sites) — verify: `./mvnw test -q -pl lumen-us -am` exits 0
  (counts + log path).

## 2. Removed surface + migrations

- [x] 2.1 Remove the 登记 transition endpoint (controller route + its service method +
  its tests; cancel/count endpoints untouched) — verify:
  `./mvnw test -q -pl lumen-us` exits 0 and the route is gone from the controller.
- [x] 2.2 Add `V55__registration_status_dict.sql` to the lumen-system ledger (design
  D4, the V52 shape: 1031 catalog + 1031001–1031006 details, ON CONFLICT + setval) and
  extend the dict API tests to cover the new catalog (items-by-id 1031 returns the six
  in sort order) — verify:
  `./mvnw test -q -pl lumen-system -Dtest='*Dict*'` exits 0.
- [x] 2.3 Add `V56__exam_order_status_six_states.sql` to the lumen-us ledger (design
  D5's three idempotent remaps) — verify: the lumen-us suite applies it on the shared
  DB without error (`./mvnw test -q -pl lumen-us` exit 0; a replayed run stays
  error-free).

## 2b. Color tag (mid-apply scope, user ruling 2026-11-26)

- [x] 2.4 Extend the dict lookup test FIRST: the 1031 items serialize the third
  `tag` key (blue/amber/amber/green/green/gray in sort order) while an untagged
  catalog (1028) keeps the strict two-key shape — verify: the dict tests exit
  non-zero (red).
- [x] 2.5 Add `tag` to `DictItemDto` (`@JsonInclude(NON_NULL)`, javadoc amendment
  per design D8) and map it in both projections
  (`listEnabledItemsByName` / `listEnabledItemsByCaId`); fold the tag seeds into V55
  and replay it through the sanctioned path (drop the history row, re-apply — ON
  CONFLICT keeps the operator-corrected codes) — verify:
  `./mvnw test -q -pl lumen-system -Dtest='AuthDictItemsLookupTests'` exits 0 (green).

## 3. Change-scoped regression

- [x] 3.1 Run the full backend suite once green — verify: `./mvnw test -q` exit 0
  (counts + log path reported); confirm no test fixture still asserts the removed
  statuses (`grep -rn "BOOKED\|BOOKING_CANCELLED\|IN_PROGRESS" lumen-sev/*/src/test`
  comes back empty or annotation-explained).
