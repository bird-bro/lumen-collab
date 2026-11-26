# Spec Delta

## MODIFIED Requirements

### Requirement: Device-Facing Field Maintenance

The three catalog pages — 检查项目, 检查部位, 检查方法 — SHALL additionally expose
the device-facing fields in their edit/create forms and echo them in their tables:
检查项目 gains 设备描述 (`deviceDesc`), 检查部位 gains 部位token (`deviceBodyPart` —
the DICOM Body Part Examined defined term), 检查方法 gains 设备描述 (`deviceDesc`).
All are optional Latin inputs (the Chinese originals stay the required identities);
the create/update payloads carry them verbatim and the page tables render them (a
device column per page, alongside the Chinese name). 检查部位 has no 拉丁名 input —
its device field set is the single Body Part term.

#### Scenario: The catalog forms maintain the device fields

- **WHEN** an administrator edits a 检查项目 / 检查部位 / 检查方法 and fills the
  device fields
- **THEN** the update payload carries them and the refreshed table shows them in
  the device column
- **WHEN** the device fields are left blank
- **THEN** the record persists with NULL device fields and the table shows an
  em-dash

#### Scenario: The demo catalog ships the device values

- **WHEN** the demo catalog is seeded (the 34 bodies / 46 items / 58 method links)
- **THEN** every seeded row carries its drafted device values — body rows only the
  Body Part term (the Latin body names went away with `device_name`; item/method
  rows keep their 设备描述) — so the workbench and the future MWL composer see a
  complete device-facing catalog on a fresh install
