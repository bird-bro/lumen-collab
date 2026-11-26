---
verify:
  change: patient-resolve-demographics-enrichment
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "5/5"
      L3: PASS
---

# Verification: patient-resolve-demographics-enrichment

## backend

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q`; exit=0; logs/build-backend.log |
| L2 spec | 5/5 | all scenarios IMPLEMENTED (evidence below) |
| L3 tests | PASS | full reactor `./mvnw test` — **549 tests / 0 failures / 0 errors** (logs/test-backend.log) |

## Scenario alignment

### patient-domain — Identity Resolution Chain (specs/patient-domain/spec.md)

| Scenario | Status | Evidence |
|:---|:---|:---|
| The composite match enriches the archive's null demographics | IMPLEMENTED | PatientArchiveServiceImpl.java:310-321 (the MATCHED branch enriches INSIDE the composite's transaction, the race backstop intact); :325-373 (`enrichMatchedArchive` — the minimal-merge null-fill + the absent-card append via the existing insertIdentifier, idempotent); PatientInternalApiTests.compositeMatchEnrichesTheSparseArchiveNullDemographics (null fields fill + the DIFFERENT-name no-overwrite band + the SQL-verified archive row + the one-card-row idempotency) |
| Exact id_no hit resolves immediately (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| Soft match returns candidates (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| No match creates a new archive (carried) | IMPLEMENTED | PatientInternalApiTests (green in the reactor run) |
| Outbox/lookup/key-guard surfaces (carried, incl. the lookup's no-side-effect stance) | IMPLEMENTED | PatientInternalApiTests + PatientLookupApiTests (green in the reactor run) |

## Issues

None blocking. Notes:

- The enrichment is composite-only: the plain lookup stays side-effect-free (the
  recorded stance) — the desk's data sticks at SUBMIT time (the composite), not at
  resolve time.
- The chain-ordering reasoning for the id_no fill (a claimed id_no resolves to its
  owner before the card walk) is recorded in the design + the method javadoc.

Overall: **PASS** — ready for `/opsx:archive patient-resolve-demographics-enrichment`.
