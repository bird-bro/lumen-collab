## MODIFIED Requirements

### Requirement: US Registration Extension

The module SHALL carry the US-specific registration fields in a us-side 1:1 extension table
`us_registration_ext` (pregnancy status, gestational weeks, note, apply department, apply
doctor, apply time, and the reserved id anchors apply_dept_id / apply_doctor_id) — the
vendored kernel DDL stays verbatim — read and written through the kernel `RegistrationHook`
seam so the create/query path carries the extension without kernel changes. The apply fields
record the HIS application origin (the department, doctor, and moment that opened the
检查申请单): `apply_dept varchar(64)` / `apply_doctor varchar(64)` / `apply_time varchar(19)`,
all nullable; `apply_time` is a raw-text stamp (informational only — no parse policy, no
state semantics in v1); the id anchors are varchar(64) reservations that stay null until an
HIS feed or a picker surfaces them.

#### Scenario: Extension persists with the order

- **WHEN** an order is created with US extension fields
- **THEN** the hook persists them 1:1 against the order id and the query path returns them

#### Scenario: Apply origin persists and flattens onto the view

- **WHEN** an order is created whose ext block carries applyDept / applyDoctor / applyTime
  (and optionally the reserved applyDeptId / applyDoctorId anchors)
- **THEN** the ext row stores them verbatim and the page query's flattened view returns them
- **AND** an ext block without them stores NULLs and the view returns null
