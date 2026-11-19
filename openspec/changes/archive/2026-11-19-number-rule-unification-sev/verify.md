---
verify:
  change: number-rule-unification-sev
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "8/8"
      L3: PASS
---

# Verification — number-rule-unification-sev

Backend-only change (lumen-system + lumen-patient + the new lumen-api contract module).
All scenario evidence from the apply-phase TDD suites; the L3 run is the FULL reactor.

## Stacks

| Stack | Layer | Status | Detail |
|:---|:---|:---|:---|
| backend | L1 build | PASS | `./mvnw compile -q` (JDK 11); exit=0; logs/build-reactor.log |
| backend | L2 spec | 8/8 | all IMPLEMENTED (below) |
| backend | L3 tests | PASS | `./mvnw test -q`; exit=0; **339 tests / 0 failures** (common 4 · lumen-core 23 · lumen-patient 45 · lumen-system 267); logs/test-reactor.log |

## Scenario alignment

### specs/number-rule (ADDED)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Rule CRUD enforces system perms | IMPLEMENTED | NumberRuleApiTests (reflection pins: class gate `system:number:list` + create/update/delete method pins — the AuthRoomPermPinTests contract pattern); duplicate-active behavioral rejection (A0714, one row written) |
| Family defaults are seeded | IMPLEMENTED | NumberRuleSeedTests.familyDefaultsAreSeeded (pat_id/accession/booking each one live yyMMdd row) |
| Concurrent issuance does not duplicate | IMPLEMENTED | NumberIssuerServiceTests.concurrentIssuanceDoesNotDuplicate (two threads → consecutive serials, FOR UPDATE row lock) |
| Stamp rule resets by day | IMPLEMENTED | NumberIssuerServiceTests.stampRuleResetsByDay (past token + serial 57 → restart at 1, stamp_value advances) |
| Burned serial survives a caller rollback | IMPLEMENTED | NumberIssuerServiceTests.burnedSerialSurvivesCallerRollback (harness tx rolls back after issuance; serial_cur committed; next issuance takes 0002 — 0001 never reused) |
| Issue endpoint rejects non-internal callers | IMPLEMENTED | NumberRuleApiTests.issueEndpointRejectsNonInternalCallers (missing/wrong X-Internal-Key → 401 envelope, serial_cur stays 0) |
| Feign failure fails fast | IMPLEMENTED | NumberApiFallback throws C0001 (never fabricates); PatientNumberIssuanceTests.platformFailureRefusesArchiveCreation (C-class propagates, archive refused) |

### specs/patient-domain (MODIFIED Image Number Issuance / REMOVED Number Rule Management API)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Concurrent issuance does not duplicate (patient-side) | IMPLEMENTED | the same platform lock serves pat_id (system suite green; the patient path consumes the identical issuer) |
| Stamp rule resets by day (patient-side) | IMPLEMENTED | same platform mechanism (seeded pat_id family yyMMdd) |
| Rolled-back archive creation burns the number | IMPLEMENTED | NumberIssuerServiceTests.burnedSerialSurvivesCallerRollback (the platform-side burn the patient flow inherits) + PatientNumberIssuanceTests pins the consumer side |
| Number Rule Management API REMOVED | IMPLEMENTED | patient main sources carry ZERO PatientNumberRule/patient:rule references (V3 drops the table; controller/DTOs/mapper/service deleted; PatientMenuSeedTests asserts the retired seed state) |

## Issues

- None blocking. The suite-wide Feign seam stub (PatientIntegrationTestBase `@MockBean
  NumberApi`) is the designed test contract — the real-call path is proven system-side.
- The shared test DB converged through the V2-resurrect repair dance (V40
  content-anchored; NumberRuleSeedTests asserts content-anchored, "not-live" contracts).

Overall: **PASS** — 8/8 scenarios implemented, L1 + L3 green (full reactor 339/0).
Next: user restarts :8081 to apply V40, then `/opsx:archive` together with the web
change (the coordination gate: both verify PASS → batch archive).
