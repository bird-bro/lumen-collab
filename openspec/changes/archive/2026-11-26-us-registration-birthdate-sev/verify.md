<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-registration-birthdate-sev
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: us-registration-birthdate-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 2/2 | PASS | logs/build-backend.log, logs/test-backend.log (rerun notes below) |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Walk-in birth date joins the archive chain | IMPLEMENTED | RegistrationCreateRequest.java:56 (`@JsonFormat yyyy-MM-dd` binding) → RegistrationOrderServiceImpl.java:425 (`toPortRequest` pass-through) → kernel PatientRegistrationRequest.java:43 → adapter `BeanUtil.copyProperties` 1:1 → Feign contract (pre-existing `birthDate`); UsRegistrationApiTests.java:217 captures the contract body and asserts `1990-05-20` (soft-key path) |
| Birth date is optional | IMPLEMENTED | UsRegistrationApiTests.java:217 second captor asserts contract `birthDate` null on absent; PatientSnapshot carries no birthDate (snapshot stays age-only — field inventory unchanged); controller untouched (DTO binding automatic) |

## L3 Notes (reruns)

- Full-reactor run: lumen-us 76/76 ✓, lumen-core 63/63 ✓; lumen-system 18F+1E —
  ALL parallel-session fixture races on the shared test DB (run-identity
  interleave, `create_by expected 10302 but was 10299` family; zero overlap with
  this change's diff — lumen-system untouched).
- Exclusive-window rerun: 321/322 green; the remaining one was
  `NoSuchMethodError: ILogInfoService.getPage` — a `-pl lumen-system` (no `-am`)
  invocation artifact resolving a STALE framework jar from ~/.m2, not code.
- Fresh `-am` rerun: AuthPermEnforcementTests PASS (exit 0). L3 recorded PASS.
