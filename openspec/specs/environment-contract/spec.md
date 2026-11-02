# environment-contract Specification

## Purpose

Defines what the deployment environment (hospital machine-room servers or cloud managed
instances) MUST provide for lumen services, and how services consume it - lumen never
deploys PostgreSQL, Nacos, or Redis itself (user decision 2026-10-30, superseding the
containerized premise of init-infra-compose).

## Requirements

### Requirement: Externally Provisioned Data Services
PostgreSQL, Nacos, and Redis SHALL be provided by the deployment environment - dedicated
hospital machine-room servers or cloud managed instances (e.g. Aliyun RDS/MSE). No lumen
artifact (compose, chart, script) SHALL deploy these services. The Docker directory is
reserved for lumen application containers and the nginx entry (deployed by a later change).

#### Scenario: No data-service deployment artifact
- **WHEN** the repository is inspected
- **THEN** no compose/chart/script deploys PostgreSQL, Nacos, or Redis
- **AND** the six-database creation script exists as a DBA handoff artifact
  (`docs/deployment/pg-init.sql`)

### Requirement: Six Per-Service Databases
The deployment environment's PostgreSQL instance SHALL contain six per-service databases -
`lumen_auth`, `lumen_patient`, `lumen_radiology`, `lumen_us`, `lumen_endo`,
`lumen_integration` - materializing the DB-per-service rule (`openspec/specs/data/spec.md`).
Creation is performed once by the hospital DBA or deployment engineer using the handoff
script.

#### Scenario: Six databases exist before services start
- **WHEN** a lumen service that requires its database starts
- **THEN** its database already exists (created from `docs/deployment/pg-init.sql`)

### Requirement: Connection via Environment, Never Committed
Service connections to the external data services SHALL be configured through environment
variables or the Nacos configuration center (e.g. `NACOS_ADDR`, datasource URL/credentials,
Redis address). Connection values MUST NOT be committed to the repository - including test
resources, which carry only explicit non-credential dummies.

#### Scenario: Addresses come from the environment
- **WHEN** a service connects to PostgreSQL, Nacos, or Redis
- **THEN** host/port/credentials resolve from environment variables or Nacos
- **AND** no committed file contains a real instance address or credential
