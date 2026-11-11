# system-data Specification

## Purpose

Defines the platform-shared system data: dictionary catalogs/details (single-text
`value` entries for dropdowns and rendering; the catalog anchors its details by `caId`)
and operation-log storage/query - living in the shared `public` schema of the single
`lumen` database, managed through lumen-system, with the copied framework log machinery
finally wired to real tables.

<!-- synced from: system-data 2026-11-03 -->

## Requirements

<!-- synced from: rename-auth-to-system 2026-11-04 -->
<!-- V12 dict_catal_code_drop synced DIRECTLY (user ruling 2026-11-05: no change needed) -->

### Requirement: Dict Catalog Management
The service SHALL provide CRUD on `dict_catal` (dictionary catalog: `name` unique among
live rows - V12 dropped the `code` column outright, making the display name the only
human-facing key - plus `enable`, `sort`, `remark`, `cls`) exposed under
`/api/v1/system/dicts/catal/**` and guarded by the copied security chain. The duplicate
`name` SHALL be rejected with an `A`-class code (partial unique index
`uk_dict_catal_name WHERE deleted = 0`; V9 dropped the pinyin `initial` column).

#### Scenario: Create a catalog
- **WHEN** an authenticated administrator creates a catalog with a fresh `name`
- **THEN** it persists in `public.dict_catal` with `create_by` filled from the audit
  bridge

#### Scenario: Duplicate catalog name rejected
- **WHEN** a catalog is created with an existing live `name`
- **THEN** the service rejects it with an `A`-class code

### Requirement: Dict Detail Management
The service SHALL provide CRUD on `dict_detail` (dictionary detail: `caId` anchor to its
live catalog, `code` machine key, `value` as THE single text field carrying both the
display and the stored text, `tag`/`flag` spare slots, `color`, `css`, `sort`, `enable`,
`remark`) exposed under `/api/v1/system/dicts/detail/**`. The detail-level uniqueness is
the live `(caId, value)` pair, enforced service-side with an `A`-class rejection and
backstopped by the partial unique index `uk_dict_detail_ca_id_value WHERE deleted = 0`.
The by-id list endpoint takes a `?caId=` query parameter (V12 dropped the catalog
`code` column outright, so every anchor is `caId` - the seed catalog ids are stable:
1000 common_status, 1001-1007 the six real dictionaries; V9 had already removed the
denormalized `caCode` column and the duplicated `label` column, carrying the seeded
display text into `value`).

#### Scenario: Create a detail under a catalog
- **WHEN** a detail is created for a live catalog with `caId` and a fresh `value`
- **THEN** it persists anchored by `caId` with `value` as its text
- **AND** `create_by` is filled from the audit bridge

#### Scenario: Duplicate detail value under a catalog rejected
- **WHEN** a detail is created with a `value` that already exists as a live detail under
  the same catalog
- **THEN** the service rejects it with an `A`-class code

#### Scenario: List details by catalog id
- **WHEN** a client requests the details of a catalog `caId`
- **THEN** only live details of that catalog return, ordered by `sort`

### Requirement: Dict Seed Data
The Flyway migrations SHALL seed the starter dictionary set with explicit ids and
guarded statements (idempotent under re-run): `common_status` (启用/停用, its two
details carrying the display text in `value`, catalog re-based to id 1000) plus six real
person/org dictionaries curated from the rcp reference database - `bloodType`, `marital`,
`gender`, `jobTitle`, `idType`, `orgType` (catalog ids 1001-1007, detail ids offset by
100000), PLUS the user-profile business catalogs seeded by the user-page change:
「检查类型」 (the exam-type visibility vocabulary — CT/MR/US/... modality abbreviations,
single-text `value` = the abbreviation) and 「审核权限」 (the report-workflow authority
tier vocabulary — 报告医生/初审医生/终审医生, plus the RCP 不参与 row), deterministic
ids clear of the seeded bands (V23 house style: catalog id 1021/1022, detail ids
+200000 offset), every statement ON CONFLICT-guarded. The set stays extensible through
the management endpoints without hand-inserted rows.

#### Scenario: Fresh database carries the starter dictionary

- **WHEN** the migrations run against an empty database
- **THEN** the seeded catalogs of this requirement exist with their details, all
  enabled, in the stable id bands

#### Scenario: Re-run is a no-op

- **WHEN** any seed migration statement executes a second time
- **THEN** no duplicate rows appear (the guarded-statement discipline)

#### Scenario: Exam-type vocabulary renders abbreviations

- **WHEN** a consumer resolves the 检查类型 catalog through the by-name lookup
- **THEN** the details are the modality abbreviations (CT, MR, US, ...) as the single
  text values, ordered by sort

