---
verify:
  change: us-order-exam-room-sev
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "19/19"
      L3: PASS
    lumen-web:
      L1: SKIP
      L2: SKIP
      L3: SKIP
---

# Verification: us-order-exam-room-sev

> Backend-only half of the cross-stack pair; the web half (us-order-exam-room-web)
> verifies against its own stack when it applies. Stacking resolved: the co-riding
> applied slice landed inside 2ac1a99 (registration-order-status-dict archive, which
> documents the carry); the remaining tasks of THIS change verified here.

## backend

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q`; exit=0; logs/build-backend.log |
| L2 spec | 19/19 | all scenarios IMPLEMENTED (evidence below) |
| L3 tests | PASS | `./mvnw test -q`; exit=0; logs/test-backend.log — surefire aggregate **547 tests / 0 failures / 0 errors** (the 20 `[ERROR]` log lines are the JDK 11 MyBatis-Plus illegal-reflective-access WARNINGS, stderr noise) |

## Scenario alignment

### exam-workflow-kernel (specs/exam-workflow-kernel/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Exam room anchors the exam destination | IMPLEMENTED | ExamOrder.java:75 (`roomId` field); ExamOrderAggregateTests.java:192 (stamp + mapper round-trip + legacy-NULL load); V4__exam_order_exam_room.sql:16 / V57__exam_order_exam_room.sql:16 |
| Aggregate persists as four linked members (carried) | IMPLEMENTED | ExamOrderAggregateTests.java:65 (green in the reactor run) |
| Snapshot refresh guarded by report state (carried) | IMPLEMENTED | SnapshotRefreshGuardTests (green in the reactor run) |
| Channel reserves the remote value (carried) | IMPLEMENTED | ExamOrderAggregateTests.java:98 |
| Booking time anchors the pre-registration slot (carried) | IMPLEMENTED | ExamOrderAggregateTests + PreRegistrationFlowTests (green in the reactor run) |
| Registration type records the entry category (carried) | IMPLEMENTED | UsRegistrationApiTests walk-in/prereg assertions (green in the reactor run) |

### us-station (specs/us-station/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Create a booked order (room stamps BOTH kinds) | IMPLEMENTED | RegistrationOrderServiceImpl.java:226 (the one stamp point in `newOrder`); UsRegistrationRoomTests.java:153 (walk-in + pre-registration rows carry room_id) |
| Invalid pre-registration is refused verbatim (carried) | IMPLEMENTED | PreRegistrationPolicyTests / PreRegistrationFlowTests (green in the reactor run) |
| Create refuses a missing or invalid room | IMPLEMENTED | RegistrationOrderServiceImpl.java:156 (`requireValidRoom` at the create HEAD, before the port resolve) + :624 (null → 检查室不能为空; missing/disabled/foreign-dept → 检查室不存在或未启用); UsRegistrationRoomTests.java:86/106/120/136 (nothing persisted asserted) |
| Below-capacity pre-registration occupies the slot (carried) | IMPLEMENTED | PreRegistrationFlowTests (green in the reactor run) |
| The create result carries the derived state (roomId/roomName on view rows) | IMPLEMENTED | RegistrationOrderServiceImpl.java:413/:424 (`applyRoomNames` — ONE directory read per page, dangling id → null name); UsRegistrationRoomTests.java:192 (both bands proven) |
| Illegal transition is refused verbatim (carried) | IMPLEMENTED | UsRegistrationApiTests (green in the reactor run) |
| Cancel carries a reason (carried) | IMPLEMENTED | UsRegistrationApiTests (green in the reactor run) |
| Modality reaches the issuer port (carried) | IMPLEMENTED | UsRegistrationApiTests (green in the reactor run) |
| Station rooms list the department's enabled rooms | IMPLEMENTED | StationRoomController.java:35-36; StationRoomServiceImpl.java:41 (knob-scoped, null-safe envelope); UsStationRoomsApiTests.java:63 |
| Room service unavailability degrades to an honest empty list | IMPLEMENTED | SystemRoomApiFallback.java:28 (list → empty; detail → throw, the split semantics); UsStationRoomsApiTests.java:83 (200 + empty through the real chain) |

### api (specs/api/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Internal list returns the department's enabled rooms | IMPLEMENTED | InternalRoomController.java:41; OrgRoomServiceImpl.java:74 (enabled filter + id order + slim rows); AuthRoomInternalApiTests.java:53 |
| Internal detail carries the validation fields | IMPLEMENTED | InternalRoomController.java:47; OrgRoomServiceImpl.java:93 (did/enable carried, @TableLogic live filter); AuthRoomInternalApiTests.java:87 |
| Missing internal key is refused | IMPLEMENTED | InternalRoomApiWebConfig (both paths registered) + application.yml permit-all (both paths); AuthRoomInternalApiTests.java:112 (keyless + wrong-key → 401) |

## Issues

None blocking. Notes:

- The `>500-char` requirement-text WARNINGS on the two ADDED requirements are advisory
  (validate --changes): the text carries the Feign/knob rationale; behaviors live in
  the scenarios.
- The rail-quick-search `keyword` annotation misplacement (a foreign change's bug that
  went red during apply regression) was fixed by user-authorized hotfix with a
  placement note; its owner should carry the fix into their own archive.
- Shared-DB WAN flake family: one connection drop during an earlier apply-stage run
  (isolated rerun green); this verify run was clean end-to-end.

Overall: **PASS** — ready for `/opsx:archive us-order-exam-room-sev`.
