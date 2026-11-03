# Spec Delta

## MODIFIED Requirements

### Requirement: Externally Provisioned Data Services
PostgreSQL and Redis SHALL be provided by the deployment environment - dedicated hospital
machine-room servers or cloud managed instances (e.g. Aliyun RDS). No lumen artifact
(compose, chart, script) SHALL deploy these services. The stack carries NO service registry
and NO remote config center (user decision 2026-11-03: configuration is local, 随访
fu-launch shape). The Docker directory is reserved for lumen application containers and the
nginx entry (deployed by a later change).

#### Scenario: No data-service deployment artifact
- **WHEN** the repository is inspected
- **THEN** no compose/chart/script deploys PostgreSQL or Redis
- **AND** the database/schema creation script exists as a DBA handoff artifact
  (`docs/deployment/pg-init.sql`)

### Requirement: Connection via Environment, Never Committed
Service connections to the external data services SHALL be configured through environment
variables or the GITIGNORED profile file (`application-dev.yml`, with the committed
`application-dev.yml.example` template - the 随访 fu-launch pattern). Connection values
MUST NOT be committed to the repository; committed `application.yml` carries feature
defaults and explicit replace-me placeholders only.

#### Scenario: Addresses come from the environment
- **WHEN** a service connects to PostgreSQL or Redis
- **THEN** host/port/credentials resolve from environment variables or the gitignored
  profile file
- **AND** no committed file contains a real instance address or credential
