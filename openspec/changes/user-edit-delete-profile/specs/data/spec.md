# Spec Delta: data

## ADDED Requirements

### Requirement: User Profile Columns

`user_info` SHALL carry the profile columns (V26 migration): `phone varchar(32)`,
`email varchar(128)`, `gender integer`, `title integer`, `dep_id integer` references
`org_dep(id)`, `modalitys varchar(128)` (CSV of 检查类型 dict codes), `rep_audit
integer` (one 审核权限 dict code), and `signature varchar(255)` (the common-upload
stored path) — all nullable, no defaults.

#### Scenario: Profile columns added without breaking existing rows

- **WHEN** the V26 migration runs against a database holding pre-existing users
- **THEN** every existing row keeps its identity and gains NULL profile columns
- **AND** the migration is guarded (idempotent column adds)

#### Scenario: Department anchor references a live department

- **WHEN** a user row carries a `dep_id`
- **THEN** the value references a live `org_dep` row (a logically deleted department's
  id may remain on historical rows; the read path renders deleted departments as an
  empty label, not an error)

#### Scenario: Gender and title store dict codes

- **WHEN** a user row stores `gender`, `title`, or `rep_audit`
- **THEN** the stored value is the dict detail's numeric `code` (e.g. gender 100401 男,
  jobTitle 100505 技师), resolvable through `GET /api/v1/system/dicts/items/{catalogName}`

#### Scenario: Exam-type visibility stores a CSV of codes

- **WHEN** a user row stores `modalitys`
- **THEN** the value is a comma-separated list of 检查类型 dict `code`s (the RCP
  lineage's CSV shape — `CT,MR,US`), each resolvable through the dict lookup

#### Scenario: Signature stores a served path

- **WHEN** a user row stores `signature`
- **THEN** the value is a relative image path under the common upload root, served
  back through the `/profile/**` static segment (the logo precedent — no byte column)

<!-- synced from: user-edit-delete-profile-api 2026-11-10 -->
