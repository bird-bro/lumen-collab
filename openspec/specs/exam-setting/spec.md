# exam-setting Specification

## Purpose
<!-- synced from: merge-setting-into-system 2026-11-13 (hosting wording per the 2026-11-13 merge ruling) -->
The 检查设置 domain hosted inside the `lumen-system` service (the 2026-11-13 merge
ruling dissolved the standalone `lumen-setting` service): the hospital-wide exam
catalog (部位分组 / 检查部位 / 检查方法 / 检查项目, carrying billing and
performance-score weight fields) and the report-template knowledge base (公共/个人
scopes), stored in the hosting service's `system` schema and served under
`/api/v1/setting/**`.

## Requirements

### Requirement: Setting Schema Ownership

<!-- synced from: merge-setting-into-system 2026-11-13 -->

The exam-setting domain SHALL be HOSTED in the `lumen-system` service (2026-11-13 merge
ruling; the standalone `lumen-setting` service is dissolved): its six tables live in the
hosting service's `system` schema, migrated by the hosting service's ONE Flyway ledger
(V29 joins the chain), with no dedicated `setting` schema, no `lumen_setting_role`, and
no second `spring.flyway.schemas` value. The domain keeps its own URL prefix
(`/api/v1/setting/**`), perms family (`setting:*`), and package root
(`org.birdbro.lumen.setting`) inside the hosting module.

#### Scenario: Service migrates only its own schema

- **WHEN** the `lumen-system` service boots against a fresh database
- **THEN** Flyway applies the single ledger in `system.flyway_schema_history` through
  V29, and the six exam tables are created in the `system` schema
- **AND** no migration touches a separate schema or the unqualified public default

#### Scenario: Role isolation denies cross-schema access

- **WHEN** a deployment is initialized from `docs/deployment/pg-init.sql`
- **THEN** no `setting` schema and no `lumen_setting_role` exist
- **AND** the exam tables follow the `lumen_system_role` grant set like every other
  `system` table

### Requirement: Exam Body Group Columns

The `system` schema SHALL contain `exam_body_group` as the flat body-part group
registry with `id` integer PK, `modality` NOT NULL (dict 1021 value), `name` NOT NULL,
`sort`, `enable` (dict 1000 codes), and the EntityBase audit columns. A group name
SHALL be unique within its modality among non-deleted rows. Zero physical FKs.

#### Scenario: Fresh deployment creates the body-group columns

- **WHEN** Flyway runs on an empty `system` schema
- **THEN** `exam_body_group` exists with the ruled columns and the per-modality
  unique rule holds for non-deleted rows

### Requirement: Exam Body Columns

The `system` schema SHALL contain `exam_body` as the body-part registry with `id`
integer PK, `modality` NOT NULL, `group_id` NOT NULL (convention reference to
`exam_body_group`), `up_id` NULL (in-group parent body, convention reference),
`name` NOT NULL, `code`, `sort`, `enable`, and the EntityBase audit columns. Zero
physical FKs.

#### Scenario: Body belongs to a group and may nest

- **WHEN** a body row is created under a group with no parent
- **THEN** `group_id` is set and `up_id` is NULL
- **WHEN** a body row references a parent body of the same group
- **THEN** `up_id` carries that parent and the hierarchy resolves within the group

### Requirement: Exam Method Columns

The `system` schema SHALL contain `exam_method` with `id` integer PK, `modality` NOT
NULL, `name` NOT NULL, `code`, `sort`, `enable`, and the EntityBase audit columns.
Zero physical FKs.

#### Scenario: Fresh deployment creates the method columns

- **WHEN** Flyway runs on an empty `system` schema
- **THEN** `exam_method` exists with the ruled columns

### Requirement: Exam Item Columns

