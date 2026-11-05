# Spec Delta

## MODIFIED Requirements

### Requirement: System Log Tables
The `public.log_info` and `public.log_sign` tables SHALL be declaratively partitioned by
`RANGE (date_ym)` (the int yyyymm key column) with yearly child partitions
(`log_info_2026`/`log_info_2027`/..., bounds `FROM (YYYY00) TO (YYYY99)`) mirroring the OPS
reference database layout, PLUS a default partition `log_info_def` / `log_sign_def` as the
write safety net for years without a pre-created child (user decision 2026-11-05 - a
deliberate deviation from the reference, which has none). The primary keys SHALL become
composite `(id, date_ym)` (PostgreSQL requires the partition key in every unique
constraint); `id` remains the global identity sequence so entity-level reads and the
append-write path are unchanged. Existing rows SHALL be carried into the partitioned layout
by the conversion migration. The tables stay append-only.

#### Scenario: Log table exists on fresh database
- **WHEN** a fresh database receives the migrations
- **THEN** `public.log_info` and `public.log_sign` exist in the `public` schema as
  partitioned tables keyed `RANGE (date_ym)`
- **AND** yearly partitions for 2026 and 2027 exist plus one default partition per table

#### Scenario: Rows survive the conversion
- **WHEN** the partition conversion migration runs on a database that already has log rows
- **THEN** every previously visible row remains readable through the parent table

#### Scenario: Write into an unpartitioned year lands in the default partition
- **WHEN** a sign or operation event writes a row whose `date_ym` year has no child partition
- **THEN** the row lands in that table's default partition instead of the write failing

## ADDED Requirements

### Requirement: Sign Log Query Endpoint
The service SHALL expose a paged sign-log query under `/api/v1/system/logs/sign` (filters:
`account`, `uid`, `dateYm`; ordered by `actionTime` descending) reading `public.log_sign`,
gated server-side by the page permission `system:signlog:list` - independent of the
operation-log permission (a role may hold one without the other). Responses SHALL NOT
include the `token` column (credential material never reaches admin screens). The change is
additive: the existing `GET /api/v1/system/logs` contract is unchanged, and there is
deliberately no mutation endpoint for either log table.

#### Scenario: Query sign logs
- **WHEN** an administrator holding `system:signlog:list` queries the sign-log endpoint
- **THEN** `log_sign` rows return newest-first with the requested filters applied
- **AND** no row carries a `token` field

#### Scenario: Sign-log permission is enforced server-side
- **WHEN** a request without the `system:signlog:list` authority reaches the sign-log endpoint
- **THEN** the service rejects it (forbidden) even when the caller holds `system:log:list`
