# Tasks: us-prereg-as-registered-sev

> Build from `lumen-sev/` with `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`; reactor only. Redirect output to a file, report exit code + counts.

## 1. Kernel: column + DDL source

- [x] 1.1 Add `ExamRegistrationType` enum (`WALK_IN`, `PRE_REGISTERED`) + the `registrationType` field to kernel `ExamOrder` (the `channel`/`ExamChannel` precedent); verify `./mvnw -pl lumen-core -am compile -q` exits 0
- [x] 1.2 Add kernel `db/kernel/V3__exam_order_registration_type.sql` (ADD COLUMN IF NOT EXISTS `registration_type` varchar(16) NOT NULL DEFAULT 'WALK_IN', no-semicolon comments, V4 house style) and verify the kernel test ledger applies it: run the kernel flow tests, exit 0
- [x] 1.3 Kernel state-machine test guard: a test pinning that the EDGES map and `transition()` are byte-unchanged by this change (existing tests suffice — run them, exit 0)

## 2. Us ledger + create-path stamps (TDD)

- [x] 2.1 RED: extend `UsRegistrationApiTests` — a walk-in create persists `registrationType=WALK_IN` + status REGISTERED; a future-slot create persists `registrationType=PRE_REGISTERED` + status REGISTERED (not BOOKED); the create response echoes `registrationType`; run, watch them fail
- [x] 2.2 Vendor the kernel V3 as us `V6__exam_order_registration_type.sql` (same statements, us-ledger header) and verify the us migration tests apply it, exit 0
- [x] 2.3 GREEN: stamp the two branches in `RegistrationOrderServiceImpl` (walk-in → WALK_IN, future-slot → PRE_REGISTERED, both `setStatus(REGISTERED)` — `PreRegistrationPolicy.deriveEntryStatus` drops out of the flow, `requireFutureBooking` stays); re-run 2.1, green
- [x] 2.4 Guard: a test posting `registrationType` in the create body asserts the stamp is ignored (server-owned), green

## 3. Occupancy re-anchor (TDD)

- [x] 3.1 RED: extend the slot tests — a REGISTERED pre-registered order (and a legacy BOOKED one) occupies its window; a CANCELLED one frees it; the `slotOptions` picker booked counts mirror the same predicate; run, watch them fail
- [x] 3.2 GREEN: swap both count predicates (`registerPreRegistration` occupancy + `slotOptions`) from `.eq(status, BOOKED)` to `.notIn(status, BOOKING_CANCELLED, CANCELLED)`; re-run 3.1 + the capacity-refusal tests, green

## 4. Cancel + view exposure (TDD)

- [x] 4.1 RED: a test cancelling a pre-registered order (REGISTERED) lands `CANCELLED`; run, watch it fail if the current fixture creates BOOKED, then update the fixture to the new entry semantics and assert the green path
- [x] 4.2 RED→GREEN: `RegistrationOrderView` gains `registrationType` + `createBy`; `RegistrationCreateResult` gains `registrationType`; `toView` maps them; a page-query test asserts the fields on rows, green
- [x] 4.3 Run the FULL us suite (`./mvnw -pl lumen-us -am test -q`), exit 0 — no regression in the ext/apply-info or slot-picker tests building on the same files

## 5. Verification hooks

- [x] 5.1 `openspec validate us-prereg-as-registered-sev` passes (deltas parse, superset rule holds)
