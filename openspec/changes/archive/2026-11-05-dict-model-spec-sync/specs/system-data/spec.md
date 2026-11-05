# Spec Delta

## MODIFIED Requirements

### Requirement: Dict Catalog Management
The service SHALL provide CRUD on `dict_catal` (dictionary catalog: `code` unique among
live rows, `name`, `enable`, `sort`, `remark`, `cls`) exposed under
`/api/v1/system/dicts/catal/**` and guarded by the copied security chain. The duplicate
`code` SHALL be rejected with an `A`-class code. (V9 dropped the pinyin `initial` column
- catalogs are no longer field-extensible with search helpers.)

#### Scenario: Create a catalog
- **WHEN** an authenticated administrator creates a catalog with a fresh `code`
- **THEN** it persists in `public.dict_catal` with `create_by` filled from the audit
  bridge

#### Scenario: Duplicate catalog code rejected
- **WHEN** a catalog is created with an existing live `code`
- **THEN** the service rejects it with an `A`-class code

### Requirement: Dict Detail Management
The service SHALL provide CRUD on `dict_detail` (dictionary detail: `caId` anchor to its
live catalog, `code` machine key, `value` as THE single text field carrying both the
display and the stored text, `tag`/`flag` spare slots, `color`, `css`, `sort`, `enable`,
`remark`) exposed under `/api/v1/system/dicts/detail/**`. The detail-level uniqueness is
the live `(caId, value)` pair, enforced service-side with an `A`-class rejection and
backstopped by the partial unique index `uk_dict_detail_ca_id_value WHERE deleted = 0`.
The by-code list endpoint keeps its `?caCode=` query parameter and resolves the catalog
code to its `caId` internally (V9 removed the denormalized `caCode` column and the
duplicated `label` column; V9 also carried the seeded display text into `value`).

#### Scenario: Create a detail under a catalog
- **WHEN** a detail is created for a live catalog with `caId` and a fresh `value`
- **THEN** it persists anchored by `caId` with `value` as its text
- **AND** `create_by` is filled from the audit bridge

#### Scenario: Duplicate detail value under a catalog rejected
- **WHEN** a detail is created with a `value` that already exists as a live detail under
  the same catalog
- **THEN** the service rejects it with an `A`-class code

#### Scenario: List details by catalog code
- **WHEN** a client requests the details of a catalog `code`
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
  endpoint by catalog `code` (e.g. `gender` → 男/女/未知)
- **AND** `common_status`'s details expose 启用/停用 as their `value` texts with machine
  keys 1/0 in `code`
