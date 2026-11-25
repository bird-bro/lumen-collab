<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-booking-slot-picker-sev
  date: 2026-11-25
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "3/3"
      L3: PASS
---

# Verification: us-booking-slot-picker-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 3/3 | PASS | logs/build-backend.log · logs/test-backend.log |

- L1: `./mvnw compile -q` exit 0. L3: `./mvnw -pl lumen-us -am test -q` exit 0 —
  **75 tests / 0 failures / 0 errors**.

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| The configured grid carries live occupancy | IMPLEMENTED | RegistrationController.java:103 endpoint; RegistrationOrderServiceImpl.java:241-285 (window flooring + the not-cancelled occupancy count); slotOptionsExposeTheConfiguredGridWithLiveOccupancy test (8 slots, booked=1 @ 09:00, capacity 2, untouched 0) |
| Unconfigured modality fails soft | IMPLEMENTED | :258 configured=false + empty slots; the US3NOPE soft-answer assertion (test:253-260) |
| Invalid date refuses | IMPLEMENTED | :247-253 strict LocalDate parse → A0400 预约日期格式无效 (code evidence; see Issues) |

## Issues

- The invalid-date refusal has code evidence but no dedicated API test — the
  parse-refusal branch is exercised only indirectly. Low risk (fail-closed parse);
  a future test addition can pin the A0400 token 预约日期格式无效.
- The delta's occupancy prose was refreshed pre-archive to the current
  未取消即占位 predicate (the original text predated the us-prereg-as-registered
  ruling; the implementation already followed the newer anchor).

## Next

- PASS → archive us-booking-slot-picker-sev.
