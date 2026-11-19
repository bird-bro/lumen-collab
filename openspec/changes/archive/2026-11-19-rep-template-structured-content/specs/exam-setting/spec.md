# exam-setting Delta

## MODIFIED Requirements

### Requirement: Report Template Columns

The `system` schema SHALL contain `rep_temp` with `id` integer PK, `type` NOT NULL
(1 公共 / 2 个人), `owner_uid` (NOT NULL when type=2, NULL when type=1), `modality`
NOT NULL, `flag` NOT NULL (0 目录 / 1 模板), `up_id` NULL (parent folder/template
node), `name` NOT NULL, `finding` (影像学表现), `impression` (影像学意见),
`content jsonb NULL` (结构化分节内容, GIN-indexed per the flexible-structures
architecture rule), and the EntityBase audit columns. Directories (flag=0) carry no
finding/impression/content; templates (flag=1) do. A template row is EITHER prose
(`content` IS NULL — `finding`/`impression` carry 影像学表现/意见) OR structured
(`content` IS NOT NULL — the two columns are not read and are stored NULL); the
pair is exclusive.

#### Scenario: Public and private templates share one tree model

- **WHEN** a template is created with type=1
- **THEN** owner_uid is NULL and the row is visible to every authorized user of the
  modality
- **WHEN** a template is created with type=2
- **THEN** owner_uid records the creating user and the row is private to that user

#### Scenario: Content and prose columns are exclusive

- **WHEN** a template row stores a non-null content value
- **THEN** its `finding` and `impression` columns are NULL
- **WHEN** a template row has `content` NULL
- **THEN** `finding`/`impression` are the template's prose surfaces

### Requirement: Report Template Endpoints

The service SHALL expose `/api/v1/setting/rep-temps` with `GET /tree` (modality +
type required — full folder/template tree for the scope), `GET /page` (templates
under one parent node, name filter, paged), `POST`, `PUT /{id}`, `DELETE /{id}` —
gated by perms `setting:temp:{list,create,update,delete}`. The create/update
request bodies and the template row payloads (page response; content is not part of
the tree node shape) SHALL carry the optional `content` object; tree and page
behavior is otherwise unchanged.

#### Scenario: Tree returns both scopes independently

- **WHEN** `GET /tree?modality=DR&type=1` and `GET /tree?modality=DR&type=2` are
  requested by the same user
- **THEN** the first returns the modality's public tree and the second only the
  user's own private tree

#### Scenario: Template page under a folder

- **WHEN** `GET /page?modality=DR&type=2&upId=7&name=结节` is requested
- **THEN** the response lists the user's own non-deleted template rows whose parent
  is node 7 whose name contains 结节, paged

#### Scenario: Template payloads carry the content object

- **WHEN** a structured template is created or updated with a valid content object
- **THEN** the stored row and subsequent page responses return the same content
- **AND** a prose template's payloads carry `content: null`

## ADDED Requirements

### Requirement: Structured Template Content

The `content` object SHALL follow the generic section model —
`{ "version": 1, "sections": [ { "name": string,
"finding": string|null (节内所见), "measures": [ { "code": string, "label": string,
"unit": string|null, "normal": string|null } ] } ], "impression": string|null
(全局结论) }`. The model is DEPARTMENT-AGNOSTIC: a section's `name` is free text
(超声填 心脏/肝脏, 内镜填 食道/胃, 放射填 右肺上叶 — no organ/part vocabulary
binding), and any section of any modality MAY carry measures. Validation on write:
version SHALL be 1; every section SHALL have a non-blank name; every measure SHALL
have non-blank code and label; a directory node (flag=0) SHALL NOT carry content;
`sections` SHALL NOT be empty when content is present. Update stays full-replace:
writing `content: null` over a structured row converts it to a prose row
(sections are dropped — destructive, and the API surfaces no content-salvage).

#### Scenario: Structured template stores the section model

- **WHEN** a template is created with content carrying one 心脏 section with
  measures AO/LAD (code+label+unit+normal) and a global impression
- **THEN** the stored row keeps the content verbatim and the finding/impression
  columns are NULL

#### Scenario: Invalid content rejected

- **WHEN** a create/update body carries content with a blank section name, a blank
  measure code or label, an empty sections array, a version other than 1, or a
  directory node targets content
- **THEN** the service rejects the write with a validation error naming the field

#### Scenario: Full-replace null content converts structured to prose

- **WHEN** an update sets `content: null` on a structured template row
- **THEN** the row becomes a prose row and its sections are irrecoverable
