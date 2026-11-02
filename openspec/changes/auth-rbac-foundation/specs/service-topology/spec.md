# Spec Delta

## MODIFIED Requirements

### Requirement: Context Load Without Infrastructure
Each service SHALL load its Spring application context and run its tests without any
**deployed** infrastructure - no external Nacos, PostgreSQL, Redis, or RocketMQ instance.
`lumen-auth`'s data-layer tests SHALL use ephemeral Testcontainers (PostgreSQL + Redis)
instead: the containers are created for the test run and destroyed after, so no deployed
environment is required - but a running Docker daemon IS required for those tests. Other
modules (`framework`, `common`) keep their zero-dependency tests unchanged.

#### Scenario: Context load test with no services running
- **WHEN** `./mvnw test -q` runs from `lumen-sev/` with Nacos, PostgreSQL, Redis, and
  RocketMQ all absent as deployed services, and the Docker daemon is running
- **THEN** it exits 0
- **AND** the `lumen-auth` tests have run against ephemeral containers (Flyway applied,
  mappers exercised)

#### Scenario: Nacos unreachable during verification
- **WHEN** no Nacos server responds at verification time
- **THEN** service discovery registration and remote config import are inactive for that
  run
- **AND** the context still loads, so the absence is not a build failure

#### Scenario: Docker daemon absent for lumen-auth tests
- **WHEN** the Docker daemon is not running
- **THEN** `lumen-auth`'s container-dependent tests fail with an explicit daemon error -
  a clear signal, never a silent pass
- **AND** `framework`/`common` tests remain green (zero-dependency unchanged)
