<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-prereg-as-registered-sev
  date: 2026-11-24
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "24/24"
      L3: PASS
---

# Verification: us-prereg-as-registered-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 24/24 | PASS | logs/build-backend.log · logs/test-backend2.log |

- L1: `./mvnw compile -q` (lumen-sev reactor, JDK 11) — exit 0.
- L3: `./mvnw test -q -Dsurefire.rerunFailingTestsCount=1` (full reactor) — exit 0,
  **515 tests / 0 failures / 0 errors**. First plain run hit one shared-DB residue flake
  (`AuthWorkstationSeedTests` seed idempotency, lumen-system — untouched by this change);
  it passed in isolation (exit 0) and under the documented rerun tolerance (the
  shared-test-server hazards pattern).

## Scenario Alignment

### specs/exam-workflow-kernel/spec.md (17 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Aggregate persists as four linked members | IMPLEMENTED | kernel registerOrder (unchanged) + ExamOrderAggregateTests green |
| Snapshot refresh guarded by report state | IMPLEMENTED | SnapshotRefreshGuardTests green |
| Channel reserves the remote value | IMPLEMENTED | ExamChannel LOCAL default; aggregate tests |
| Booking time anchors the pre-registration slot | IMPLEMENTED | ExamOrder.bookingTime (V4); PreRegistrationFlowTests |
| Registration type records the entry category | IMPLEMENTED | ExamOrder.java:65; stamps RegistrationOrderServiceImpl.java:162/234; V6 DDL:19; forged-body guard test |
| Booking enters at 已预约 (content superseded → 已登记) | IMPLEMENTED | surface stamps :233-234; machine edges unchanged (ExamOrderStateMachineServiceImpl.java:53-54) |
| Walk-in and HIS confirm enter at 已登记 | IMPLEMENTED | :161-162; HisTwoPhaseIntakeTests |
| Happy path advances through each stage | IMPLEMENTED | ExamOrderStateMachineTests (edges untouched) |
| Cancellation branches split by stage | IMPLEMENTED | UsRegistrationApiTests.cancelBranchSplitsByStage (raw-seeded BOOKED branch) |
| Illegal transition rejected | IMPLEMENTED | ExamOrderStateMachineTests + illegalTransitionRefusal test |
| Entry status derives from booking time in one transaction (superseded → every path REGISTERED) | IMPLEMENTED | RegistrationOrderServiceImpl.java:161/233; createWalkIn / createPreRegistration tests assert REGISTERED |
| Pre-registration refused when the slot is at capacity | IMPLEMENTED | occupancy notIn :222-224 + validatePreregistration; at-capacity + REGISTERED-occupies tests |
| Pre-registration below capacity occupies the slot | IMPLEMENTED | persists REGISTERED + window count grows (createPreRegistration test) |
| Slot counting uses live pre-registered orders only (superseded → not-cancelled) | IMPLEMENTED | :222-224 (create) + :280 (picker); cancelled-frees test |
| Pre-registration refused outside open windows or in the past | IMPLEMENTED | outside-window / past refusal tests |
| Unconfigured modality refuses pre-registration | IMPLEMENTED | unconfigured refusal test |
| Vendored DDL keeps the department-owned config storage | IMPLEMENTED | V3→V6 vendor copy; UsSchemaMigrationTests column-order pin updated |

### specs/us-station/spec.md (7 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Create a booked order (superseded → stamps category, both REGISTERED) | IMPLEMENTED | :161-162 / :233-234; walk-in + pre-reg create tests |
| Invalid pre-registration is refused verbatim | IMPLEMENTED | the four A0400 refusal tests |
| Below-capacity pre-registration occupies the slot (superseded → not-cancelled count) | IMPLEMENTED | createPreRegistration test window count |
| The create result carries the derived state (superseded → + registrationType/createBy) | IMPLEMENTED | toResult :184, toView :470-471; extRoundTrips view asserts |
| Illegal transition is refused verbatim | IMPLEMENTED | illegalTransitionRefusalSurfacesKernelMessageVerbatim |
| Cancel carries a reason (superseded → CANCELLED) | IMPLEMENTED | :403 branch; pre-registration cancel branch in the cancel test |
| Modality reaches the issuer port | IMPLEMENTED | modalityReachesTheIssuerPort test |

## Issues

- **Consistency note (non-blocking, no scenario affected)**: the kernel's dormant
  reference composition `PreRegistrationFlowTests.composedCreate` still derives BOOKED
  via the untouched `PreRegistrationPolicy.deriveEntryStatus` (design D3 retired it from
  the us flow without modifying the kernel support method). No production path uses it;
  a future kernel change should re-point the reference composition (or the policy) to
  the REGISTERED-for-every-path semantics so the kernel test suite mirrors the synced
  spec.

## Next

- PASS → archive us-prereg-as-registered-sev (after the web sibling lands, or batch per
  the coordination doc).
