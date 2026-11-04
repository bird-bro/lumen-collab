# Spec Delta

## MODIFIED Requirements

### Requirement: Schema-per-Service Isolation
All services SHALL share one PostgreSQL database (`lumen`) with one schema per service
(`system`, `patient`, `radiology`, `us`, `endo`, `integration`). Each service SHALL connect
with its own database role whose GRANTs are confined to its own schema - with ONE
design-level exception: the platform-shared system tables (dict/log) live in the `public`
schema and every service role SHALL be granted them (user decision 2026-11-03), so their
unqualified names resolve via `search_path = <service-schema>, public`. Otherwise
cross-service data access remains denied at the database layer, and cross-service data
access MUST go through API/events regardless.

#### Scenario: Role isolation denies cross-schema access
- **WHEN** the `lumen_system_role` connection attempts to read a table in another service's
  schema
- **THEN** PostgreSQL denies it (privilege error)

#### Scenario: Schemas exist after initialization
- **WHEN** the initialization script completes on a fresh instance
- **THEN** the `lumen` database and the six service schemas exist

#### Scenario: Shared system tables are reachable from every role
- **WHEN** any service role reads `public.dict_catal` or `public.log_info`
- **THEN** the access is granted (the design-level shared-data exception)
- **AND** the role still cannot read any other service's private schema tables
