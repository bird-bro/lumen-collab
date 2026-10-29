# Spec Delta

## MODIFIED Requirements

### Requirement: Entity Schema Documentation
Each core entity MUST be documented with its fields, types, and constraints.

#### Scenario: Standard entity fields
- **WHEN** a new entity is defined
- **THEN** it includes at minimum `id` (bigint, PK, snowflake generated at application
  layer, PG sequence fallback) and the OPS `EntityBase` audit contract (delivered by the
  copied `framework`/`common`, whose `MetaObjectConfig` fills the fields):
  `deleted` (logical delete flag, `0` = active, `1` = deleted),
  `create_by` / `update_by` (operator id, filled on INSERT and INSERT_UPDATE respectively),
  and `create_time` / `update_time`
- **AND** `create_time` / `update_time` serialize as `yyyy-MM-dd HH:mm:ss` in GMT+8
