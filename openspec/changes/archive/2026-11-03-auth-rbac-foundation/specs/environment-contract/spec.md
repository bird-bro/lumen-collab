# Spec Delta

## MODIFIED Requirements

### Requirement: Six Per-Service Databases
The deployment environment's PostgreSQL instance SHALL contain **one database `lumen`**
containing one schema per service (`auth`, `patient`, `radiology`, `us`, `endo`,
`integration`), created once by the DBA or deployment engineer using the handoff script
(`docs/deployment/pg-init.sql`). Each service connects with its own role confined to its
schema.

#### Scenario: Six databases exist before services start
- **WHEN** a lumen service that requires its schema starts
- **THEN** the `lumen` database and its schema already exist (created from
  `docs/deployment/pg-init.sql`)
