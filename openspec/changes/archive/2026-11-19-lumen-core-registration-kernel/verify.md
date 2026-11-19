---
verify:
  change: lumen-core-registration-kernel
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "18/18"
      L3: PASS
---

# Verification — lumen-core-registration-kernel

Backend-only pure-kernel change (lumen-core module; no HTTP surface). Post-rewind
shape (2026-11-18: number rules → the platform, the kernel carries the port only).
All scenario evidence from the module's TDD suites; the L3 run is the FULL reactor
(lumen-core 23/23 within 339/0).

## Stacks

| Stack | Layer | Status | Detail |
|:---|:---|:---|:---|
| backend | L1 build | PASS | `./mvnw compile -q` (JDK 11); exit=0; the reactor build log (number-rule-unification-sev logs) |
| backend | L2 spec | 18/18 | all IMPLEMENTED (below) |
| backend | L3 tests | PASS | `./mvnw test -q`; exit=0; lumen-core 23 tests green within the 339/0 reactor |

## Scenario alignment (delta specs/exam-workflow-kernel)

| Requirement | Scenarios | Evidence (all in lumen-core test suite) |
|:---|:---|:---|
| Exam-Order Aggregate | 3/3 | ExamOrderAggregateTests (four linked members + patient_uid NOT NULL + stateless refusal; snapshot refresh guard byte-refused after 已出 — SnapshotRefreshGuardTests; channel LOCAL default + REMOTE reserved) |
| Document State Machine | 5/5 | ExamOrderStateMachineTests (booking BOOKED / walk-in REGISTERED / happy path / cancel branches incl. ISSUED-report refusal / illegal-transition rejection, status unchanged) |
| Number Issuer Port | 2/2 | NumberIssuerPortTests (stored accession = port result VERBATIM via FakeNumberIssuerPort.LAST_ISSUED; issuer failure refuses, zero rows survive) |
| Patient Registry Port | 2/2 | PatientRegistryPortTests (order creation consumes the port; incomplete identity refused, no row) |
| HIS Two-Phase Intake | 4/4 | HisTwoPhaseIntakeTests (matched cached + unmatched dropped; confirm → REGISTERED order + COMMITTED cache; no auto-commit; correction pre-commit only, committed rejects) |
| Registration Extension Hooks | 2/2 | RegistrationHookTests (hooks fire in order() sequence inside the tx; hook failure rolls back, no row survives) |

Delta specs/service-topology (MODIFIED Shared Library Boundary): KernelModuleArchitectureTests
pins lumen-core as non-executable (no boot entry point, no controllers, canonical DDL
present) — the boundary contract holds.

## Issues

- None. The 2026-11-18 rewind (number rules → the platform) left the kernel with the
  NumberIssuerPort seam only; the port's platform machinery is verified in
  number-rule-unification-sev (system-side).

Overall: **PASS** — 18/18 scenarios implemented, L1 + L3 green.
Next: `/opsx:archive lumen-core-registration-kernel`.
