# Batch 1 Brief: Test-server integration base (tasks 1.1-1.3)

## Goal

lumen-auth's tests connect directly to the team test server (PostgreSQL + Redis), with an
idempotent startup bootstrap, per design D3. Deliver the abstract test base class, prove
red→green, and prove the "unconfigured = explicit failure" semantics.

## Task text (tasks.md 1.1-1.3)

- **1.1** gitignored `lumen-sev/.env-test.local` (`AUTH_TEST_*` vars, user-provided) +
  .gitignore entry. Verify: file exists, `git check-ignore` hits, var names match design D3.
- **1.2** `AuthIntegrationTestBase` (abstract base): startup bootstrap (connect `postgres`
  maintenance DB → `CREATE DATABASE lumen` existence guard → create `auth` schema
  idempotently); app datasource `currentSchema=auth` connecting to `lumen`; Redis to the
  test server; Flyway enabled. Verify: a minimal `@Test` (context loads + connects to the
  real DB) red first (red = explicit unconfigured failure), then green.
- **1.3** Prove "unconfigured = explicit failure": with no env file/vars the test FAILS
  explicitly (clear configuration error, never a silent skip). Verify: report contains
  unconfigured failure evidence + configured green evidence.

## Environment facts (all verified by the coordinator)

- Working dir: `/Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev` (Maven
  aggregator; build ONLY from here or with `-pl <module> -am` — reactor-only rule).
- **Every mvnw run needs**: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
- `lumen-sev/.env-test.local` ALREADY EXISTS (coordinator-created; host/ports prefilled,
  secret fields empty — the user fills them in parallel). `.gitignore` line 20 already
  covers it. Do not duplicate either.
- Exact env var names (fixed; the base class must read exactly these):
  `AUTH_TEST_PG_HOST`, `AUTH_TEST_PG_PORT`, `AUTH_TEST_PG_USER`, `AUTH_TEST_PG_PASSWORD`,
  `AUTH_TEST_DB` (default `lumen`), `AUTH_TEST_REDIS_HOST`, `AUTH_TEST_REDIS_PORT`,
  `AUTH_TEST_REDIS_PASSWORD` (optional/empty allowed).
- Framework/common are **verbatim copies — NEVER edit anything under
  `lumen-sev/framework/` or `lumen-sev/common/`**. All code goes in `lumen-auth`
  (`org.birdbro.lumen.auth.*`). Class headers: `@author birdbro`. English comments only.
- Existing lumen-auth test files (context: skeleton change):
  - `lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthApplicationTests.java` —
    `@SpringBootTest @ActiveProfiles("test") @Import(AuthTestStubConfig.class)`; asserts
    only service identity. The stub (`AuthTestStubConfig`) provides the `UserDetailsService`
    bean the copied `SecurityConfig` requires (the real implementation ships in Batch 3 —
    KEEP the stub + its import for now).
  - `lumen-auth/src/test/resources/application-test.yml` — carries a DUMMY unreachable
    datasource URL (skeleton's zero-infra trick) + `token.*`/`xss.*` dummies + Nacos-off
    flags (Nacos off also comes from `bootstrap.yml` test-profile document — keep both).

## Design decisions (binding for this batch)

1. **New base class** `org.birdbro.lumen.auth.AuthIntegrationTestBase` (abstract, test
   sources): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("test")`.
   No `@Testcontainers`/Testcontainers anywhere (design D3 rework: team server, not
   containers).
2. **Env loader**: locate `.env-test.local` by walking UP from `user.dir` (surefire's
   working dir is the MODULE dir `lumen-sev/lumen-auth`, the file sits at `lumen-sev/`),
   then overlay `AUTH_TEST_*` system environment on top of file values. A small helper
   (e.g. `AuthTestEnv`) exposes `require(name)`; missing required vars →
   `IllegalStateException` naming every missing variable and the expected file path —
   that exception is the DESIGNED explicit-failure signal (it must surface in surefire
   output as the failure cause).
3. **Bootstrap** (once per JVM, e.g. in the `@DynamicPropertySource` static registration):
   plain JDBC (DriverManager, PG driver 42.2.18 already on the classpath via framework)
   to `jdbc:postgresql://HOST:PORT/postgres` → if `SELECT 1 FROM pg_database WHERE
   datname = '<AUTH_TEST_DB>'` empty → `CREATE DATABASE <db>` (autocommit) → connect
   `<db>` → `CREATE SCHEMA IF NOT EXISTS auth`.
4. **@DynamicPropertySource** injects: `spring.datasource.url` =
   `jdbc:postgresql://HOST:PORT/<db>?currentSchema=auth`, username/password;
   `spring.redis.host/port` (+ `spring.redis.password` only when non-empty);
   `spring.flyway.default-schema=auth` (flyway-core comes from Boot's managed version —
   add the dependency WITHOUT a version tag to `lumen-auth/pom.xml`).
5. **application-test.yml**: REMOVE the dummy datasource block (dynamic props now supply
   the real one; leaving it would mask typos). KEEP `token.*`/`xss.*` dummies and all
   Nacos-off flags. Update the file's comments to state the new test-server semantics.
6. **Convert `AuthApplicationTests`** to extend the base (keep the stub import for now):
   its `contextLoads` test becomes the minimal green proof — context loads AND a real
   connection to the test server works (e.g. `SELECT 1` through the DataSource) AND
   Flyway ran (flyway_schema_history queryable in the `auth` schema). Keep the service
   identity asserts.

## TDD evidence required

1. **RED + unconfigured-proof (1.2 + 1.3 in one run)**: with the test converted and the
   base class present, temporarily move `.env-test.local` aside (e.g. `mv
   .env-test.local .env-test.local.hold`) and run:
   `cd lumen-sev && ./mvnw -pl lumen-auth -am test -q` → capture the explicit
   configuration failure (the IllegalStateException text). Restore the file immediately.
2. **GREEN (1.2)**: run the same command with the file present. If the user has not yet
   filled the secret fields (`AUTH_TEST_PG_USER`/`AUTH_TEST_PG_PASSWORD`), the run fails
   with the same explicit signal — do NOT invent credentials, do NOT weaken the loader;
   report `DONE_WITH_CONCERNS: awaiting credentials` and stop (the coordinator re-runs
   the green proof once the user confirms).
3. Report both evidence blocks verbatim (command + failure/pass excerpt) in your report.

## Commit

`✨ feat(sev): Batch 1 - test-server integration base, explicit unconfigured failure (auth-rbac-foundation 1.1-1.3)`
(from `lumen-sev/` repo root; include the yml/pom/test changes; do NOT commit
`.env-test.local` — it is gitignored; verify with `git status`).

## Report

Write to `openspec/sdd/reports/auth-b1-implementer.md`: what you implemented, TDD
evidence (RED/unconfigured + GREEN), files changed, self-review, concerns.
