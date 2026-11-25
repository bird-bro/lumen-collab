## ADDED Requirements

### Requirement: Apply Origin Fields

Section 2 请检信息 SHALL carry three text inputs recording the HIS application
origin — 申请科室, 申请医生, and 申请时间 (the department, doctor, and moment that
opened the 检查申请单 in HIS). The three SHALL submit inside the create body's `ext`
block as optional strings (`applyDept` / `applyDoctor` / `applyTime`, blank allowed)
and SHALL NOT be coupled to lumen org/user/dict vocabularies (HIS identities are
external). The workbench SHALL NOT render them on the list rows in this change.

#### Scenario: The origin trio submits with the ext block

- **WHEN** the operator fills 申请科室 / 申请医生 / 申请时间 and submits the order
- **THEN** the create body's ext carries the three values verbatim (applyTime as the
  raw `yyyy-MM-dd HH:mm:ss` text entered)
- **AND** the ext contract reserves applyDeptId / applyDoctorId as optional anchors
  that the v1 UI does not send (they stay null until an HIS feed or a picker surfaces them)

#### Scenario: Blank origin fields submit as nulls

- **WHEN** the operator leaves the trio blank
- **THEN** the ext block carries nulls for the three fields and the order registers
  normally (the origin is optional)
