# Tasks: kernel-pre-registration

> TDD house rule: each behavior task goes RED (failing test on the test-only `core`
> schema) → GREEN (minimum kernel code) → refactor. Kernel-only change — no controller,
> no contract, no web surface in this proposal.

## 1. Kernel DDL source + entity

- [x] 1.1 Write copy-from source `db/kernel/V2__exam_kernel_pre_registration.sql`:
      `ALTER TABLE exam_order ADD COLUMN booking_time timestamp;` + `CREATE TABLE
      exam_slot_config` (modality unique, granularity_minutes default 30, slot_capacity
      default 4, open_windows jsonb default '[]', house audit columns) + unique index +
      GIN on open_windows (design D3/D6; comment lines carry no semicolons — V36 lesson)
- [x] 1.2 Kernel test harness applies V1+V2 to the test-only `core` schema (RED first:
      assert `exam_order.booking_time` and `exam_slot_config` exist after both sources)
- [x] 1.3 `ExamOrder` entity gains `bookingTime` field (Lombok combo preserved —
      @Data @Builder @AllArgsConstructor @NoArgsConstructor; MyBatis-Plus mapping)
- [x] 1.4 `ExamSlotConfig` entity + mapper (per-modality row, jsonb `open_windows` with a
      KernelJsonbTypeHandler-style handler; house EntityBase audit columns)

## 2. Pre-registration policy + capacity service

- [x] 2.1 RED: `PreRegistrationPolicy.deriveEntryStatus` — NULL/now/past → REGISTERED,
      future → BOOKED (design D1/D4; DB-server-time boundary)
- [x] 2.2 GREEN: policy component (pure time math, unit-tested without DB)
- [x] 2.3 RED: `ExamSlotCapacityService.resolveSlotWindow(config, bookingTime)` — floors
      time-of-day onto the granularity grid inside a window; outside every open window →
      refuse A0400 (不在开台时间窗内); empty/unparseable windows → refuse fail-closed
- [x] 2.4 RED: `validatePreregistration(modality, bookingTime, occupancyCount)` — missing
      config → refuse (该检查类型未开通预登记, fail-closed); count ≥ capacity → refuse
      (该时段已满); below → pass with the resolved window bounds handed back
- [x] 2.5 GREEN: capacity service impl (window math + refusals; occupancy count stays the
      caller's input — design D2)
- [x] 2.6 Integration RED→GREEN: pre-registration create through
      `registerOrder` with a future booking_time persists BOOKED; past booking_time
      refuses A0400 (预登记时间不能早于当前时间); `FOR UPDATE` on the config row
      serializes same-slot creates (design D7 — lock order asserted in test)

## 3. Kernel DDL + aggregate regression guards

- [x] 3.1 V2 applied to the test-only `core` schema alongside V1 — existing kernel
      aggregate/state-machine/registration suites all green (no regression; V36/V22
      seed-idempotency lessons respected)
- [x] 3.2 `booking_time` round-trip on the aggregate: persist NULL → read NULL; persist a
      future timestamp → read equal (jsonb/autoResultMap interplay untouched)

## 4. Verification

- [x] 4.1 Full reactor test run from `lumen-sev/` (JDK 11, `./mvnw test -q`), output to a
      log file — exit 0, zero failures; quote only key lines on any red
      (log /tmp/kpr-41-reactor2.log: green EXCEPT 3 lumen-system classes killed by one
      public-network drop at ~15min — SocketTimeoutException/auth-unreachable; isolated
      rerun of those 3 with -am green 13/13 (/tmp/kpr-41-isolated3.log); first run's
      single failure = shared-server grant drift, self-repaired by the committed V22
      re-execution, DB verified 3/3, isolated green — incident recorded in loop-state)
- [x] 4.2 `/opsx:verify` three-layer credential (L1 build / L2 spec-scenarios vs tests /
      L3 full suite); verify.md overall = PASS recorded for the coordination gate
      (L1 exit 0; L2 11/11 IMPLEMENTED with file:line evidence; L3 fresh lumen-core
      exit 0 + task-4.1 reactor context with the two environmental incidents recorded)
- [x] 4.3 Coordination doc registry updated (kernel-pre-registration → verified); proposals
      ②③ may start after this gate (kernel-first dependency)
