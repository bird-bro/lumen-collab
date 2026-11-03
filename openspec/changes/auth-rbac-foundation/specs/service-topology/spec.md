# Spec Delta

## MODIFIED Requirements

### Requirement: Context Load Without Infrastructure
Each service SHALL load its Spring application context and run its tests without any
**deployed production** infrastructure. `lumen-auth`'s data-layer tests SHALL run against
the team test server's PostgreSQL and Redis (externally provisioned), with connections
read from a gitignored local env file - never committed. Unconfigured connections SHALL
fail the tests explicitly (never silently skipped). Other modules (`framework`, `common`)
keep their zero-dependency tests unchanged.

#### Scenario: Context load test with no services running
- **WHEN** `./mvnw test -q` runs from `lumen-sev/` with no deployed production services,
  and the test-server env file present
- **THEN** it exits 0
- **AND** Flyway has applied the migrations to the test server's `lumen` database and the
  mappers have been exercised against it

#### Scenario: Nacos unreachable during verification
- **WHEN** verification runs where no Nacos server exists or responds - the stack has no
  service registry and no remote config center at all since user decision 2026-11-03
  (configuration is local, 随访 fu-launch shape, design D8)
- **THEN** service discovery registration and remote config import are inactive for that
  run - the starters are removed and nothing attempts either
- **AND** the context still loads from the local `application.yml` (+ the gitignored
  profile file), so the absence is not a build failure

### Requirement: Sensitive Configuration Exclusion
Credentials and environment-specific connection values SHALL come from environment
variables or a GITIGNORED profile file (`application-dev.yml`, with the template
`application-dev.yml.example` committed - the 随访 fu-launch pattern, user decision
2026-11-03). No configuration center exists in the stack. Real secret values MUST NOT
be committed to the repository; committed `application.yml` may carry feature defaults
and explicit replace-me placeholders only.

#### Scenario: A database password is needed for a future service
- **WHEN** a service requires a credential
- **THEN** it is read from an environment variable or the gitignored
  `application-dev.yml`
- **AND** no literal secret value appears in any committed file

#### Scenario: Test-server connection unconfigured
- **WHEN** the test-server env file/variables are absent
- **THEN** `lumen-auth`'s data tests fail with an explicit configuration error - a clear
  signal, never a silent pass
- **AND** `framework`/`common` tests remain green (zero-dependency unchanged)
