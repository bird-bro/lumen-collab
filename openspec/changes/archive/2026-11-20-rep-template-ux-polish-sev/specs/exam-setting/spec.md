# Spec Delta: exam-setting

## Purpose

Make the measure `code` OPTIONAL (coordination rep-template-ux-polish, user
ruling 2026-11-20): the printed report's B-form measure table never shows the
项目 code and the UI retired its input — the field rides nullable for legacy
rows, API-direct writers and future report-domain statistics anchoring.

## MODIFIED Requirements

### Requirement: Structured Template Content

The `content` object SHALL follow the generic section model —
`{ "version": 2, "sections": [ { "name": string,
"finding": string|null (节内所见), "measures": [ { "code": string|null,
"label": string,
"unit": string|null, "normal": null | { "kind": "range", "min": number|null, "max": number|null } | { "kind": "text", "text": string } } ] } ], "impression": string|null
(全局结论) }`. The model is DEPARTMENT-AGNOSTIC: a section's `name` is free text
(超声填 心脏/肝脏, 内镜填 食道/胃, 放射填 右肺上叶 — no organ/part vocabulary
binding), and any section of any modality MAY carry measures. Validation on write:
version SHALL be 2 (the server pins it; a v1-shaped write — including a plain-string
`normal` — SHALL be rejected naming the field); every section SHALL have a non-blank
name; every measure SHALL have a non-blank label — `code` SHALL be OPTIONAL
(rep-template-ux-polish 2026-11-20: the printed B-form never shows the 项目 code
and the UI retired its input; legacy rows keep theirs, new measures ride null,
API-direct writers may still supply one); a range `normal` SHALL
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
  measure label, an empty sections array, a version other than 2, a
  plain-string (v1-shaped) measure normal, a range normal with min > max, a range
  normal with both ends null, a blank or over-64-character text normal, or a
  directory node targets content
- **THEN** the service rejects the write with a validation error naming the field

#### Scenario: Blank or absent measure code writes through

- **WHEN** a create/update carries a measure with `code: ""` (or no code key at
  all) and a non-blank label
- **THEN** the write succeeds — the code is optional (legacy rows keep theirs,
  new measures ride null)

#### Scenario: Full-replace null content converts structured to prose

- **WHEN** an update sets `content: null` on a structured template row
- **THEN** the row becomes a prose row and its sections are irrecoverable

#### Scenario: Single-sided range normal is accepted

- **WHEN** a measure carries `{"kind":"range","min":null,"max":5}` (or
  `{"kind":"range","min":3,"max":null}`)
- **THEN** the write succeeds and the range is stored verbatim
