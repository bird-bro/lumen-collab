# Spec Delta

## MODIFIED Requirements

### Requirement: Device Create Validation

Device create SHALL require `code`, `name`, `type` (the dict-1024 VALUE token, e.g.
"CT"), `status`, `tag`, `oid`, and `did` (the 使用科室 anchor - second-round ruling
2026-11-12; the DB column stays nullable, the service enforces the anchor); the
profile/access/acquisition fields are optional. The acquisition block is
`captureW/captureH/prune/pruneU/pruneD/pruneL/pruneR` — the Box parameters
(`boxAdapter/boxIp/boxPort/boxMac/boxPrint`) are removed from the contract
(ruling 2026-11-19) and MUST NOT be sent or echoed.
A `did` that misses a live department of the same org, or a duplicate live `code`
within the same org, SHALL reject envelope code `"A0400"` with no row created. A
successful create SHALL return the new device id.

#### Scenario: Create device

- **WHEN** an authenticated client posts a body with the required fields and optional
  brand/model/spec/serialNum/manufacturer/dates/responsible/responsiblePhone/
  startDate/endDate/remark, access block `ae/ip/port`, and acquisition block
  `captureW/captureH/prune/pruneU/pruneD/pruneL/pruneR`
- **THEN** the device is created with `code: "200"` and the new device id in `data`
- **AND** a missing `oid`, `did`, or required field is rejected before any row is
  written

#### Scenario: Device create rejects foreign or duplicate identifiers

- **WHEN** the create body carries a `did` outside the org, a deleted `did`, or a
  `code` that already exists live in the same org
- **THEN** the envelope rejects with `code: "A0400"` and `data` null
- **AND** no device row is created