The `system` schema SHALL contain `exam_item` with `id` integer PK, `modality` NOT
NULL, `exam` (项目名称) NOT NULL, `code`, `external_code` varchar(32) NULL (对照编码 —
the generic external-system mapping anchor; HIS is the first consumer), `body_id`
NOT NULL (convention reference to `exam_body`), `price` numeric, `rate` numeric
(折扣), `tag` (诊断计费数量), `score_rep` NULL (诊断得分 weight), `score_aud` NULL
(审核得分 weight), `is_common` boolean, `sort`, `enable`, `remark`, and the EntityBase
audit columns. An item code SHALL be unique within its modality among non-deleted
rows. The external mapping code SHALL carry NO uniqueness constraint — a one-to-many
mapping (one HIS charge item ↔ several catalog items, 打包计费) is legal. Zero
physical FKs. The item carries NO denormalized group reference and NO method/body
name copies.

#### Scenario: Item stores the catalog facts and the score weights

- **WHEN** an item is created with price, rate, tag, score_rep, and score_aud
- **THEN** the row stores them verbatim for downstream performance weighting
- **AND** the item's group is derivable only through its body's group

#### Scenario: Item code uniqueness

- **WHEN** a create or update would produce two non-deleted items with the same
  modality and code
- **THEN** the service rejects the write

#### Scenario: Item stores the external mapping code

- **WHEN** an item is created with an `externalCode`
- **THEN** the row stores it verbatim and the detail echo returns it
- **WHEN** an item is created without an `externalCode`
- **THEN** the column stays NULL and no default is applied

#### Scenario: Duplicate external mapping codes are accepted

- **WHEN** two live items in the same modality carry the same `externalCode`
- **THEN** both rows persist — the write is NOT rejected (打包计费 mapping)

### Requirement: Exam Item Method Binding

The `system` schema SHALL contain `exam_item_method` with `id` integer PK,
`exam_id` NOT NULL, `method_id` NOT NULL, unique on (exam_id, method_id). An item
update SHALL replace the binding set within the same transaction as the item row.

#### Scenario: Item update rewrites method bindings atomically

- **WHEN** an item update changes its method selection from A,B to B,C
- **THEN** the join rows after the update are exactly B,C
- **AND** a failure inside the transaction leaves the item and its bindings at the
  pre-update state

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

<!-- synced from: rep-template-structured-content 2026-11-19 -->

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

### Requirement: Modality Vocabulary

Every catalog and template `modality` value SHALL be a value of the shared 检查类型
dict catalog - resolved by its UNIQUE NAME (`uk_dict_catal_name`), never a hardcoded
catalog id (the id has flip-flopped across changes: V26 seeded 1021, a V27 draft
moved it to 1027, the working tree settled back on 1021). The value = the modality
string, value-set synced with 1024 设备类型 (DR/CT/MR/DSA/US/ENDO); the service SHALL
NOT define a parallel modality vocabulary. Write requests referencing an unknown
modality value SHALL be rejected.

#### Scenario: Unknown modality rejected

- **WHEN** a create request carries a modality value absent from dict 1021
- **THEN** the service rejects the write with a validation error

### Requirement: Referenced-Row Delete Guards

Deletion is logical (soft delete). The service SHALL refuse to delete: a body group
that still has non-deleted bodies; a body that still has non-deleted child bodies or
is referenced by any non-deleted item; a method referenced by any non-deleted item.

#### Scenario: Group with bodies refused

- **WHEN** a delete is requested for a body group that still has non-deleted bodies
- **THEN** the service rejects the delete and reports the blocking reference

#### Scenario: Method in use refused

- **WHEN** a delete is requested for a method bound to any non-deleted item
- **THEN** the service rejects the delete and reports the blocking reference

### Requirement: Exam Item Endpoints

The service SHALL expose `/api/v1/setting/exam-items` with `GET /page` (filters:
modality, groupId, keyword over name/code/externalCode; paged), `GET /{id}`, `POST`
create, `PUT /{id}` full update, `DELETE /{id}` logical delete — each gated by perms
`setting:item:{list,create,update,delete}`. Create and update requests carry
`externalCode` (optional); the detail echo returns it.

