---
verify:
  change: patient-identity-card-projection
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "5/5"
      L3: PASS
---

# Verification: patient-identity-card-projection

## backend

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q`; exit=0; logs/build-backend.log |
| L2 spec | 5/5 | all scenarios IMPLEMENTED (evidence below) |
| L3 tests | PASS | full reactor `./mvnw test` — **548 tests / 0 failures / 0 errors** (logs/test-backend.log, the completed run's output) |

## Scenario alignment

### patient-domain — Internal Service Surface (specs/patient-domain/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| The identity projects the archive's latest visit card | IMPLEMENTED | PatientIdentityResponse.java:66 (the optional cardNo); InternalPatientController.java:151-158 (the latest live `id_type='card'` row, id desc LIMIT 1, null when none — never fabricated); PatientInternalApiTests.resolvedIdentityProjectsTheArchivesLatestCard (two cards → the SECOND/higher id wins; a card-less archive → null) |
| Resolve-or-register resolves an exact match (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| Resolve-or-register registers a new archive (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| Outbox pull and ack hand out events exactly once (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| Internal-key guard refuses foreign callers (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |

## Issues

None blocking. Notes:

- The us resolve passthrough forwards the Feign result verbatim — the cardNo reaches
  the desk with ZERO us/web code (the web type + the backfill mapping were built
  anticipating the field: us-orders.ts:180, the page's fillFromIdentity).
- The candidates path rides the same projection (the candidate modal can display the
  card without further contract work).

Overall: **PASS** — ready for `/opsx:archive patient-identity-card-projection`.
