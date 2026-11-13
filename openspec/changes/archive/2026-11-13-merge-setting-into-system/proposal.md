# Proposal — merge-setting-into-system

## Why

The standalone `lumen-setting` service (exam-setting-api, archived 2026-11-13) is a
pseudo-microservice: it shares the database, Redis, and the JWT secret with
lumen-system, exchanges no MQ events, has no Feign consumers, and its menu seeds already
live in system migrations — the split costs a second image, build, Flyway ledger, nginx
upstream, and DB role while buying no deployability for a pure-CRUD config domain. User
ruling 2026-11-13 dissolves it (rationale ledger: `openspec/coordination/exam-setting.md`
§ "2026-11-13 Merge Ruling"). One service = one image = one schema = one Flyway ledger;
future department services (radiology/us/endo) keep their planned independent services.

## What Changes

- **BREAKING (deployment topology only — the API surface is unchanged)**: the Maven
  module `lumen-setting` is removed from the aggregator; the exam-setting domain is
  HOSTED inside `lumen-system`, keeping its own package root `org.birdbro.lumen.setting`
  (class FQNs unchanged), its URL prefix `/api/v1/setting/**`, and its `setting:*` perms
  family. `SystemApplication` stays the single entry (name `lumen-system`, port 8081);
  `SettingApplication` and the verify-only `SettingUserDetailsService` are deleted.
- The six exam tables move from the `setting` schema into the `system` schema:
  `V29__exam_setting_tables.sql` (= the former `V1__setting_schema.sql`) joins the ONE
  service ledger; the `setting` schema and `lumen_setting_role` are retired
  (`docs/deployment/pg-init.sql` back to six schemas).
- The setting integration-test family re-homes into lumen-system's surefire run:
  `currentSchema=system,public`, the shared `system` Flyway ledger, sweep SQL
  re-targeted to the `system` schema. No test is rewritten — schema references move.
- Frontend: ZERO changes (URLs, perms strings, menu seeds untouched; the vite proxy
  single-target dev shape keeps working against 8081).

## Capabilities

### New Capabilities

(none — no new behavior; the exam-setting capability keeps its endpoints, guards, and
scenarios verbatim)

### Modified Capabilities

- `service-topology`: "Foundation Module Set" drops `lumen-setting` from the
  deferred-module enumeration; a new "Hosted Domain Packages" requirement records the
  hosting pattern (own package root inside the hosting module, ONE application class at
  the service root, tables join the hosting service's ONE schema + ledger, cheap
  re-extraction path).
- `data`: "Schema-per-Service Isolation" enumeration reverts seven → six schemas
  (`setting` retired; no `lumen_setting_role`).
- `exam-setting`: "Setting Schema Ownership" re-written to the hosted shape (the six
  tables live in the hosting service's `system` schema under the single ledger); the
  six column requirements' schema host updates `setting` → `system`. Endpoints, perms,
  modality vocabulary, delete guards, and scope enforcement are unchanged.

## Impact

- `lumen-sev/pom.xml` (module list −1); `lumen-sev/lumen-setting/**` deleted (~35 main
  classes + 13 test classes MOVED into `lumen-sev/lumen-system/**` under the same
  `org.birdbro.lumen.setting` packages — git rename, no content rewrite except schema
  retarget in the test bases and the seed test).
- `lumen-sev/lumen-system/src/main/resources/db/migration/V29__exam_setting_tables.sql`
  (new; header cites the merge ruling); `docs/deployment/pg-init.sql` (−1 schema,
  −1 role block); `.claude/rules/naming.md` (hosted-domain-package precedent);
  `lumen-sev/CLAUDE.md` (module list line); `openspec/coordination/exam-setting.md`
  (ruling appended — done at propose time).
- Deployment: one image (8081) instead of two; nginx `/api/v1/setting/**` upstream
  retargets to 8081 when the deferred deployment change lands. Existing databases: the
  old `setting` schema holds fixture residue only (verified 2026-11-13 — zero real
  catalog rows), so the re-home is drop-and-recreate, no data migration.
