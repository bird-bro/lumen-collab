# system-data Specification

## Purpose

Defines the platform-shared system data: dictionary catalogs/details (label↔value data
for dropdowns and rendering) and operation-log storage/query - living in the shared
`public` schema of the single `lumen` database, managed through lumen-auth, with the
copied framework log machinery finally wired to real tables.

<!-- synced from: system-data 2026-11-03 -->
## Requirements

### Requirement: Dict Catalog Management
The service SHALL provide CRUD on `dict_catal` (dictionary catalog: `code` unique among
live rows, `name`, `enable`, `sort`, `remark`, `cls`, `initial`) exposed under
`/api/v1/auth/dicts/catal/**` and guarded by the copied security chain. The duplicate
`code` SHALL be rejected with an `A`-class code.

#### Scenario: Create a catalog
- **WHEN** an authenticated administrator creates a catalog with a fresh `code`
- **THEN** it persists in `public.dict_catal` with `create_by` filled from the audit
  bridge

#### Scenario: Duplicate catalog code rejected
- **WHEN** a catalog is created with an existing live `code`
- **THEN** the service rejects it with an `A`-class code

### Requirement: Dict Detail Management
The service SHALL provide CRUD on `dict_detail` (dictionary detail: `caId` + `caCode`
double anchor to its catalog, `code`, `value`, `label`, `color`, `css`, `sort`, `enable`,
`back1`/`back2` extension slots) exposed under `/api/v1/auth/dicts/detail/**`.

#### Scenario: Create a detail under a catalog
- **WHEN** a detail is created for a live catalog
- **THEN** it persists with both `caId` and the catalog's `code` denormalized

#### Scenario: List details by catalog code
- **WHEN** a client requests the details of a catalog `code`
- **THEN** only live details of that catalog return, ordered by `sort`

### Requirement: Dict Seed Data
The V3 migration SHALL seed a minimal starter catalog set (explicit ids + `setval`
sequence advance, the V2 discipline) - `common_status` (启用/停用) as the first entry -
extensible through the management endpoints without hand-inserted rows.

#### Scenario: Fresh database carries the starter dictionary
- **WHEN** a fresh database receives the migrations
- **THEN** `public.dict_catal` holds `common_status` and its details are listed through
  the detail query endpoint

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

### Requirement: Log Query Endpoint
The service SHALL expose a paged log query under `/api/v1/auth/logs` (filters: `type`,
`uid`, `dateYm`; ordered by `actionTime` descending) for administration screens.

#### Scenario: Query operation logs
- **WHEN** an authenticated administrator queries the log endpoint
- **THEN** `log_info` rows return newest-first with the requested filters applied

### Requirement: Shared Placement in the Public Schema
The four system-data tables SHALL live in the `public` schema of the `lumen` database and
SHALL be created by lumen-auth's Flyway migration with explicit `public.` qualification;
every service connection SHALL resolve the unqualified entity names via
`search_path = <service-schema>, public` (the `ice.` prefix of the copied entities is
removed - recorded adaptation (7)).

#### Scenario: Unqualified entity names resolve
- **WHEN** a service connection with `currentSchema=<own-schema>,public` reads
  `log_info` / `dict_catal` through the copied entities
- **THEN** the rows resolve from the `public` schema without schema qualification
