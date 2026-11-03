# Data Model

## Purpose
Defines data models, entity schemas, and database conventions for all stacks.

## Requirements

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
### Requirement: Entity Schema Documentation
Each core entity MUST be documented with its fields, types, and constraints.

#### Scenario: Standard entity fields
- **WHEN** a new entity is defined
- **THEN** it includes at minimum `id` (Integer, PK, database auto-increment - user
  decision 2026-11-02, OPS/随访 lineage; the former bigint-snowflake clause is withdrawn)
  and the OPS `EntityBase` audit contract (delivered by the copied
  `framework`/`common`):
  `deleted` (logical delete flag, `0` = active, `1` = deleted),
  `create_by` / `update_by` (operator id, filled on INSERT and INSERT_UPDATE respectively by
  `MetaObjectConfig`, from the authenticated user context),
  and `create_time` / `update_time` (persisted via the database defaults - `EntityBase`
  carries no `FieldFill` on them, exactly as the OPS source)
- **AND** `create_time` / `update_time` serialize as `yyyy-MM-dd HH:mm:ss` in GMT+8

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
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

### Requirement: Migration-Only Schema Changes
All schema changes MUST ship as Flyway migrations in the owning service's `src/main/resources/db/migration`. Manual DDL against any environment is forbidden.

#### Scenario: New column needed
- **WHEN** a service adds a column
- **THEN** a new versioned Flyway script is added and applied on startup; no hand-run SQL

### Requirement: Flexible Fields via JSONB
Department-specific / evolving structures (report templates, structured findings, custom forms) MUST use `jsonb` columns with GIN indexes instead of frequent DDL.

#### Scenario: New structured report field
- **WHEN** a department adds a structured finding field
- **THEN** it is stored inside the existing `jsonb` column; no table alteration required
