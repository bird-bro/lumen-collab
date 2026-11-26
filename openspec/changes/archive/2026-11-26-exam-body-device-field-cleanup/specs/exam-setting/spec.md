# Spec Delta

## MODIFIED Requirements

### Requirement: Exam Body Columns

The `system` schema SHALL contain `exam_body` as the body-part registry with `id`
integer PK, `modality` NOT NULL, `group_id` NOT NULL (convention reference to
`exam_body_group`), `up_id` NULL (in-group parent body, convention reference),
`name` NOT NULL, `code`, `device_body_part` varchar(32) NULL (the DICOM Body Part
Examined defined term — CHEST / ABDOMEN / LSPINE …, the device-recognized term
the MWL composer sends verbatim), `sort`, `enable`, and the EntityBase audit
columns. Zero physical FKs. The body's device field set is this single column —
no Latin-name column exists.

#### Scenario: Body belongs to a group and may nest

- **WHEN** a body row is created under a group with no parent
- **THEN** `group_id` is set and `up_id` is NULL
- **WHEN** a body row references a parent body of the same group
- **THEN** `up_id` carries that parent and the hierarchy resolves within the group

#### Scenario: Body carries the device-facing fields

- **WHEN** an administrator maintains a body with a `deviceBodyPart`
- **THEN** the row stores it verbatim and the admin echo returns it
- **WHEN** it is not provided
- **THEN** the column stays NULL (the Chinese `name` remains the only required
  identity — the device field is optional until an administrator fills it)