#### Scenario: Audit-authority vocabulary carries the three tiers

- **WHEN** a consumer resolves the 审核权限 catalog through the by-name lookup
- **THEN** the details cover the report-workflow tiers (报告医生/初审医生/终审医生 and
  the RCP 不参与 row) as the single text values with numeric codes

<!-- synced from: user-edit-delete-profile-api 2026-11-11 -->

### Requirement: Operation Log Recording
<!-- synced from: audit-log-unify-api 2026-11-10 (absorbs the former Sign Event
     Recording requirement — sign events are log_info rows now, V25 dropped log_sign) -->
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
<!-- synced from: audit-log-unify-api 2026-11-10 (log_sign half dropped by V25) -->
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

<!-- synced from: audit-log-unify-api 2026-11-10 (supersedes the sign-log-query wording) -->

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

### Requirement: Shared Placement in the Public Schema
<!-- synced from: audit-log-unify-api 2026-11-10 (four → three: V25 dropped log_sign) -->
The three system-data tables (`log_info`, `dict_catal`, `dict_detail`) SHALL live in the
`public` schema of the `lumen` database and
SHALL be created by lumen-system's Flyway migration with explicit `public.` qualification;
every service connection SHALL resolve the unqualified entity names via
`search_path = <service-schema>, public` (the `ice.` prefix of the copied entities is
removed - recorded adaptation (7)).

#### Scenario: Unqualified entity names resolve
- **WHEN** a service connection with `currentSchema=<own-schema>,public` reads
  `log_info` / `dict_catal` through the copied entities
- **THEN** the rows resolve from the `public` schema without schema qualification

<!-- synced from: dict-city-reference 2026-11-04 -->

### Requirement: City Reference Table
The service SHALL provide the administrative-divisions reference table `public.dict_city`
in the 随访 lineage shape - 13 columns including `pinyin`/`lng`/`lat`/`initial`, no audit
block (static reference data) - seeded by lumen-system's V4 migration from the lineage
database snapshot: 3751 rows (level 0 ×1, level 1 ×35, level 2 ×373, level 3 ×3342). The
`Long` primary key is a recorded lineage-fidelity deviation from the Integer convention.

#### Scenario: Seeded divisions match the lineage checksum
- **WHEN** a fresh database receives the migrations
- **THEN** `public.dict_city` holds 3751 rows - level 1 ×35, level 2 ×373, level 3 ×3342
- **AND** every copied column resolves (13-column shape asserted) and the 中国 root
  (level 0, `up_id` 0) is present as the tree root

### Requirement: City Reference Query
The service SHALL expose READ-ONLY query endpoints under `/api/v1/system/dicts/city/**`:
`GET /tree` (assembled from `up_id` = 0) and `GET /list` filtered by `level`/`upId`. NO
mutation endpoints SHALL exist for the table (user decision 2026-11-03: no frontend CRUD
pages - the seed migrations are the data source; updates arrive as later seed migrations).

#### Scenario: Tree query returns the nested divisions
- **WHEN** a client requests the city tree
- **THEN** the 中国 root returns with its province children nested from `up_id`

#### Scenario: List a city's districts
- **WHEN** a client requests the list filtered by the 北京市 city node (level 2)
- **THEN** only its level-3 districts return
- **AND** no write endpoint exists for the table

<!-- synced from: role-type-dict-api 2026-11-09 -->

### Requirement: Role Type Dict Catalog

The platform dict seed SHALL carry a 「角色类型」 catalog (id 1020) whose details
define the `role_info.type` vocabulary: code **10 = 系统角色**, code **11 =
RCP角色** — mirroring the copied `RoleConstants` lineage (`TYPE_GLOBAL`,
`TYPE_RCP`). Seeded deterministically with ON CONFLICT guards (V6 house style) so
re-runs are no-ops; further categories are operator-managed rows in 字典管理
(no code change required — user ruling 2026-11-09).

#### Scenario: Seed provisions the role type catalog

- **WHEN** a fresh deployment replays the migration chain through V23
- **THEN** `dict_catal` contains 「角色类型」 (id 1020, enabled) and
  `dict_detail` contains its two items (code 10 / code 11) with deterministic ids
  1020001 / 1020002

#### Scenario: Seed re-run is a no-op

- **WHEN** the migration chain replays on a database that already carries the
  catalog
- **THEN** no duplicate rows appear and Flyway reports success

#### Scenario: Operator extends the vocabulary

- **WHEN** an administrator adds a detail (code + value) to the 「角色类型」
  catalog in 字典管理
- **THEN** the new category is immediately part of the type vocabulary served by
  the by-name lookup — no deployment or code change involved
