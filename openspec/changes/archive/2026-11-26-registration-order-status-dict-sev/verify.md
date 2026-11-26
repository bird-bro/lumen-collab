<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: registration-order-status-dict-sev
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "34/34"
      L3: PASS
---

# Verification: registration-order-status-dict-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q` exit=0) | 34/34 | PASS (full reactor suite; 1 known shared-DB connection flake — isolated rerun green) | logs/build-backend.log, logs/test-backend.log |

## Scenario Alignment

### exam-workflow-kernel delta — Document State Machine

| Scenario | Status | Evidence |
|:--|:--|:--|
| Booking enters at 已预约 (content: booking → 已登记, no BOOKED state) | IMPLEMENTED | lumen-core ExamOrderStatus.java:22-30 (six constants, no BOOKED); PreRegistrationPolicy.java:48-58 |
| Walk-in and HIS confirm enter at 已登记 | IMPLEMENTED | RegistrationOrderServiceImpl.java:171; HisTwoPhaseIntakeServiceImpl.java:102 |
| Happy path advances through each stage | IMPLEMENTED | ExamOrderStateMachineServiceImpl.java:58-66 (forward-permissive EDGES incl. COMPLETED→{PRINTED}); ExamOrderStateMachineTests.forwardEdgesStayPermissive |
| Cancellation branches split by stage (single 已取消 landing) | IMPLEMENTED | ExamOrderStateMachineServiceImpl.java:93-103 (issued-report guard + single isCancelBranch); EDGES omit CANCELLED from COMPLETED |
| Illegal transition rejected | IMPLEMENTED | ExamOrderStateMachineServiceImpl.java:81-88 (refusal before any write) |

### exam-workflow-kernel delta — Pre-Registration Support (7 carried scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Entry status derives from booking time in one transaction | IMPLEMENTED | PreRegistrationPolicy.java:48-58; RegistrationOrderServiceImpl.java:171/242 |
| Pre-registration refused when the slot is at capacity | IMPLEMENTED | ExamSlotCapacityService.validatePreregistration (A0400 该时段已满); suite green |
| Pre-registration below capacity occupies the slot | IMPLEMENTED | same path; suite green |
| Slot counting uses live pre-registered orders only | IMPLEMENTED | RegistrationOrderServiceImpl.java:225-235 (notIn(CANCELLED) occupancy) |
| Pre-registration refused outside open windows or in the past | IMPLEMENTED | validatePreregistration window/past refusals; suite green |
| Unconfigured modality refuses pre-registration | IMPLEMENTED | validatePreregistration config-missing refusal; suite green |
| Vendored DDL keeps the department-owned config storage | IMPLEMENTED | lumen-us V1__exam_kernel_registration.sql (kernel DDL vendoring) |

### us-station delta — Registration Surface

| Scenario | Status | Evidence |
|:--|:--|:--|
| Create a booked order (WALK_IN/PRE_REGISTERED → REGISTERED) | IMPLEMENTED | RegistrationOrderServiceImpl.java:171 / :242 |
| Invalid pre-registration is refused verbatim | IMPLEMENTED | validatePreregistration A0400 surface; suite green |
| Below-capacity pre-registration occupies the slot | IMPLEMENTED | same path |
| The create result carries the derived state | IMPLEMENTED | RegistrationOrderServiceImpl toResult (status/bookingTime/registrationType + create stamps) |
| Illegal transition is refused verbatim | IMPLEMENTED | machine refusal via the single writer; 登记 route ABSENT (RegistrationController.java:67-103 — only resolve/create/page/status-counts/slot-options/cancel) |
| Cancel carries a reason (single landing) | IMPLEMENTED | RegistrationOrderServiceImpl.java:464-467; reason patch ExamOrderStateMachineServiceImpl.java:101-103 |
| Modality reaches the issuer port | IMPLEMENTED | registerOrder → NumberIssuerPort (kernel contract); suite green |

### us-station delta — Workbench Query Surface

| Scenario | Status | Evidence |
|:--|:--|:--|
| Status multi-select composes with the wide conditions | IMPLEMENTED | appendWideConditions status IN; UsRegistrationApiTests.statusMultiSelectComposesWithWideConditions |
| Snapshot conditions hit the jsonb document | IMPLEMENTED | appendWideConditions patId/patientSource/cardNo |
| Exam-item name matches fuzzily across the jsonb array | IMPLEMENTED | exam_items per-element LIKE |
| Apply-time range bounds the registration moment | IMPLEMENTED | create_time range condition |
| Status counts respect the filters (six keys zero-filled) | IMPLEMENTED | RegistrationOrderServiceImpl.java:396+ (values() zero-fill); UsRegistrationApiTests.statusCountsRespectTheFilters |

### api delta — Dict Items Lookup By Catalog Name

| Scenario | Status | Evidence |
|:--|:--|:--|
| Items returned for a known catalog | IMPLEMENTED | DictDetailManageServiceImpl.java:99-118 (byCaId) / :127+ (byName), sort-ordered |
| Unknown catalog resolves to an empty list | IMPLEMENTED | DictDetailManageServiceImpl.java:104-106 |
| Disabled details are excluded | IMPLEMENTED | enable=1 filter DictDetailManageServiceImpl.java:110 |
| Lookup requires authentication but no permission string | IMPLEMENTED | DictDetailController @PreAuthorize("isAuthenticated()") |
| Tagged items carry the optional display metadata | IMPLEMENTED | DictItemDto.java:33-34 (NON_NULL); blank→absent normalization DictDetailManageServiceImpl.java:113/140; both shapes asserted in AuthDictItemsLookupTests |

## Issues

- L3 flake (non-blocking): the full-suite run carried 1 error —
  `UsRegistrationApiTests.registeredPreRegistrationOccupiesTheSlotAndCancelledFreesIt`:
  `尝试连线已失败` (PSQL EOF — the shared test server dropped one connection, the
  recorded WAN-flake family). Isolated rerun of the class: exit=0, all green. No code
  action.
- Historical note: V55 was replayed twice through the never-rewrite sanctioned path
  (uncommitted + applied; the user's same-day code-prefix correction and the tag
  seeds) — the flyway history row was dropped each time and the file re-applied;
  checksums now match the file.

## Next

- PASS → archive registration-order-status-dict-sev (batch with the web change per the
  coordination doc gate).
