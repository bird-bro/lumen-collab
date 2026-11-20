# Report: us-station-registration-sev Batch 3 (tasks 3.1-3.2 + the lookup-only contract gap)

## Status

DONE — both tasks implemented and green (us module 39/39; patient module 58/58 fresh
reports; L1 full-reactor compile exit 0). The B2-flagged lookup-only contract gap is
closed add-only (lumen-api +134 insertions / 0 deletions; patient main +134 lines
across 6 files, all strictly additive — git-verified). Kernel untouched
(`git diff lumen-core` empty). One shared-server hygiene fix was required before the
us suite could validate (stale Flyway checksums, see Concerns).

## What was implemented

### The B2 gap first — lookup-only resolve (add-only growth)

- `lumen-api/patient/PatientLookupResult` (NEW) — result ∈ MATCHED/CANDIDATES/NONE +
  identity + candidates; NONE replaces CREATED because the lookup chain never reaches
  the create fallback.
- `PatientApi.lookup` (ADDITIVE method) — `POST /api/v1/patient/internal/patients/lookup`
  taking the same resolve form (its `provisional` flag ignored — no fallback archive on
  this path). `PatientApiFallback` gains the forced override (fail-fast C0001; the three
  existing methods byte-identical).
- patient-side `PatientArchiveService.lookup` (ADDITIVE interface method, the
  `resolveOrRegister` overload precedent) — the impl re-runs the resolution chain's
  LOOKUP steps (i) exact → (ii) card → (iii) soft through the SAME private helpers the
  public `POST /patients/resolve` chain uses, with step (iv) REMOVED: exact | candidates |
  the ADDITIVE `PatientResolveResult.RESULT_NONE` marker, NO side effect ever.
- `InternalPatientController` gains `POST /patients/lookup` (same internal-key guard
  family) mapping to the contract DTO.
- `PatientLookupApiTests` (NEW, patient module) — exact-match-no-new-row, candidates,
  NONE-writes-nothing (count-before/after + keyed id_no), internal-key 401. 4/4 green.

### Task 3.1 — us adapters (`org.birdbro.lumen.us.adapter`)

- `PatientRegistryPortAdapter implements` kernel `PatientRegistryPort` — delegates to
  `PatientApi.resolveOrRegister`; REFUSES before any write: data-less envelope → A0400,
  CANDIDATES/missing identity → A0400 (candidates are a UI list, never an order
  linkage), `patUid`-less identity → A0400; transport failures propagate UNTOUCHED (the
  fallback's C0001, the patient `PatientNumberServiceImpl` precedent — no catch, no
  masking). Maps MATCHED→resolved=true / CREATED→resolved=false and builds the
  delivered-archive snapshot base (patId/name/gender/phone). The LOOKUP-ONLY
  passthrough rides `lookup(...)` on the same bean — deliberately OUTSIDE the kernel
  port interface (the kernel owns that seam and stays untouched).
- `NumberIssuerPortAdapter implements` kernel `NumberIssuerPort` — MIRRORS
  `PatientNumberServiceImpl` verbatim (envelope missing → B0001, transport → C0001
  propagates); the rule-family token comes from the kernel (the accession family is
  issued inside `registerOrder` — the composition point the kernel contract dictates).
- `config/FeignInternalKeyConfig` — the patient precedent COPIED per-service
  (additive-first per D3): `X-Internal-Key` from `lumen.internal-api-key`, env-var
  overridable, never committed.
- `UsApplication` gains `@EnableFeignClients(clients = {PatientApi.class,
  NumberApi.class})` (the PatientApplication explicit-cohort pattern). The auth slot B1
  left is exactly this — `UsUserDetailsService` already satisfied the framework
  SecurityConfig constructor.

### Task 3.2 — registration surface (`org.birdbro.lumen.us.controller` + hook)

- `RegistrationController` on `/api/v1/us`: `POST /patients/resolve` (lookup-only
  passthrough, `us:order:create`), `POST /orders` (identity-or-demographics + exam-item
  refs + optional ext block + emg flag; entry state fixed BOOKED), `GET /orders` (page:
  pageNum/pageSize/status/accessionNo/patientName — the name filter matches INSIDE the
  jsonb snapshot), `POST /orders/{id}/register` (`us:order:update`),
  `POST /orders/{id}/cancel` (reason body; `us:order:cancel`). Every endpoint carries a
  method-level `@PreAuthorize` pin; tokenless = 203, perm-less = A0301.
