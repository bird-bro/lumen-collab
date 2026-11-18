# patient-domain Batch 1 Brief — tasks 1.1–1.3 (module scaffold + Flyway V1 + schema proof)

Change: `openspec/changes/patient-domain/` (spec-driven, SDD mode). Design: `design.md` D1/D3/D6.
Tasks source: `tasks.md` lines 5–7. Do NOT load other changes or unrelated specs.

- **1.1** Create `lumen-sev/lumen-patient`: pom (parent `lumen-sev`, deps framework+common), aggregator `<module>` line, app class `PatientApplication` at `org.birdbro.lumen.patient` with `scanBasePackages="org.birdbro"` + wildcard `@MapperScan(basePackages = "org.birdbro.**.mapper")`, `application.yml` (name `lumen-patient`, port 8082, flyway schemas/default-schema `patient`). Verify: `./mvnw compile -q` from `lumen-sev/` exit 0, module in reactor.
- **1.2** Add the 8082 row to the port table in `openspec/coordination/init-project-skeleton.md`.
- **1.3** `src/main/resources/db/migration/V1__patient_domain.sql`: five tables + partial unique identity index + default rule seed. Verify: data-layer test boots the ledger against the team test-server PG and asserts the five tables, the index, and the seed row.

## Coordinator-pinned binding decisions (apply, do not re-litigate)

