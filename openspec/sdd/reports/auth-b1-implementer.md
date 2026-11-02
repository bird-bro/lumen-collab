# Batch 1 Implementer Report - test-server integration base (tasks 1.1-1.3)

Status: **DONE_WITH_CONCERNS** - all code delivered and the unconfigured-explicit-failure
semantics are PROVEN; the configured GREEN run awaits the user's
`AUTH_TEST_PG_USER` / `AUTH_TEST_PG_PASSWORD` (fields still empty at run time; not
invented per the brief). Commit: `bb2815e`.

## What was implemented

- **1.1** `lumen-sev/.env-test.local` (coordinator-created, user fills secrets) verified:
  file exists, `git check-ignore -v` hits root `.gitignore:20`, variable names match
  design D3 exactly. No duplication of either file/entry.
- **1.2** Test-server integration base, all in `org.birdbro.lumen.auth` (test sources):
  - `AuthTestEnv` - locates `.env-test.local` by walking UP from the JVM working dir
    (surefire runs in `lumen-sev/lumen-auth`, file at `lumen-sev/`), parses `KEY=VALUE`
    (comments/quotes tolerated), overlays non-blank `AUTH_TEST_*` system env on top;
    `require(String...)` throws the DESIGNED explicit-failure `IllegalStateException`
    naming EVERY missing variable and the expected file path.
  - `AuthTestServerBootstrap` - once-per-JVM (double-checked-lock guard), plain JDBC
    (DriverManager, PG 42.2.18 via framework): `postgres` maintenance DB ->
    `pg_database` existence guard -> `CREATE DATABASE "<db>"` (autocommit; identifier
    validated `[A-Za-z0-9_]+`) -> `CREATE SCHEMA IF NOT EXISTS auth` (idempotent).
  - `AuthIntegrationTestBase` (abstract) - `@SpringBootTest(RANDOM_PORT)` +
    `@ActiveProfiles("test")`; `@DynamicPropertySource` registers
    `spring.datasource.{url,username,password}` with
    `jdbc:postgresql://HOST:PORT/<db>?currentSchema=auth`, `spring.redis.{host,port}`
    (+ `spring.redis.password` only when non-empty), `spring.flyway.default-schema=auth`.
  - `lumen-auth/pom.xml`: `flyway-core` WITHOUT version tag (Boot 2.5.15 manages ->
    resolves **7.7.3**; verified via `dependency:tree`; 7.7.3 source-verified to create
    `flyway_schema_history` on `migrate()` even with zero migrations, so the Flyway
    assertion holds before Batch 2's V1/V2 exist).
- **1.3** Unconfigured = explicit failure: `AuthTestEnv.require` runs during context
  creation (before refresh), so the `IllegalStateException` surfaces as the surefire
  failure cause. No silent skip anywhere; no Testcontainers anywhere.

Files also touched: `application-test.yml` - dummy datasource block REMOVED (dynamic
props are the single datasource source of truth; a static one would mask an unconfigured
server), Nacos-off flags + token/xss dummies kept, comments rewritten for the
test-server semantics. `AuthApplicationTests` - converted to extend the base, stub
import kept, identity asserts kept, plus real-DB assertions: `current_schema()` = `auth`,
`SELECT 1` through the pooled DataSource, `SELECT COUNT(*) FROM auth.flyway_schema_history`.

## TDD evidence

### RED / unconfigured (1.2 + 1.3 in one run)

Command (env file temporarily moved aside, restored immediately after in the same shell):
```
mv .env-test.local .env-test.local.hold
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw -pl lumen-auth -am test -q
mv .env-test.local.hold .env-test.local   # restored; verified afterwards (717 bytes, ignored)
```
Exit 1 (`MVN_EXIT=1`); reactor reached lumen-auth only after framework/common built clean
(they currently ship zero test sources - their zero-dependency guarantee is unchanged).
Verbatim failure excerpt (also in `lumen-auth/target/surefire-reports/`):
```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.442 s <<< FAILURE! - in org.birdbro.lumen.auth.AuthApplicationTests
java.lang.IllegalStateException: Failed to load ApplicationContext
Caused by: java.lang.IllegalStateException:
lumen-auth's data tests fail explicitly by design: the team test server connection is not configured (specs/service-topology: unconfigured connections are never silently skipped).
Missing required variables: [AUTH_TEST_PG_HOST, AUTH_TEST_PG_PORT, AUTH_TEST_PG_USER, AUTH_TEST_PG_PASSWORD, AUTH_TEST_REDIS_HOST, AUTH_TEST_REDIS_PORT]
The gitignored env file .env-test.local was NOT FOUND searching upward from /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev/lumen-auth - expected at the lumen-sev/ directory level. Provide the AUTH_TEST_* variables there or as environment variables.
```

### GREEN (1.2) - NOT YET ACHIEVED: awaiting credentials

Same command with the file present. `AUTH_TEST_PG_USER`/`AUTH_TEST_PG_PASSWORD` are still
empty (user fills them in parallel), so the designed explicit signal fired again - this
run doubles as proof the FILE path resolves correctly (host/port/redis satisfied from the
file; only the two blank secrets named):
```
MVN_EXIT=1
Caused by: java.lang.IllegalStateException:
lumen-auth's data tests fail explicitly by design: ...
Missing required variables: [AUTH_TEST_PG_USER, AUTH_TEST_PG_PASSWORD]
Provide them in the gitignored env file /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev/.env-test.local (AUTH_TEST_* keys) or as AUTH_TEST_* environment variables (environment overrides the file).
```
Credentials were NOT invented and the loader was NOT weakened. The coordinator re-runs
`cd lumen-sev && ./mvnw -pl lumen-auth -am test -q` once the user confirms; expected green
assertions: context loads, `current_schema()`=auth, `SELECT 1`=1, `flyway_schema_history`
queryable in `auth`.

## Files changed (commit bb2815e)

- `lumen-sev/lumen-auth/pom.xml` (+8)
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthApplicationTests.java`
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthIntegrationTestBase.java` (new)
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthTestEnv.java` (new)
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthTestServerBootstrap.java` (new)
- `lumen-sev/lumen-auth/src/test/resources/application-test.yml`

`.env-test.local` NOT committed (gitignored; staged-list grep count 0; `git status` clean
after commit; no `.env-test.local.hold` leftover).

## Self-review findings

- Framework/common untouched (verbatim guarantee intact); all new code in
  `org.birdbro.lumen.auth`, `@author birdbro` headers, English comments.
- Flyway version initially assumed 7.15.0 from Boot 2.5.15 memory - WRONG, resolved 7.7.3;
  pom comment corrected and the 7.7.3 sources were fetched and checked (same
  empty-schema -> `schemaHistory.create(false)` path; `createSchemas` default true), so
  the green-proof Flyway assertion remains valid.
- Bootstrap runs during context creation before refresh: unreachable server fails fast
  (`connectTimeout=10` on bootstrap URLs) with a named-user error message.
- `spring.redis.password` registered only when non-empty (contract: optional/empty
  allowed); Batch 1 exercises no Redis command (Lettuce connects lazily), per brief scope.
- Env-var overlay precedence (system env beats file) code-reviewed; both file-present and
  file-absent paths exercised empirically in the two runs above.

## Issues or concerns

1. **Green proof pending credentials** (the only blocker; DONE_WITH_CONCERNS per brief).
   After the user fills the two secret fields, the coordinator re-runs the green command;
   nothing else should need to change.
2. Shared test server: first green run will `CREATE DATABASE lumen` (existence-guarded)
   and create `auth` + `flyway_schema_history` there - intended D3 bootstrap behavior.
3. If the test server's Redis requires a password and `AUTH_TEST_REDIS_PASSWORD` is left
   empty, context load still succeeds (lazy connect) - Redis correctness is exercised in
   later batches, not Batch 1 (per brief scope).

---

## ADDENDUM (coordinator, 2026-11-02): green evidence + fix commit 9da15e9

The predicted-green assumption ("nothing else should need to change") was wrong at the
Boot layer. With credentials supplied, three independent context-load blockers surfaced
and were fixed in commit `9da15e9` (coordinator-applied, reviewed and approved by the
Batch 1 review):

1. Boot 2.5.15's `FlywayAutoConfiguration.checkLocationExists` rejects an EMPTY
   `classpath:db/migration` (`FlywayMigrationScriptMissingException`) - relaxed
   test-only via `spring.flyway.check-location=false` (AuthIntegrationTestBase).
2. Flyway 7.x requires `default-schema` to be a member of `schemas` -
   `spring.flyway.schemas=auth` added alongside `default-schema`.
3. The skeleton's `assertEquals("8081", server.port)` identity assert is unsatisfiable
   under the base's RANDOM_PORT (Boot resolves server.port=0) - replaced by
   `local.server.port` set-and-positive.

**Configured GREEN evidence** (cd lumen-sev && ./mvnw -pl lumen-auth -am test -q):

```
2026-11-02 16:36:10.217  INFO ... Database: jdbc:postgresql://220.181.78.238:54322/lumen (PostgreSQL 14.11)
2026-11-02 16:36:10.390  WARN ... Flyway upgrade recommended: ... 14.11 is newer than this version of Flyway and support has not been tested. The latest supported version ... is 13.
2026-11-02 16:36:10.568  INFO ... Successfully validated 0 migrations
2026-11-02 16:36:10.795  INFO ... Current version of schema "auth": << Empty Schema >>
2026-11-02 16:36:11.159  INFO ... Tomcat started on port(s): 58146 (http)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   (surefire: Tests run: 1, Failures: 0, Errors: 0)
MVN_EXIT=0
```

Server facts: PostgreSQL 14.11; Flyway resolved to 7.7.3 (Boot 2.5.15 managed - NOT
7.14.0/7.15 as earlier comments assumed); PG-14-vs-Flyway-7.7.3 is a WARN-only
recommendation (decompile-verified by the review: no "Unsupported Database" throw).
Watch-item for Batch 2 recorded; a version pin would be a lumen-only deviation requiring
a proposal per the train rules.

Review verdict: Approved at HEAD (9da15e9) - see auth-b1-reviewer.md.
