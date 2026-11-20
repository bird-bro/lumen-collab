# Tasks

## Batch 1 — module scaffold + ledger

- [x] 1.1 `lumen-us` module: pom (aggregator + BOM import), `UsApplication` (scan `org.birdbro`,
      `@MapperScan org.birdbro.**.mapper`), `application.yml` (port 8083, flyway schemas/us,
      internal-key + patient/system url props), port-table row in `init-project-skeleton.md`,
      nginx location + docker compose service. Test: context boots (KernelContextBoot precedent).
      (nginx/compose deferred per D1 amendment — B1 review ACCEPT, docker change owns deployment)
- [x] 1.2 Ledger V1: vendor-copy the kernel registration DDL verbatim. Test: schema migration test
      asserting the four tables + audit columns + re-execution idempotence (statement-split, V36
      lesson).
- [x] 1.3 Ledger V2 `us_registration_ext` + ledger V3 cross-schema menu seed (登记 C page + F
      buttons under the US W node, `us:order:*`, admin grant). Tests: ext columns + seed
      CONTENT-guard assertions (menu-seed test precedent).

## Batch 2 — patient internal surface + contract

- [x] 2.1 `lumen-api/patient`: `PatientApi` Feign contract + DTOs (resolve-or-register identity,
      outbox entry/ack) — add-only, TDD against the contract shape.
- [x] 2.2 patient-side: `InternalKeyInterceptor` config + `InternalPatientController`
      (resolve-or-register composite over existing `PatientArchiveService`, outbox pull/ack).
      Tests: exact-match no-new-row, register-new (incl. placeholder + A0715), pull/ack exactly-once,
      internal-key refusal — TDD.

## Batch 3 — us adapters + registration surface

- [x] 3.1 us adapters: `PatientRegistryPortAdapter`, `NumberIssuerPortAdapter`, internal-key
      propagation config. Tests: Feign-seam stubs (`@MockBean` base), incomplete-identity refusal.
- [x] 3.2 `RegistrationController` (resolve passthrough / create / page query / register / cancel)
      + `RegistrationHook` impl persisting `us_registration_ext`. Tests: real-API create with
      accession issuance, transition guard refusals verbatim, cancel reasons, ext round-trip — TDD.

## Batch 4 — snapshot wiring

- [x] 4.1 Snapshot poller: scheduled pull → PATIENT_CHANGED refresh / PATIENT_MERGED repoint+refresh
      → ack; failure keeps PENDING. Tests: seeded outbox entries drive refresh + repoint + ack;
      failed dispatch stays PENDING — TDD. (+ two B3-review riders fixed in passing:
      ext audit fields server-owned; sweep SQL gate made explicit — see the b4 report)

## Batch 5 — verify

- [ ] 5.1 Full reactor `./mvnw test -q` green; `/opsx:verify` credential.
