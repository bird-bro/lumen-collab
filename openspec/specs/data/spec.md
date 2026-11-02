# Data Model

## Purpose
Defines data models, entity schemas, and database conventions for all stacks.

## Requirements

### Requirement: Entity Schema Documentation
Each core entity MUST be documented with its fields, types, and constraints.

#### Scenario: Standard entity fields
- **WHEN** a new entity is defined
- **THEN** it includes at minimum `id` (bigint, PK, snowflake generated at application
  layer, PG sequence fallback) and the OPS `EntityBase` audit contract (delivered by the
  copied `framework`/`common`):
  `deleted` (logical delete flag, `0` = active, `1` = deleted),
  `create_by` / `update_by` (operator id, filled on INSERT and INSERT_UPDATE respectively by
  `MetaObjectConfig`, from the authenticated user context),
  and `create_time` / `update_time` (persisted via the database defaults - `EntityBase`
  carries no `FieldFill` on them, exactly as the OPS source)
- **AND** `create_time` / `update_time` serialize as `yyyy-MM-dd HH:mm:ss` in GMT+8

<!-- synced from: init-backend-skeleton 2026-11-02 -->
### Requirement: Database-per-Service
Each service owns exactly one PostgreSQL database (`lumen_auth` / `lumen_patient` / `lumen_ris` / `lumen_us` / `lumen_endo` / `lumen_integration`). Cross-service data access MUST go through API/events, never cross-DB queries.

#### Scenario: Service needs another service's data
- **WHEN** `lumen-radiology` needs patient demographics
- **THEN** it calls `lumen-patient` via OpenFeign (contract in `lumen-sev/lumen-api/lumen-patient-api`), never queries `lumen_patient` DB directly

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
