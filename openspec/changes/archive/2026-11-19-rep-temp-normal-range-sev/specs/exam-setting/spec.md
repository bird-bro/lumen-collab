## MODIFIED Requirements

### Requirement: Structured Template Content

The `content` object SHALL follow the generic section model —
`{ "version": 2, "sections": [ { "name": string,
"finding": string|null (节内所见), "measures": [ { "code": string, "label": string,
"unit": string|null, "normal": null | { "kind": "range", "min": number|null, "max": number|null } | { "kind": "text", "text": string } } ] } ], "impression": string|null
(全局结论) }`. The model is DEPARTMENT-AGNOSTIC: a section's `name` is free text
(超声填 心脏/肝脏, 内镜填 食道/胃, 放射填 右肺上叶 — no organ/part vocabulary
binding), and any section of any modality MAY carry measures. Validation on write:
version SHALL be 2 (the server pins it; a v1-shaped write — including a plain-string
`normal` — SHALL be rejected naming the field); every section SHALL have a non-blank
name; every measure SHALL have non-blank code and label; a range `normal` SHALL
carry min/max as independently nullable decimals (single-sided open intervals are
expressed by leaving one end null), SHALL reject min > max, and SHALL reject both
ends null; a text `normal` SHALL be non-blank and at most 64 characters; a directory
node (flag=0) SHALL NOT carry content; `sections` SHALL NOT be empty when content is
present. Update stays full-replace: writing `content: null` over a structured row
converts it to a prose row (sections are dropped — destructive, and the API surfaces
no content-salvage). The report-filling side's abnormal-value alerting consumes this
shape in a FUTURE change and is out of scope here.

#### Scenario: Structured template stores the section model

- **WHEN** a template is created with content carrying one 心脏 section with
  measures AO/LAD (code+label+unit+normal as a range union / a text union) and a
  global impression
- **THEN** the stored row keeps the content verbatim (version 2) and the
  finding/impression columns are NULL

#### Scenario: Invalid content rejected

- **WHEN** a create/update body carries content with a blank section name, a blank
  measure code or label, an empty sections array, a version other than 2, a
  plain-string (v1-shaped) measure normal, a range normal with min > max, a range
  normal with both ends null, a blank or over-64-character text normal, or a
  directory node targets content
- **THEN** the service rejects the write with a validation error naming the field

#### Scenario: Full-replace null content converts structured to prose

- **WHEN** an update sets `content: null` on a structured template row
- **THEN** the row becomes a prose row and its sections are irrecoverable

#### Scenario: Single-sided range normal is accepted

- **WHEN** a measure carries `{"kind":"range","min":null,"max":5}` (or
  `{"kind":"range","min":3,"max":null}`)
- **THEN** the write succeeds and the range is stored verbatim

## ADDED Requirements

### Requirement: Normal-Range v2 One-Shot Migration

A one-shot data migration SHALL convert every stored v1 content row to the v2
shape. The migration SHALL only touch rows whose `content.version` is 1 (replay
skips v2 rows — idempotent), SHALL use a deterministic strict pattern set on the
trimmed v1 `normal` text — `19-40` (separator `-` or `~`) becomes a range with both
ends; `<5` or `≤5` becomes a range with only `max`; `>3` or `≥3` becomes a range
with only `min`; `<5` and `≤5` share the same stored semantics (`max=5`, a recorded
flattening); any other non-empty text (qualitative phrases such as 阴性) becomes a
text variant carrying the ORIGINAL text verbatim; a null normal stays null — and
SHALL leave the jsonb column and its GIN index otherwise untouched. Fresh installs
(never-populated column) are a natural no-op.

#### Scenario: Parseable v1 normals convert to ranges

- **WHEN** the migration runs over a v1 row whose measures carry `19-40`, `<5`, and
  `>3`
- **THEN** the row is v2 with `{kind:'range',min:19,max:40}`, `{kind:'range',min:null,max:5}`,
  and `{kind:'range',min:3,max:null}` respectively

#### Scenario: Unparseable v1 normals degrade to text verbatim

- **WHEN** the migration runs over a v1 row whose measure normal is 阴性
- **THEN** the row is v2 with `{kind:'text',text:'阴性'}` — the original text is
  preserved byte-for-byte

#### Scenario: Migration replay is idempotent

- **WHEN** the migration runs again over rows already at version 2
- **THEN** those rows are untouched and the migration completes without error

#### Scenario: v1-shaped writes are rejected after migration

- **WHEN** a create/update body carries v1-shaped content (version 1 or a
  plain-string measure normal)
- **THEN** the write is rejected with a validation error naming the field
