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
100000). The set stays extensible through the management endpoints without hand-inserted
rows.

#### Scenario: Fresh database carries the starter dictionary
- **WHEN** a fresh database receives the migrations
- **THEN** `public.dict_catal` holds 7 live catalogs - `common_status` at id 1000 and
  the six real dictionaries in the 1001-1007 range
- **AND** `public.dict_detail` holds 30 live details resolving through the detail query
  endpoint by catalog id (seed ids fixed: `gender` = 1004 → 男/女/未知)
- **AND** `common_status`'s details expose 启用/停用 as their `value` texts with machine
  keys 1/0 in `code`

### Requirement: Operation Log Recording
The `public.log_info` table (append-only operation log of the copied `LogInfoEntity`
lineage: `dateYm`, title, descr, type, uid, account, actionTime, spent, method, url,
session, mobile, ip - no audit block) SHALL receive rows through the copied machinery:
`@Log`-annotated write endpoints record via `LogAspect` + `AsyncFactory.recordAction`,
with operator identity from the audit bridge.

#### Scenario: Annotated endpoint writes an operation log
- **WHEN** an authenticated administrator calls a `@Log`-annotated write endpoint
- **THEN** a `public.log_info` row appears with that user's `uid`/`account` and the
  request's method/url

### Requirement: Sign Event Recording
Sign events SHALL be recorded in `public.log_sign` symmetrically: login success and
failure SHALL call `AsyncFactory.recordForSign` from the login flow, and logout recording
is already wired by the copied `LogoutSuccessHandlerImpl` - the V3 tables make both live.

#### Scenario: Login records a sign event
- **WHEN** a login attempt succeeds or fails against the login endpoint
- **THEN** a `public.log_sign` row appears with the account, event type, and action time

### Requirement: System Log Tables
The V3 migration SHALL create `public.log_info` and `public.log_sign` as append-only
tables (no audit block; the `dateYm` key supports monthly archival) so the copied log
machinery resolves real tables.

#### Scenario: Log table exists on fresh database
- **WHEN** a fresh database receives the migrations
- **THEN** `public.log_info` and `public.log_sign` exist in the `public` schema

<!-- synced from: rename-auth-to-system 2026-11-04 -->

### Requirement: Log Query Endpoint
The service SHALL expose a paged log query under `/api/v1/system/logs` (filters: `type`,
`uid`, `dateYm`; ordered by `actionTime` descending) for administration screens. The
operation-log WRITE path (`LogAspect` + `AsyncFactory` + entities/mappers/service) stays in
the shared `framework` library - every `@Log`-using service records through it; the
`lumen-system` service hosts the admin query facade.

#### Scenario: Query operation logs
- **WHEN** an authenticated administrator queries the log endpoint
- **THEN** `log_info` rows return newest-first with the requested filters applied

<!-- synced from: rename-auth-to-system 2026-11-04 -->

### Requirement: Shared Placement in the Public Schema
The four system-data tables SHALL live in the `public` schema of the `lumen` database and
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
