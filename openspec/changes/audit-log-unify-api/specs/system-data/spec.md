# Spec Delta — system-data (audit-log-unify-api)

## MODIFIED Requirements

### Requirement: Operation Log Recording
The `public.log_info` table (append-only audit log: `date_ym`, title, descr, `type`
INTEGER, uid, account, actionTime, spent, method, url, session, mobile, ip, os, browser,
engine, record, param, result, exception, app — no audit block) SHALL receive rows for
BOTH event families through the copied machinery: `@Log`-annotated write endpoints record
operation events via `LogAspect` + `AsyncFactory.recordAction`, and the login/logout flow
records sign events (login success/failure, logout) into the SAME table. The `type`
column carries Integer codes: operations 1=新增 2=修改 3=删除 4=授权 5=重置密码; sign
events 10=登录成功 11=登录失败 12=登出 (the copied `Constants` pair extended by the
platform). The annotation-declared String type is translated to its code exactly once,
in the operation-log insert path; a sign event passes its code through unchanged. The
sign event's session-token anchor (UUID, never the JWT) SHALL be written to the `session`
column and SHALL NOT appear in any admin query response.

#### Scenario: Annotated endpoint writes an operation log
- **WHEN** an authenticated administrator calls a `@Log(type = "新增")`-annotated write
  endpoint
- **THEN** a `public.log_info` row appears with that user's `uid`/`account`, the
  request's method/url, and `type = 1`

#### Scenario: Login records a sign event
- **WHEN** a login attempt succeeds or fails against the login endpoint
- **THEN** a `public.log_info` row appears with the account, the event code
  (10 success / 11 failure), and action time

#### Scenario: Logout records a sign event
- **WHEN** an authenticated session logs out
- **THEN** a `public.log_info` row appears with `type = 12`

#### Scenario: Credential anchor never reaches admin responses
- **WHEN** any admin query endpoint returns log rows
- **THEN** no row carries the `session` column value (the credential anchor stays
  server-side)

### Requirement: System Log Tables
The `public.log_info` table SHALL be declaratively partitioned by `RANGE (date_ym)`
(the int yyyymm key column) with yearly child partitions (`log_info_2026`/`log_info_2027`/
..., bounds `FROM (YYYY00) TO (YYYY99)`) PLUS a default partition `log_info_def` as the
write safety net for years without a pre-created child. The primary key SHALL remain
composite `(id, date_ym)`; `id` remains the global identity sequence so entity-level
reads and the append-write path are unchanged. The tables stay append-only.

`public.log_sign` SHALL BE DROPPED (roll-forward migration: partitioned parent,
yearly children, default partition, any parked-conversion residue, and the id sequence).
No data migration is performed — the early-stage table holds no data worth keeping
(user ruling 2026-11-10).

#### Scenario: Log table exists on fresh database
- **WHEN** a fresh database receives the migrations
- **THEN** `public.log_info` exists in the `public` schema as a partitioned table keyed
  `RANGE (date_ym)` with yearly partitions for 2026 and 2027 plus one default partition
- **AND** `public.log_info.type` is `integer`

#### Scenario: log_sign is gone
- **WHEN** the migration chain completes on any database
- **THEN** no `log_sign` relation remains in the `public` schema (parent, partitions,
  parked residue, and sequence all absent)

#### Scenario: Rows survive the conversion
- **WHEN** the partition conversion migration runs on a database that already has
  log_info rows
- **THEN** every previously visible row remains readable through the parent table

#### Scenario: Write into an unpartitioned year lands in the default partition
- **WHEN** a sign or operation event writes a row whose `date_ym` year has no child
  partition
- **THEN** the row lands in `log_info_def` instead of the write failing

### Requirement: Log Query Endpoint
The service SHALL expose the SINGLE paged audit-log query under `/api/v1/system/logs`
(filters: `type` Integer, `uid`, `account`, `dateYm`; ordered by `actionTime` descending,
`id` descending tie-break), gated server-side by `system:log:list`. Responses SHALL
select-exclude the `session` column — credential material never reaches admin screens
(supersedes the sign-log-query token-column exclusion with the equivalent guarantee on
the new anchor column). The sign-log endpoint `GET /api/v1/system/logs/sign` and the
`system:signlog:list` permission are REMOVED (supersedes sign-log-query D4 — the
sensitivity split collapsed: both event families carry the same UA/IP columns). The
operation-log WRITE path (`LogAspect` + `AsyncFactory` + entities/mappers/service) stays
in the shared `framework` library; `lumen-system` hosts the admin query facade. There is
deliberately no mutation endpoint.

#### Scenario: Query operation logs
- **WHEN** an administrator holding `system:log:list` queries the log endpoint with any
  combination of the filters
- **THEN** `log_info` rows (both event families) return newest-first with the filters
  applied

#### Scenario: Account filter matches sign and operation rows
- **WHEN** the `account` filter is applied
- **THEN** rows from BOTH event families whose account matches return (sign-page parity)

#### Scenario: Integer type filter
- **WHEN** the `type` filter carries an Integer code (e.g. 11)
- **THEN** only rows with that code return; a non-integer value is rejected as a bad
  request parameter

#### Scenario: Sign-log endpoint is gone
- **WHEN** any client calls `GET /api/v1/system/logs/sign`
- **THEN** the endpoint no longer exists (404)

#### Scenario: Session column excluded
- **WHEN** audit rows are returned by the endpoint
- **THEN** no row carries a `session` field

## REMOVED Requirements

### Requirement: Sign Event Recording (as log_sign rows)
Sign events SHALL NO LONGER record into `public.log_sign` — the table is dropped and
sign events record into `public.log_info` as first-class audit rows (MODIFIED Operation
Log Recording above).

**Reason**: The `log_sign` table is dropped per coordination audit-log-unify D1 — the
early-stage table holds no data worth keeping, and a single audit table ends the dual
write path.
**Migration**: The login/logout write path retargets through the translated insert; the
`AsyncFactory.recordForSign` call signature is unchanged, so `AuthServiceImpl` and the
logout handler are untouched. Spec coverage moves into the Operation Log Recording
requirement's sign scenarios.

#### Scenario: Sign rows land in log_info instead
- **WHEN** the platform records a sign event after this change
- **THEN** the row appears in `public.log_info` and no `log_sign` relation exists

### Requirement: Sign Log Query Endpoint
The service SHALL NO LONGER expose `GET /api/v1/system/logs/sign` — the unified
`GET /api/v1/system/logs` endpoint (MODIFIED Log Query Endpoint above) is the single
audit query, and the `system:signlog:list` permission retires.

**Reason**: Superseded by the unified audit-log endpoint (coordination
audit-log-unify D4/D8) — the dual-permission split lost its rationale and the dual
endpoint/dual menu split its IA value.
**Migration**: Consumers of `GET /system/logs/sign` use `GET /system/logs` with the same
filters (`account` replaces the sign-specific filter set; `type` narrows to sign events
by code 10/11/12). The `system:signlog:list` permission retires; no role holds it
exclusively (single-admin stage, role_menu grants verified in the migration).

#### Scenario: The sign-log endpoint answers 404
- **WHEN** any client calls `GET /api/v1/system/logs/sign` after this change
- **THEN** the endpoint no longer exists (404) and `system:signlog:list` grants nothing
