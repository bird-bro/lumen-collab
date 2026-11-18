# patient-domain Batch 1 Report — tasks 1.1–1.3

Commit: `25bca2f` (14 files, 1009 insertions; only the brief's exact file list staged by explicit path).

## What was implemented

- **1.1 Module scaffold** — `lumen-sev/lumen-patient`: module pom (parent `lumen-sev`; framework + common + web + validation + flyway-core + commons-pool2 + test starter; NO bouncycastle pin/exclusion — no SM2 usage here, druid/postgres/mybatis-plus ride framework/common), aggregator `<module>lumen-patient</module>` line, `PatientApplication` at `org.birdbro.lumen.patient` with `scanBasePackages="org.birdbro"` + wildcard `@MapperScan("org.birdbro.**.mapper")`, `PatientUserDetailsService` in `patient/security` (always-throws placeholder satisfying framework `SecurityConfig` constructor; javadoc states the JWT path builds Authentication from token claims, never loads UserDetails), `application.yml` (port 8082, name `lumen-patient`, flyway `schemas/default-schema: patient`, token/xss/dispose/mybatis-plus/jackson/mvc/logging copied verbatim; `security.permit-all`, `upload.*`, `password-crypto` deliberately absent — SecurityAnonymousProperties/GlobalProperties/WebMvcConfig defaults cover all three; the green context load confirms), `application-dev.yml.example` (`currentSchema=patient,public` + redis, fu shape), `db/migration/.gitignore` (`!*.sql` negation copied).
- **1.2 Port table** — `lumen-patient | 8082` row in `openspec/coordination/init-project-skeleton.md`; trailing note narrowed to ris/integration (patient is now allocated).
- **1.3 V1__patient_domain.sql** — five tables (`patient_master`, `patient_identifier`, `patient_number_rule`, `patient_event_outbox`, `patient_merge_log`) per the binding column map: `serial PRIMARY KEY` + EntityBase audit block (exact order, DB defaults), identity partial unique on patient_master with the verbatim predicate `WHERE id_no IS NOT NULL AND merged_into IS NULL`, `uk_patient_number_rule_active ... WHERE active = 1 AND deleted = 0`, archive_status varchar(16) one-way machine with no CHECK, NO physical FKs, guarded `WHERE NOT EXISTS` seed. **Amendment #1 applied**: `stamp_value varchar(16) NULL` (runtime state, NULL until first issuance) + `idx_patient_number_rule_type`; **Amendment #2 applied**: seed `stamp = 'yyMMdd'` (Java DateTimeFormatter pattern), column comment updated; both recorded in the migration header.
- `.gitignore` line 21 made module-generic: `lumen-sev/*/src/main/resources/application-dev.yml` (verified matching both lumen-system and lumen-patient dev ymls via `git check-ignore`).

## TDD evidence

- **RED stage 0** (`./mvnw -pl lumen-patient -am test -q`, before the module existed): exit 1 — "Could not find the selected project in the reactor: lumen-patient". Log `lumen-sev/logs/b1-red0.log`.
- **RED stage 1** (scaffold + tests, no V1): exit 1 — `Tests run: 5, Failures: 3, Errors: 1` — context BOOTED (scaffold valid), assertions failed on the missing ledger: "table patient.patient_master must exist", "partial-unique index uk_patient_master_identity must exist". Log `lumen-sev/logs/b1-red1.log`.
- **GREEN** (`./mvnw -pl lumen-patient -am test -q`, with V1): exit 0 — `PatientSchemaMigrationTests: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` (surefire report). Log `lumen-sev/logs/b1-green.log`.
- **L1 compile** (`./mvnw compile -q` from `lumen-sev/`, JDK 11 exported): exit 0; module built in the reactor (also proven by the `-pl lumen-patient -am` run). Log `lumen-sev/logs/b1-compile.log`.

Test assertions (CONTAINS-style, shared long-lived DB): five tables in schema `patient`; per-table id-first/audit-block-tail column proof; both partial uniques' indexdef fragments (UNIQUE + columns + both predicate clauses each); zero physical FKs per table; live seed row (`pat_id`, active=1, serial_length=4, `stamp='yyMMdd'`, stamp_value NULL).

## Files changed

Module: `lumen-sev/lumen-patient/` (pom, PatientApplication, security/PatientUserDetailsService, application.yml, application-dev.yml.example, db/migration/V1__patient_domain.sql + .gitignore, 4 test files) · `lumen-sev/pom.xml` · `.gitignore` · `openspec/coordination/init-project-skeleton.md`.

## Issues or concerns

- **Both B1 amendments landed** (coordinator messages received mid-batch, before V1 was written — no shared-server repair was needed): `stamp_value varchar(16) NULL` + `idx_patient_number_rule_type`, and seed `stamp='yyMMdd'`; the brief's seed value `'yymmdd'` and its test assertion were superseded accordingly.
- Shared test server now carries the real `patient` schema: five tables + history table + 1 seed rule row. That is the module's legitimate permanent ledger state (not test residue — no fixtures were written this batch; the minimal base has no run-admin/sweep hooks by design).
- Framework `@Value` audit done before writing patient's yml: required keys = token.*, xss.* only (upload.home and swagger.* have defaults; permit-all/dispose are defaulted ConfigurationProperties) — all committed values are safe placeholders.
- Name length for `patient_master.name` was unspecified in the brief; chose `varchar(64)` matching the platform's `user_info.name` precedent (noted in the DDL only, no behavior).

## Self-review

- Completeness: all three tasks + both amendments + the .gitignore watch item done; nothing from tasks 2.x–4.x built.
- Quality: house patterns followed (AuthTestEnv/AuthIntegrationTestBase mirror; V1__auth_schema DDL style; naming.md single-domain shell).
- Discipline: no full-reactor suite run (task 4.4 owns it); no lumen-system/framework/common/openspec-specs edits; stage-by-explicit-path only.
- Testing: red/green honest (module-absent → context-boot → assertion fail → 5/5 green).
