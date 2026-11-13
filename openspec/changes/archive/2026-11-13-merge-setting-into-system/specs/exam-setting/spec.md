# Spec Delta: exam-setting

## MODIFIED Requirements

### Requirement: Setting Schema Ownership

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
NULL, `exam` (项目名称) NOT NULL, `code`, `body_id` NOT NULL (convention reference to
`exam_body`), `price` numeric, `rate` numeric (折扣), `tag` (诊断计费数量),
`score_rep` NULL (诊断得分 weight), `score_aud` NULL (审核得分 weight), `is_common`
boolean, `sort`, `enable`, `remark`, and the EntityBase audit columns. An item code
SHALL be unique within its modality among non-deleted rows. Zero physical FKs. The
item carries NO denormalized group reference and NO method/body name copies.

#### Scenario: Item stores the catalog facts and the score weights

- **WHEN** an item is created with price, rate, tag, score_rep, and score_aud
- **THEN** the row stores them verbatim for downstream performance weighting
- **AND** the item's group is derivable only through its body's group

#### Scenario: Item code uniqueness

- **WHEN** a create or update would produce two non-deleted items with the same
  modality and code
- **THEN** the service rejects the write

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
node), `name` NOT NULL, `finding` (影像学表现), `impression` (影像学意见), and the
EntityBase audit columns. Directories (flag=0) carry no finding/impression content;
templates (flag=1) do.

#### Scenario: Public and private templates share one tree model

- **WHEN** a template is created with type=1
- **THEN** owner_uid is NULL and the row is visible to every authorized user of the
  modality
- **WHEN** a template is created with type=2
- **THEN** owner_uid records the creating user and the row is private to that user
