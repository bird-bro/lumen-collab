# Spec Delta: api

## ADDED Requirements

### Requirement: Device Registry Endpoints

`lumen-system` SHALL expose the device registry under `/api/v1/system/org-devices`:
a paged list (`GET /page` - `orgId` OPTIONAL: absent lists all campuses' devices,
the department page's root-means-all semantics; a present orgId filters to that
campus), a picker list (`GET /list?orgId=&depId=` - department-scoped when depId is
present), detail (`GET /{id}`), create (`POST`), full update (`PUT /{id}`), and
logical delete (`DELETE /{id}`). All responses use the standard envelope. The class
gate is `system:device:list`; mutating methods additionally pin
`system:device:create` / `system:device:update` / `system:device:delete`.

#### Scenario: Paged device list filtered by org

- **WHEN** an authenticated client requests `GET /api/v1/system/org-devices/page` with
  `orgId`, `pageNum`, `pageSize`
- **THEN** it returns `code: "200"` with the paged device rows (`status`/`tag` as raw
  dict codes; `type` as the dict-1024 VALUE token) and total, ordered by id descending
- **AND** rows of other orgs never appear under that filter

#### Scenario: The hospital root means all campuses

- **WHEN** the page request omits `orgId`
- **THEN** live devices of every org are returned (the tree's root selection shows
  the whole estate)

#### Scenario: Keyword filters name or code

- **WHEN** the page request carries `keyword`
- **THEN** only live devices whose `name` or `code` contains the keyword are returned
- **AND** `type`, when present, filters by the exact dict VALUE token, and `status`
  by the exact dict code

#### Scenario: Device list feeds the room picker

- **WHEN** the room form requests `GET /api/v1/system/org-devices/list?orgId=&depId=`
- **THEN** only that org's live devices are returned as id/name/code/type rows,
  ordered by name, narrowed to the department when `depId` is present (the picker
  feeds from the room's own 负责科室)

### Requirement: Device Create Validation

Device create SHALL require `code`, `name`, `type` (the dict-1024 VALUE token, e.g.
"CT"), `status`, `tag`, `oid`, and `did` (the 使用科室 anchor - second-round ruling
2026-11-12; the DB column stays nullable, the service enforces the anchor); the
profile/access/acquisition fields are optional.
A `did` that misses a live department of the same org, or a duplicate live `code`
within the same org, SHALL reject envelope code `"A0400"` with no row created. A
successful create SHALL return the new device id.

#### Scenario: Create device

- **WHEN** an authenticated client posts a body with the required fields and optional
  brand/model/spec/serialNum/manufacturer/dates/responsible/responsiblePhone/
  startDate/endDate/remark, access block `ae/ip/port`, and acquisition block
  `boxAdapter/boxIp/boxPort/boxMac/boxPrint/captureW/captureH/prune/pruneU/pruneD/
  pruneL/pruneR`
- **THEN** the device is created with `code: "200"` and the new device id in `data`
- **AND** a missing `oid`, `did`, or required field is rejected before any row is
  written

#### Scenario: Device create rejects foreign or duplicate identifiers

- **WHEN** the create body carries a `did` outside the org, a deleted `did`, or a
  `code` that already exists live in the same org
- **THEN** the envelope rejects with `code: "A0400"` and `data` null
- **AND** no device row is created

### Requirement: Device Update and Delete

Device update SHALL full-replace the editable surface for an existing device id
(the required `did` anchor stays required - a device always belongs to a department);
device delete SHALL soft-delete it. A missing device id SHALL reject `A0400`. A
device still bound by at least one live room SHALL NOT be deleted; the rejection
message SHALL name the live room count.

#### Scenario: Update device full-replaces the editable surface

- **WHEN** the update body carries the full editable field set for an existing device id
- **THEN** the row is updated and `code: "200"` returns `true`
- **AND** a missing device id rejects `"A0400"`

#### Scenario: Delete device is logical and reference-guarded

- **WHEN** an authenticated client sends `DELETE /api/v1/system/org-devices/{id}`
- **THEN** the row is logically deleted and `code: "200"` returns `true`
- **AND** a device still bound by at least one live room rejects `"A0400"` with a
  descriptive `msg` naming the room count (the room must rebind first)

### Requirement: Exam Room Registry Endpoints

`lumen-system` SHALL expose the exam-room registry under `/api/v1/system/org-rooms`:
a paged list (`GET /page` - `orgId` OPTIONAL: absent lists all campuses, a present
orgId filters to that campus), a list (`GET /list?orgId=&depId=`), detail
with the bound-device summary (`GET /{id}`), create, full update, status toggle, and
logical delete. Responses use the standard envelope. The class gate is
`system:room:list`; mutating methods additionally pin
`system:room:create` / `system:room:update` / `system:room:status` /
`system:room:delete`.

#### Scenario: Paged room list with bound device summary

- **WHEN** an authenticated client requests `GET /api/v1/system/org-rooms/page` with
  `orgId`, `pageNum`, `pageSize`
- **THEN** it returns `code: "200"` with paged room rows, each carrying
  `devId`/`devName`/`devAe`/`devIp`/`devPort` of the bound device (nulls when unbound),
  ordered by id descending

#### Scenario: Room detail echoes the bound device

- **WHEN** the detail endpoint is called for a room bound to a device
- **THEN** `data` carries the room fields plus `devId`, `devName`, `devAe`, `devIp`,
  `devPort` resolved from the live device row

#### Scenario: Room status toggles without a full body

- **WHEN** an authenticated client sends `PUT /api/v1/system/org-rooms/{id}/status`
  with the next `enable` code
- **THEN** only `enable` changes and `code: "200"` returns `true`

### Requirement: Exam Room Binding Validation

Room create SHALL require `oid`, `did`, `name`, and `enable`; `devId` and the
叫号 fields are optional. The room carries no type (诊室类型 dissolved, ruling
2026-11-12). `devId` MUST identify a live device of the same org; `oid`
and `did` MUST identify live parent rows, with `did` in the same org. A violation
SHALL reject `"A0400"` with no row created. The room stores only the device
reference; no device access field is copied onto the room.

#### Scenario: Create room binds a device by reference

- **WHEN** a create body carries the required room fields, an optional `devId`, and
  the optional 叫号 block `callIp/callPort/callMac`
- **THEN** the room is created with the device bound by `dev_id` (no device field is
  copied onto the room row) and `code: "200"` returns the new room id

#### Scenario: Room create rejects dead or foreign references

- **WHEN** the create body carries a `devId` from another org, a deleted device, a
  missing `oid`, a missing `did`, or a `did` outside the org
- **THEN** the envelope rejects with `code: "A0400"` and `data` null
- **AND** no room row is created

### Requirement: Exam Room Update and Delete

Room update SHALL full-replace the room's editable surface and support rebinding or
clearing `devId` in the same update. Room delete SHALL soft-delete the row, and a
missing room id SHALL reject `A0400`.

#### Scenario: Rebind or clear the room's device

- **WHEN** the update body carries a different `devId`, or `devId: null`
- **THEN** the room's `dev_id` is replaced (or cleared) in the same update - rebinding
  is always allowed, no exam data is touched

#### Scenario: Delete room is logical

- **WHEN** an authenticated client sends `DELETE /api/v1/system/org-rooms/{id}`
- **THEN** the row is logically deleted and `code: "200"` returns `true`
- **AND** a missing room id rejects `"A0400"`
