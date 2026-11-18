# Spec Delta: exam-setting

## Purpose

Give `exam_item` a generic external mapping code (`external_code`) — the catalog-level
anchor for external-system comparison (HIS first, via the future `lumen-integration`
HL7v2 route). Nullable, NO uniqueness constraint. Build on the post-`exam-item-common-filter-drop`
endpoint text (isCommon already retired from the page filters).

## MODIFIED Requirements

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