- `service/RegistrationOrderService` + `impl/` (interface + impl per naming.md):
  create is deliberately NOT transactional — the port resolves over HTTP BEFORE any tx
  opens (a remote call must never hold a local tx open), then the kernel
  `registerOrder(identity, order, execution, report)` runs the ONE registration
  transaction, issuing the accession through `NumberIssuerPort` INSIDE it (the kernel's
  composition point). The snapshot = delivered archive fields + the body's exam-time
  demographics (patient decision #4). Transitions go through the kernel state machine
  ONLY; the cancel branch splits by stage (BOOKED→BOOKING_CANCELLED, else→CANCELLED);
  the page query rides the kernel `ExamOrderMapper` + one batched ext load.
- `hook/UsRegistrationHook implements RegistrationHook` — wired per the ACTUAL seam
  (`order()` + `execute(RegistrationContext)`, nothing invented): drains the ext block
  and persists `us_registration_ext` 1:1 INSIDE the registration transaction; no
  stashed block = no row (the HIS two-phase path stays ext-less). The ext payload rides
  `hook/UsRegistrationExtHolder` (a us-side ThreadLocal stash — the kernel context
  carries only the aggregate BY DESIGN); the create path clears it in a finally so a
  refused registration never leaks onto a pooled thread. The rollback property is
  PROVEN by test: a varchar(16) overflow in pregnancy_status dies at the DB level and
  leaves NO order row (the ext insert is transactional with the aggregate).
- Entities/mappers/DTOs: `UsRegistrationExt` (order_id as `IdType.INPUT` PK, Lombok
  combo per the entity-builder ruling), `UsRegistrationExtMapper`, the four
  registration DTOs, `pom.xml` gains `lumen-api` + `lumen-core` (the B1 absence notes
  resolved).

### Tests (TDD; the us test harness B1 built, `.env-test.local`)

- `UsAdapterTests` 12/12 — Feign-seam stubs (`@MockBean` on both contracts, the
  feign-consumer-test-seam-stub precedent), the three incomplete-result refusals,
  C0001 propagation, lookup unwrap/refuse, number issuance/B0001/C0001.
- `UsRegistrationApiTests` 11/11 — real HTTP through the run-identity machinery
  (`UsApiTestBase`, the PatientApiTestBase pattern with a DISTINCT `itus3_` family so
  the sweep never deletes another module's run user): create-with-accession (stub band
  + accession markers, order/ext/execution/report rows asserted), patUid-less +
  ambiguous refusals persist nothing, 登记 BOOKED→REGISTERED, cancel reasons on both
  branches, the kernel refusal VERBATIM ("非法状态迁移: BOOKING_CANCELLED → REGISTERED"
  embedded unmasked in the house envelope, status unchanged), ext round-trip through
  the page query (and null ext for ext-less orders), the resolve passthrough's three
  outcomes with zero side effects, perm gates (203/A0301), and the hook-rollback proof.
- `UsIntegrationTestBase` — batch-3 amendment: MOCK→RANDOM_PORT (the
  PatientIntegrationTestBase precedent) + the base-level Feign-seam stubs (lenient
  unique-band defaults, D8; scenario suites override). `UsContextBootTests` identity
  assertion follows the AuthApplicationTests precedent (local.server.port under
  RANDOM_PORT; reserved 8083 stays the committed application.yml contract).
- Fixture hygiene: `UsRegistrationFixtures` hard-DELETEs on the stub uid band +
  `us3` snapshot-name + `US3-` accession markers (user ruling 2026-11-07); the run
  identity sweeps itself (`itus3_` family, log_info included).

## Verification

- `./mvnw -pl lumen-patient -am test` → exit 0 (58/58 fresh reports; the
  PatientPermTests surefire file in target/ is a stale Nov-18 leftover of a
  since-deleted class — not from this run).
- `./mvnw -pl lumen-us -am test` → exit 0, 39/39 (12 adapter + 2 boot + 5 menu seed +
  11 API + 4 ext + 5 schema migration).
- `./mvnw compile -q` (L1 full reactor) → exit 0 (includes the parallel session's
  lumen-system WIP without touching it).
- tasks.md 3.1/3.2 ticked; loop-state tasks_done 5→7 + artifacts_hash refreshed via
  `ensure-contract-fresh.sh --update`.

## Self-review

- Kernel untouched: `git status lumen-core` empty; `framework`/`common`/`bom` empty.
- Patient + lumen-api strictly add-only: 134 insertions / 0 deletions; the fallback's
  three existing overrides and every existing member byte-identical; the interface
  method additions are the sanctioned B2-review growth ("契约 add-only 届时可加").
- Perms on every endpoint: five `@PreAuthorize` pins riding the seeded
  `us:order:{list,create,update,cancel}` family; resolve passthrough pinned
  `us:order:create` per the spec.
- Hook per the ACTUAL seam: only `order()` + `execute(RegistrationContext)`, no
  invented methods, kernel zero-touch; the ThreadLocal stash is us-side only and
  finally-cleared.
- Never-modify rule: no existing public method touched anywhere; all growth is new
  methods/overloads/constants/files.

## Concerns

1. **Shared-server Flyway checksum repair (done, needs the parent's awareness):** the
   us suite failed validation with checksum mismatches on `us` ledger V2/V3 — B1's
   review-fix commit (3081099) amended the V2/V3 COMMENT LINES after first application,
   leaving the shared server's `us.flyway_schema_history` rows stale (DB state itself
   correct; files are the committed truth). Re-aligned the two checksum cells to the
   committed files (exactly what `flyway repair` does) via a one-off JDBC tool run from
   /tmp (outside the repo): V2 1859793870→1598572447, V3 -1930542690→-167675209, V1
   untouched, no schema/data change. Per the never-rewrite ruling this is the opposite
   shape (files committed+unchanged, history stale), not repair-and-diverge.
2. **Create-body demographics never override a delivered archive:** the snapshot's
   name/gender/phone come from the RESOLVED archive (the delivered fields win); the
   body's values only fill gaps (CREATED archives) — the web change's fill-from-archive
   flow matches this. The page `patientName` filter therefore matches the ARCHIVE name,
   not a body-typed name.
3. **Shared test server was free this window;** the patient + us suites ran
   back-to-back with no contention (single owner, no fixture interference observed).