1. **Identity columns live on `patient_master`.** `id_type` + `id_no` are patient_master columns (the natural identity document); the partial unique index is ON patient_master: `uk_patient_master_identity ON patient_master (id_type, id_no) WHERE id_no IS NOT NULL AND merged_into IS NULL` (predicate verbatim from the task). `patient_identifier` carries ONLY additional numbers (visit card / physical / outpatient / inpatient / HIS) with `id_no NOT NULL` and NO unique index. This is the only reading consistent with `merged_into` being in the index predicate (merged rows drop out of the key without touching identifier rows).
2. Archive status = `varchar(16)` NOT NULL, values `'PROVISIONAL'`/`'RESOLVED'`, service-enforced one-way machine, no DB CHECK (house DDL style).
3. `merged_into integer NULL`, `oid integer NULL` (issuer org id, service fills when known), `pat_id varchar(64) NOT NULL` (PROVISIONAL placeholders also get one).
4. Exactly-one-active-rule backstop: `uk_patient_number_rule_active ON patient_number_rule (rule_type) WHERE active = 1 AND deleted = 0`. Multiple inactive rules per type allowed.
5. Rule-delete ruling (contract required edge case — carry to later batches): deleting the ACTIVE rule is REJECTED (A-class conflict error); only inactive rules delete (house soft-delete).
6. Default rule seed = guarded insert (`WHERE NOT EXISTS`, house seed discipline — a history-row repair must be able to re-run V1): `('pat_id', prefix '', suffix '', serial_length 4, serial_cur 0, is_stamp 1, stamp 'yyMMdd', stamp_value NULL, active 1)`. [AMENDED 2026-11-17 by coordinator: + `stamp_value varchar(16) NULL` column (last-issued stamp token — required by the ruled stamp-day-reset behavior; `stamp` = the format, `stamp_value` = runtime state), `idx_patient_number_rule_type (rule_type)`, and `stamp` stores a Java date pattern directly (`yyMMdd`) so format changes never need code.]
7. DDL house style = `lumen-sev/lumen-system/src/main/resources/db/migration/V1__auth_schema.sql`: `serial PRIMARY KEY` ids (Integer AUTO), EntityBase audit block exact columns+order (`deleted/create_by/create_time/update_by/update_time`, DB defaults), logical-delete partial uniques. **NO physical FKs in this module** (execution contract is explicit; V1__auth_schema's org_dep REFERENCES is NOT the pattern here).
8. `gender integer` (0 unknown / 1 male / 2 female), `birth_date date` — typed columns per design D4 (never EIS's string-birthday).

## Column map (binding)

- **patient_master**: id, pat_id, id_type, id_no, name (NULLable — unnamed emergency), pinyin varchar(128) NULL (server-generated), gender, birth_date, phone varchar(32), nation varchar(32), address varchar(255), archive_status, merged_into, oid + audit block. Indexes: the identity partial unique, `idx_patient_master_pat_id (pat_id)`, `idx_patient_master_merged_into (merged_into)`.
- **patient_identifier**: id, patient_id integer NOT NULL, id_type varchar(32) NOT NULL, id_no varchar(128) NOT NULL + audit block. Indexes: `(patient_id)`, `(id_no)` (card-number resolve lookup).
- **patient_number_rule**: id, rule_type varchar(32) NOT NULL, prefix varchar(32) NOT NULL DEFAULT '', suffix varchar(32) NOT NULL DEFAULT '', serial_length integer NOT NULL DEFAULT 4, serial_cur integer NOT NULL DEFAULT 0, is_stamp integer NOT NULL DEFAULT 0, stamp varchar(16) NULL, active integer NOT NULL DEFAULT 1 + audit block. Indexes: the active-rule partial unique.
- **patient_event_outbox**: id, event_type varchar(32) NOT NULL, patient_id integer NOT NULL, payload jsonb NOT NULL, status varchar(16) NOT NULL DEFAULT 'PENDING' + audit block. Indexes: `(status)`, `(patient_id)`.
- **patient_merge_log**: id, merged_id integer NOT NULL, survivor_id integer NOT NULL, reason varchar(255) NULL + audit block. Indexes: `(merged_id)`, `(survivor_id)`.
- Migration header comment carries the design rationale (V1__auth_schema style).

## Files to create/modify — stage ONLY these exact paths

The working tree carries UNRELATED residue from parallel changes (exam-item* in lumen-sev/lumen-web + openspec/). Never `git add -A` / `git add .`; stage by explicit path.

1. `lumen-sev/pom.xml` — one `<module>lumen-patient</module>` line
2. `lumen-sev/lumen-patient/pom.xml` — copy lumen-system's shape: parent + framework + common + spring-boot-starter-web + spring-boot-starter-validation + flyway-core + commons-pool2 + test starter. NO bouncycastle pins/exclusions (patient has no SM2 usage; common's transitive bcprov-jdk16 is inert here)
3. `lumen-sev/lumen-patient/src/main/java/org/birdbro/lumen/patient/PatientApplication.java` — single-domain OPS shape (naming.md): app class at the SERVICE root package; `scanBasePackages="org.birdbro"` + wildcard `@MapperScan` + `@author birdbro` javadoc
4. `lumen-sev/lumen-patient/src/main/java/org/birdbro/lumen/patient/security/PatientUserDetailsService.java` — see Watch item 1
5. `lumen-sev/lumen-patient/src/main/resources/application.yml` — see Reference 4
6. `lumen-sev/lumen-patient/src/main/resources/application-dev.yml.example` — datasource `currentSchema=patient,public` + redis, fu shape
7. `lumen-sev/lumen-patient/src/main/resources/db/migration/V1__patient_domain.sql`
8. `lumen-sev/lumen-patient/src/main/resources/db/migration/.gitignore` — COPY the `!*.sql` negation pattern from lumen-system's db/migration/.gitignore (machine-level global gitignore hides `*.sql` — recorded footgun, do not skip)
9. `lumen-sev/lumen-patient/src/test/java/org/birdbro/lumen/patient/PatientTestEnv.java` + `PatientTestServerBootstrap.java` + `PatientIntegrationTestBase.java` + `PatientSchemaMigrationTests.java`
10. `openspec/coordination/init-project-skeleton.md` — the 8082 row
11. Check `lumen-sev/.gitignore` covers `application-dev.yml` module-generically; extend if lumen-system-scoped

## Reference patterns to read (do not re-derive)

1. `lumen-sev/pom.xml` — aggregator; children resolve lumen-bom through its import (reactor-only rule)
2. `lumen-sev/lumen-system/pom.xml` — module pom shape
3. `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/SystemApplication.java` — app-class shape/javadoc; patient is single-domain so the class lives at `org.birdbro.lumen.patient`
4. `lumen-sev/lumen-system/src/main/resources/application.yml` + `application-dev.yml.example` — copy shape, adjust: port 8082, name `lumen-patient`, flyway `schemas: patient` / `default-schema: patient`, swagger description, **omit the `upload.*` block** (framework keeps location-less parity without the key), **omit login/auth-key permit-all** (patient has no login — every path token-guarded; if `security.permit-all` binding needs the key, set an empty list — context test decides), copy `token.*` / `xss.*` / `dispose.*` / mybatis-plus / jackson / mvc / logging verbatim (framework-demanded `@Value` keys — missing ones fail context load), log file `./logs/lumen-patient.log`
5. `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/manage/{AuthTestEnv,AuthTestServerBootstrap,AuthIntegrationTestBase}.java` — patient copies the trio as `PatientTestEnv`/`PatientTestServerBootstrap`/`PatientIntegrationTestBase` in `org.birdbro.lumen.patient`: SAME `.env-test.local` file, SAME `AUTH_TEST_*` keys (shared team server; note in javadoc that the file/keys are the platform-wide test contract), `currentSchema=patient,public`, schema constant `patient`, `@ActiveProfiles("test")` + `@DynamicPropertySource` datasource/redis/flyway injection. Keep the base MINIMAL — no run-admin cloning (patient API tests arrive in later batches; the base is extensible)
6. `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/manage/AuthSchemaMigrationTests.java` — CONTAINS-style assertions (never exact row counts — long-lived shared DB); assert via `information_schema.tables` (5 tables in schema `patient`), `pg_indexes.indexdef` (identity index: name, uniqueness, both predicate clauses), and the seed row (`rule_type='pat_id'`, `active=1`, `serial_length=4`, `stamp='yyMMdd'` — amendment 2 superseded the original 'yymmdd')
7. `docs/deployment/pg-init.sql` — `CREATE SCHEMA IF NOT EXISTS patient` already exists there; NO edit needed

## Watch items / traps

1. **UserDetailsService bean REQUIRED for context load.** Framework `SecurityConfig` (`lumen-sev/framework/src/main/java/org/birdbro/framework/config/SecurityConfig.java:32`) constructor-injects `UserDetailsService`; `scanBasePackages="org.birdbro"` pulls that config into patient. Ship `PatientUserDetailsService` (`loadUserByUsername` always throws `UsernameNotFoundException`); javadoc states: the JWT request path never loads UserDetails (`JwtAuthenticationTokenFilter` builds the Authentication from token claims) — this bean only satisfies the framework constructor until a future login-bearing change replaces it.
2. **JDK 11**: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home` before every `./mvnw` call (foreground shells lose JAVA_HOME; the Lombok TypeTag crash masquerades as broken code).
3. **Reactor-only builds**: always from `lumen-sev/`; a standalone `-f lumen-patient/pom.xml` fails until lumen-bom is installed.
4. `PasswordCipherHelper` consumers are all lumen-system manage classes (verified) — no password-crypto config in patient's yml.
5. `lumen-sev/.env-test.local` is present — data-layer tests run for real. Unconfigured = explicit failure (designed signal, never skip).

## TDD + verification

- `PatientSchemaMigrationTests` IS the red: with no module there is no test (compile failure = red evidence); after scaffold, confirm the assertions fail-red only if V1 is wrong, then green. Capture RED/GREEN evidence (command + exit + counts) — RED may legitimately be "module did not exist / context failed on X" — record the honest form.
- L1 per task: `./mvnw compile -q` (task 1.1), `./mvnw -pl lumen-patient -am test -q` (task 1.3). Redirect output to a log file (`lumen-sev/logs/` or /tmp); report exit code + test counts + log path only, never full logs.
- Do NOT run the full reactor suite this batch (task 4.4 owns it). Do NOT touch lumen-system/framework/common/openspec specs.

## Commit

Stage only the exact file list above. Message (repo conventional style):
`✨ feat(sev): patient-domain b1 — lumen-patient 模块脚手架 + V1 patient 账本（五表+身份部分唯一索引+默认影像号规则种子）+ 端口表 8082 行`

## Report

Write `openspec/sdd/reports/patient-domain-b1-report.md` (<80 lines): what you implemented, TDD evidence (RED/GREEN commands + exit/counts), files changed, self-review findings, concerns. Then reply with a status line (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT + one-line summary + report path).
