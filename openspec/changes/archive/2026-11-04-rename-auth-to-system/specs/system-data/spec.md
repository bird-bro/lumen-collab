# Spec Delta

## MODIFIED Requirements

### Requirement: Dict Catalog Management
The service SHALL provide CRUD on `dict_catal` (dictionary catalog: `code` unique among
live rows, `name`, `enable`, `sort`, `remark`, `cls`, `initial`) exposed under
`/api/v1/system/dicts/catal/**` and guarded by the copied security chain. The duplicate
`code` SHALL be rejected with an `A`-class code. The dict data layer (entity/mapper/
service/pojo/helpers) lives in the `lumen-system` service module (the OPS `manage` pattern)
- not in the shared `framework` library.

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
`back1`/`back2` extension slots) exposed under `/api/v1/system/dicts/detail/**`.

#### Scenario: Create a detail under a catalog
- **WHEN** a detail is created for a live catalog
- **THEN** it persists with both `caId` and the catalog's `code` denormalized

#### Scenario: List details by catalog code
- **WHEN** a client requests the details of a catalog `code`
- **THEN** only live details of that catalog return, ordered by `sort`

### Requirement: Log Query Endpoint
The service SHALL expose a paged log query under `/api/v1/system/logs` (filters: `type`,
`uid`, `dateYm`; ordered by `actionTime` descending) for administration screens. The
operation-log WRITE path (`LogAspect` + `AsyncFactory` + entities/mappers/service) stays in
the shared `framework` library - every `@Log`-using service records through it; the
`lumen-system` service hosts the admin query facade.

#### Scenario: Query operation logs
- **WHEN** an authenticated administrator queries the log endpoint
- **THEN** `log_info` rows return newest-first with the requested filters applied

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