#### Scenario: Page filter by group

- **WHEN** `GET /page?modality=CT&groupId=5` is requested
- **THEN** the response lists exactly the non-deleted CT items whose body belongs to
  group 5, with body and method display names resolved server-side

#### Scenario: Keyword matches the external mapping code

- **WHEN** `GET /page?modality=CT&keyword=<an externalCode value>` is requested
- **THEN** the response lists the items whose `external_code` contains the keyword

#### Scenario: Perm-gated write

- **WHEN** a create request arrives without `setting:item:create`
- **THEN** the service refuses it per the platform auth contract

### Requirement: Body Group and Body Endpoints

The service SHALL expose `/api/v1/setting/exam-body-groups` with `GET /list`
(modality required), `POST`, `PUT /{id}`, `DELETE /{id}`; and
`/api/v1/setting/exam-bodies` with `GET /list` (modality required, optional groupId —
returns bodies nested by up_id), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` —
gated by perms `setting:body:{list,create,update,delete}` covering both resources.

#### Scenario: Group list feeds the item page cascade

- **WHEN** `GET /api/v1/setting/exam-body-groups/list?modality=DR` is requested
- **THEN** the response lists that modality's non-deleted groups in sort order

#### Scenario: Body tree scoped to a group

- **WHEN** `GET /api/v1/setting/exam-bodies/list?modality=DR&groupId=3` is requested
- **THEN** the response returns that group's bodies as an up_id-nested tree

### Requirement: Exam Method Endpoints

The service SHALL expose `/api/v1/setting/exam-methods` with `GET /page` (modality +
keyword filters), `GET /list` (modality required — picker feed), `POST`, `PUT /{id}`,
`DELETE /{id}` — gated by perms `setting:method:{list,create,update,delete}`.

#### Scenario: Method list feeds the item form

- **WHEN** `GET /api/v1/setting/exam-methods/list?modality=US` is requested
- **THEN** the response lists that modality's non-deleted methods in sort order

### Requirement: Report Template Endpoints

The service SHALL expose `/api/v1/setting/rep-temps` with `GET /tree` (modality +
type required — full folder/template tree for the scope), `GET /page` (templates
under one parent node, name filter, paged), `POST`, `PUT /{id}`, `DELETE /{id}` —
gated by perms `setting:temp:{list,create,update,delete}`. The create/update
request bodies and the template row payloads (page response; content is not part of
the tree node shape) SHALL carry the optional `content` object; tree and page
behavior is otherwise unchanged.

<!-- synced from: rep-template-structured-content 2026-11-19 -->

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

### Requirement: Exam Item Detail Binding Ids

The item detail echo (`GET /api/v1/setting/exam-items/{id}`) SHALL carry the current
binding ids (`methodIds`) alongside the display names - the edit form needs the exact
bound ids for the transactional rewrite, and name-matching against the method list is
not a contract.

#### Scenario: Detail carries the binding ids

- **WHEN** an item bound to methods 3 and 4 is fetched by id
- **THEN** the detail payload's `methodIds` is exactly [3, 4]
- **AND** the display names still resolve server-side alongside them

### Requirement: Report Template Scope Enforcement

The service SHALL enforce write ownership on private templates: create with type=2
binds owner_uid to the requesting user; update/delete of a type=2 row SHALL be
refused unless the requester is the owner. Public (type=1) writes require the menu's
temp perms. A user SHALL never see another user's private rows in any listing.

#### Scenario: Non-owner private write refused

- **WHEN** user B requests update or delete of user A's private template row
- **THEN** the service refuses the write and does not leak the row's content

#### Scenario: Private create binds the owner

- **WHEN** a user creates a template with type=2
- **THEN** the stored row's owner_uid is the requesting user, regardless of the
  request body
