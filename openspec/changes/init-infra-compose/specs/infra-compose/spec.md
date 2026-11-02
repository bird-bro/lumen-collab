# Spec Delta

## Purpose

Defines the hospital-form infrastructure compose contract: which containers exist, the port
contract (overridable via `.env`), the per-service database initialization, and persistence
plus health-check wiring.

## ADDED Requirements

### Requirement: Stack Declaration and Port Contract
The compose stack SHALL declare exactly three infrastructure containers - PostgreSQL,
Nacos (standalone, configuration-center role), and Redis. Host ports MUST be overridable
via `.env` (PG default `15432`, Nacos `8848`, Redis `6379`), and `.env` MUST be gitignored
while `.env.example` carries placeholders. No lumen application container and no Java
gateway container SHALL be declared.

#### Scenario: Config validates without daemon
- **WHEN** `docker compose config -q` runs from `docker/`
- **THEN** it exits 0 with no daemon required

#### Scenario: Ports follow .env
- **WHEN** `.env` changes `PG_PORT`
- **THEN** the PostgreSQL host port follows on the next `up`
- **AND** the committed tree contains no literal production credential

### Requirement: Six Per-Service Databases Initialized
PostgreSQL SHALL initialize exactly six databases on first start - `lumen_auth`,
`lumen_patient`, `lumen_radiology`, `lumen_us`, `lumen_endo`, `lumen_integration` - via an
idempotent init script mounted into the image's init directory. The DB-per-service rule
(`openspec/specs/data/spec.md`) is materialized by this initialization.

#### Scenario: Six databases exist after first start
- **WHEN** the PostgreSQL container is healthy after a first start
- **THEN** all six databases exist
- **AND** re-running the container does not duplicate or fail (idempotent init)

### Requirement: Persistence and Health-check Wiring
Data SHALL persist in named volumes (`pg-data`, `nacos-derby`, `redis-data`). Each
container SHALL declare a health-check, and dependents SHALL gate on the health status.
Restart policy SHALL be `unless-stopped` for power-loss recovery.

#### Scenario: Containers restart clean after power loss
- **WHEN** the host reboots with the Docker daemon enabled
- **THEN** all three containers restart automatically (`unless-stopped`)
- **AND** data volumes survive (volumes are named, not anonymous)
