<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: kernel-pre-registration
  date: 2026-11-23
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "11/11"
      L3: PASS
---

# Verification: kernel-pre-registration

Overall: **PASS**

Single-stack change (backend, lumen-core kernel library only — no web surface).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 11/11 | PASS | logs/build-backend.log, logs/test-backend.log |

- **L1**: `./mvnw compile -q` from `lumen-sev/` (JDK 11) — exit 0.
- **L3**: fresh `./mvnw -pl lumen-core test -q` — exit 0 (62 tests incl. the 27 new
  pre-registration cases). Full-reactor context from task 4.1 (see Issues).

## Scenario Alignment

Delta: `specs/exam-workflow-kernel/spec.md` (3 preserved Aggregate scenarios re-verified
by regression; 1 new booking-time scenario; 7 Pre-Registration Support scenarios).

| Scenario | Status | Evidence |
|:--|:--|:--|
| Aggregate persists as four linked members | IMPLEMENTED | `ExamOrderAggregateServiceImpl.java:77-89` (untouched; existing suites green in L3) |
| Snapshot refresh guarded by report state | IMPLEMENTED | `ExamOrderAggregateServiceImpl.java:96-108`; SnapshotRefreshGuardTests green |
| Channel reserves the remote value | IMPLEMENTED | `ExamOrderAggregateServiceImpl.java:71-73`; ExamChannel enum; existing suites green |
| Booking time anchors the pre-registration slot | IMPLEMENTED | `ExamOrder.java:57`; `V2__exam_kernel_pre_registration.sql:21`; round-trips `PreRegistrationFlowTests.java:185,277` |
| Entry status derives from booking time in one transaction | IMPLEMENTED | `PreRegistrationPolicy.java:42` (pure derivation; unit-tested `PreRegistrationPolicyTests`); composed create derives inside the caller's transaction before `registerOrder` (`PreRegistrationFlowTests.java:185`) |
| Pre-registration refused when the slot is at capacity | IMPLEMENTED | `ExamSlotCapacityServiceImpl.java:138` (A0400 该时段已满); real-row proof `PreRegistrationFlowTests.java:209` (4/4 live BOOKED, refusal persists nothing) |
| Pre-registration below capacity occupies the slot | IMPLEMENTED | `PreRegistrationFlowTests.java:209` (create passes at 3/4, window count 3→4) |
| Slot counting uses live pre-registered orders only | IMPLEMENTED | count predicate = caller-side per design D2; reference count demonstrated `PreRegistrationFlowTests.java:209` (REGISTERED + NULL-booking orders excluded, live count 3) |
| Pre-registration refused outside open windows or in the past | IMPLEMENTED | window: `ExamSlotCapacityServiceImpl.java:113` + tests `ExamSlotCapacityServiceTests.java:96,105`; past: `PreRegistrationPolicy.java:63` (D4 — guard runs before the count) + `PreRegistrationFlowTests.java:257`, boundary `ExamSlotCapacityServiceTests.java:175` |
| Unconfigured modality refuses pre-registration | IMPLEMENTED | `ExamSlotCapacityServiceImpl.java:127-130` (fail-closed); `ExamSlotCapacityServiceTests.java:165`; AND-clause (walk-in unaffected) `PreRegistrationFlowTests.java:277` |
| Vendored DDL keeps the department-owned config storage | IMPLEMENTED | kernel obligation complete: copy-from source `V2__exam_kernel_pre_registration.sql:21-36` (modality live-unique + GIN), applied to the test-only `core` schema `PreRegistrationSchemaTests`; mapper schema-agnostic (`ExamSlotConfigMapper.java`). Department vendoring EXECUTION belongs to proposal ② per the coordination registry — not this change's scope |

D7 lock-order design assurance: `PreRegistrationFlowTests.java:299` proves
`validatePreregistration` holds the config-row FOR UPDATE lock inside the caller's
create transaction (second-connection 55P03 timeout probe).

## Issues

Environmental only — none attributable to this change's code (lumen-core untouched by
both incidents):

1. **Shared-server grant drift (task 4.1, reactor run 1)**: `AuthWorkstationSeedTests`
   detected 2 missing role-1 W grants on the shared `system` schema ("expected 1 but
   was 3" after re-executing V22). The test's committed re-execution repaired the
   state (DB verified 3 W-nodes/3 grants after); isolated rerun green. Known
   shared-server hazard family (种子幂等×清扫互踩); loop-state retry_count=1.
2. **Public-network drop (task 4.1, reactor run 2)**: one drop event at ~15 min killed
   3 lumen-system classes (`SocketTimeoutException: Read timed out` / auth-unreachable
   / server-side 500). Isolated rerun of the three classes with `-am` (fresh framework
   in reactor): 13/13 green. Same credential shape as the number-rule-modality-scope
   verify (isolated-green + issues recorded).
