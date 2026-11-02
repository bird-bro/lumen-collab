# Spec Delta

## MODIFIED Requirements

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
