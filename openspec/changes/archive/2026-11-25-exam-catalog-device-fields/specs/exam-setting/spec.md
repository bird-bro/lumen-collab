## MODIFIED Requirements

### Requirement: Exam Body Columns

The `system` schema SHALL contain `exam_body` as the body-part registry with `id`
integer PK, `modality` NOT NULL, `group_id` NOT NULL (convention reference to
`exam_body_group`), `up_id` NULL (in-group parent body, convention reference),
`name` NOT NULL, `code`, `device_token` varchar(32) NULL (the DICOM Body Part
Examined defined term — CHEST / ABDOMEN / LSPINE …, the device-recognized token
the MWL composer sends verbatim), `device_name` varchar(64) NULL (the Latin body
name for charset-strict devices), `sort`, `enable`, and the EntityBase audit
columns. Zero physical FKs.

#### Scenario: Body belongs to a group and may nest

- **WHEN** a body row is created under a group with no parent
- **THEN** `group_id` is set and `up_id` is NULL
- **WHEN** a body row references a parent body of the same group
- **THEN** `up_id` carries that parent and the hierarchy resolves within the group

#### Scenario: Body carries the device-facing fields

- **WHEN** an administrator maintains a body with a `deviceToken` and a
  `deviceName`
- **THEN** the row stores them verbatim and the admin echo returns them
- **WHEN** neither is provided
- **THEN** both columns stay NULL (the Chinese `name` remains the only required
  identity — the device fields are optional until an administrator fills them)

### Requirement: Exam Method Columns

The `system` schema SHALL contain `exam_method` with `id` integer PK, `modality` NOT
NULL, `name` NOT NULL, `code`, `device_desc` varchar(128) NULL (the Latin method
text for charset-strict devices), `sort`, `enable`, and the EntityBase audit
columns. Zero physical FKs.

#### Scenario: Fresh deployment creates the method columns

- **WHEN** Flyway runs on an empty `system` schema
- **THEN** `exam_method` exists with the ruled columns (including
  `device_desc` NULL)

#### Scenario: Method carries the device-facing description

- **WHEN** an administrator maintains a method with a `deviceDesc`
- **THEN** the row stores it verbatim and the admin echo returns it
- **WHEN** it is not provided
- **THEN** the column stays NULL (the link table `exam_item_method` carries no
  text — the method dictionary owns the device wording)

### Requirement: Exam Item Columns

The `system` schema SHALL contain `exam_item` with `id` integer PK, `modality` NOT
NULL, `exam` (项目名称) NOT NULL, `code`, `device_desc` varchar(128) NULL (the Latin
procedure description — the Scheduled Procedure Step Description / Procedure Code
Meaning the MWL composer sends; the item's `code` serves as the Procedure Code
Value), `external_code` varchar(32) NULL (对照编码 —
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

#### Scenario: Item carries the device-facing description

- **WHEN** an administrator maintains an item with a `deviceDesc`
- **THEN** the row stores it verbatim and the admin echo returns it (the future
  MWL composer reads it by the item id — the order flow never copies it)
- **WHEN** it is not provided
- **THEN** the column stays NULL (the Chinese `exam` name remains the only
  required identity)
