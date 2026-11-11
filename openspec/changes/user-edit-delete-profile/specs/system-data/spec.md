# Spec Delta: system-data

## MODIFIED Requirements

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

<!-- synced from: user-edit-delete-profile-api 2026-11-10 -->
