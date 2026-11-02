# Spec Delta

## MODIFIED Requirements

### Requirement: Tests Against the Team Test Server
Each service SHALL load its Spring application context and run its tests without any
**deployed production** infrastructure. `lumen-auth`'s data-layer tests SHALL run against
the team test server's PostgreSQL and Redis (externally provisioned), with connections
read from a gitignored local env file - never committed. Unconfigured connections SHALL
fail the tests explicitly (never silently skipped). Other modules (`framework`, `common`)
keep their zero-dependency tests unchanged.

#### Scenario: Context load and data tests against the test server
- **WHEN** `./mvnw test -q` runs from `lumen-sev/` with the test-server env file present
- **THEN** it exits 0
- **AND** Flyway has applied the migrations to the test server's `lumen` database and the
  mappers have been exercised against it

#### Scenario: Nacos unreachable during verification
- **WHEN** no Nacos server responds at verification time
- **THEN** service discovery registration and remote config import are inactive for that
  run
- **AND** the context still loads, so the absence is not a build failure

#### Scenario: Test-server connection unconfigured
- **WHEN** the test-server env file/variables are absent
- **THEN** `lumen-auth`'s data tests fail with an explicit configuration error - a clear
  signal, never a silent pass
- **AND** `framework`/`common` tests remain green (zero-dependency unchanged)
