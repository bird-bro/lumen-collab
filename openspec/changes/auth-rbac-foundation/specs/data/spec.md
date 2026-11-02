# Spec Delta

## MODIFIED Requirements

### Requirement: Entity Schema Documentation
Each core entity MUST be documented with its fields, types, and constraints.

#### Scenario: Standard entity fields
- **WHEN** a new entity is defined
- **THEN** it includes at minimum `id` (Integer, PK, database auto-increment - user
  decision 2026-11-02, OPS/随访 lineage; the former bigint-snowflake clause is withdrawn)
  and the OPS `EntityBase` audit contract (delivered by the copied `framework`/`common`):
  `deleted` (logical delete flag, `0` = active, `1` = deleted),
  `create_by` / `update_by` (operator id, filled on INSERT and INSERT_UPDATE respectively by
  `MetaObjectConfig`, from the authenticated user context),
  and `create_time` / `update_time` (persisted via the database defaults - `EntityBase`
  carries no `FieldFill` on them, exactly as the OPS source)
- **AND** `create_time` / `update_time` serialize as `yyyy-MM-dd HH:mm:ss` in GMT+8

## REMOVED Requirements

### Requirement: Database-per-Service
Each service owns exactly one PostgreSQL database (`lumen_auth` / `lumen_patient` /
`lumen_radiology` / `lumen_us` / `lumen_endo` / `lumen_integration`). Cross-service data
access MUST go through API/events, never cross-DB queries.

**Reason**: user decision 2026-11-02 - the deployment reality is one managed PostgreSQL
instance per environment (hospital machine-room or cloud); six separate databases added
DBA overhead with no isolation gain once connection roles exist.

**Migration**: replaced by "Schema-per-Service Isolation" below - one database `lumen`,
one schema per service, one connection role per service with GRANTs confined to its own
schema, so cross-service access remains denied at the database layer.

#### Scenario: Database-per-Service is retired
- **WHEN** the data spec is inspected after this change is archived
- **THEN** the six-database requirement is replaced by Schema-per-Service Isolation
- **AND** the `lumen` database and the per-service schemas exist per the environment
  contract (`docs/deployment/pg-init.sql`)

## ADDED Requirements

### Requirement: Schema-per-Service Isolation
All services SHALL share one PostgreSQL database (`lumen`) with one schema per service
(`auth`, `patient`, `radiology`, `us`, `endo`, `integration`). Each service SHALL connect
with its own database role whose GRANTs are confined to its own schema, so cross-service
data access remains denied at the database layer. Cross-service data access MUST go through
API/events regardless.

#### Scenario: Role isolation denies cross-schema access
- **WHEN** the `lumen_auth_role` connection attempts to read a table in another service's
  schema
- **THEN** PostgreSQL denies it (privilege error)

#### Scenario: Schemas exist after initialization
- **WHEN** the initialization script completes on a fresh instance
- **THEN** the `lumen` database and the six service schemas exist
