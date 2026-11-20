<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-station-registration-sev
  date: 2026-11-20
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "16/16"
      L3: PASS
---

# Verification: us-station-registration-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 16/16 | PASS | logs/build-backend.log · logs/test-backend.log |

- L1 `./mvnw compile -q` (JDK 11) exit=0; L3 full reactor `./mvnw test -q` exit=0: **426 tests / 86 classes, 0 failures, 0 errors** (fresh surefire reports; two orphan report files from a retired Nov-18 test class removed before counting). Shared-DB window exclusive (`pg_stat_activity`).
- L2 evidence = the four SDD batch reviews' independently-verified file:line citations (B1–B4, all Approved); spot-checked during this verify via the surefire aggregates and the review citations below.

## Scenario Alignment

### us-station (12)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Endpoints reachable under the unified entry | IMPLEMENTED | `UsApplication.java:39-40` (scan `org.birdbro` + `@MapperScan`), `application.yml:25-26` (flyway schemas/us); the nginx hop is deployment-owned (D1 amendment — docker change), module identity + ledger fully landed |
| Kernel DDL is vendored verbatim | IMPLEMENTED | B1 review: independent `diff` of us V1 vs kernel source empty; statement-split replay test (`UsSchemaMigrationTests`, throwaway schema hard-dropped in finally) |
| Create a booked order | IMPLEMENTED | `RegistrationController` create → adapter → kernel `registerOrder` ONE transaction, accession issued via `NumberIssuerPort` inside it (B3 review); real-API create-with-accession test |
| Illegal transition is refused verbatim | IMPLEMENTED | B3: test asserts the kernel's exact message shape (matches `ExamOrderStateMachineServiceImpl.java:83`), status unchanged |
| Cancel carries a reason | IMPLEMENTED | B3: BOOKING_CANCELLED vs CANCELLED branch by stage + reason stored |
| Registration consumes the port result | IMPLEMENTED | B3 adapter tests: order links returned `patUid`, snapshot stored |
| Incomplete port result refused | IMPLEMENTED | B3 refusal ladder (data-less envelope / CANDIDATES / missing identity / null patUid → A0400) fires before any write |
| US-side resolve passthrough | IMPLEMENTED | B3: lookup-only `PatientApi.lookup` (MATCHED/CANDIDATES/NONE) + patient `/internal/patients/lookup` + us `POST /patients/resolve`, no side effect; `PatientLookupApiTests` 4/4 |
| Patient change refreshes open orders | IMPLEMENTED | B4 `UsSnapshotPollerTests`: refresh+ack with exam-time preservation; live-set = `deleted=0` + two cancel states excluded |
| Merge repoints live orders | IMPLEMENTED | B4: repoint test with cancelled-uid kept; ack after both steps; repoint = targeted single-column UPDATE with atomic WHERE re-eval. (Display staleness for merged-away identity → debt.md follow-up, patient owner; no corruption — empty-overlay verified as no-op merge) |
| Extension persists with the order | IMPLEMENTED | B3 `UsRegistrationHook` per the ACTUAL kernel seam, ext round-trip + rollback proof (varchar overflow → 500, zero order rows, zero ext rows); B4 rider strips client-posted audit fields (injection bug fixed RED→GREEN) |
| Registration page joins the US workstation tree | IMPLEMENTED | B1 V3 seed CONTENT-anchored to the V22 US W node (`up_id 0 + type W + title 超声工作站`), perms `us:order:{list,create,update,cancel}`, admin grant + setval tails (`UsMenuSeedTests`) |

### patient-domain — Internal Service Surface (4)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Resolve-or-register resolves an exact match | IMPLEMENTED | B2: exact-match no-new-row test |
| Resolve-or-register registers a new archive | IMPLEMENTED | B2: register-new incl. PROVISIONAL placeholder + pat_id issuance; `A0715` verbatim via the composite's REQUIRES_NEW re-read (race-sound per B2 review) |
| Outbox pull and ack hand out events exactly once | IMPLEMENTED | B2: pull (PENDING, id-asc, LIMIT) / guarded ack (id AND status=PENDING) idempotent no-ops tested; rows kept CONSUMED |
| Internal-key guard refuses foreign callers | IMPLEMENTED | B2: fail-closed interceptor (`InternalKeyInterceptor.java:44`), 401-before-service-layer with zero-row side-effect assertion |

## Notes

- SDD run: 4 implementer batches (opus ×1, sonnet ×3) + 4 reviewer rounds, all Approved; review riders (audit-field strip, sweep semantics pin) landed in B4; two B3 minors folded as riders.
- Debt registered in `debt.md`: MERGED-payload survivor staleness (flatten-widening follow-up for the patient owner) + unknown-event-type PENDING permanence (ruled semantics).
- Pre-existing patient bug surfaced (NOT this change): `create()`'s index backstop catch is dead code (25P02 in aborted tx) — the public create route answers 500 on a racing duplicate; composite route fixed additively. Root fix = editing an existing method (never-modify rule) — parent/user ruling pending.

## Next

- PASS → per the coordination deployment order: user verifies the real APIs (us service at 8083) before the frontend apply; then `/opsx:apply us-station-registration-web`; batch archive both after the web credential.
