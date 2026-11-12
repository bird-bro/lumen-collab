# Spec Delta: system-data

## ADDED Requirements

### Requirement: Org Device Core Columns

The `system` schema SHALL contain `system.org_device` as the device-asset registry
with `id` integer PK, `code`, `name`, `type` varchar(16) NOT NULL storing the
dict-1024 VALUE (the modality token 'CT'/'DR' - business matching joins on the token,
ruling 2026-11-12), `status` (dict 1025), `tag` (dict 1026),
`brand/model/spec/serial_num/manufacturer/production_date/
purchase_date/acceptance_date/responsible/responsible_phone/start_date/end_date/
remark`, `oid` NOT NULL, `did` NULL, and the EntityBase audit columns. It SHALL be
created additively with zero physical FKs.

#### Scenario: Fresh deployment creates the device asset columns

- **WHEN** Flyway runs the new migration on an empty `system` schema
- **THEN** `org_device` exists with the ruled asset, ownership, and audit columns
- **AND** it carries no physical foreign key

### Requirement: Org Device Access and Acquisition Columns

`system.org_device` SHALL carry the DICOM access block `ae`, `ip`, and `port`, plus
the acquisition block `box_adapter`, `box_ip`, `box_port`, `box_mac`, `box_print`,
`capture_w`, `capture_h`, `prune` (boolean), `prune_u`, `prune_d`, `prune_l`, and
`prune_r`. These columns SHALL live on the device, not on the room.

#### Scenario: Device is the access and acquisition source

- **WHEN** a device row is created with its access and acquisition settings
- **THEN** those settings are stored on `org_device`
- **AND** they can be resolved for consumers from the device reference, not copied to
  `org_room`

### Requirement: Org Room Core Columns

The `system` schema SHALL contain `system.org_room` as the business-place registry
with `id` integer PK, `oid` NOT NULL, `did` NOT NULL, `name`, `code`, `enable`
(dict 1000 codes), and the EntityBase audit columns. It SHALL be created additively
with zero physical FKs. The room carries NO type column (诊室类型 dissolved - rooms
hang off departments; ruling 2026-11-12).

#### Scenario: Fresh deployment creates the room registry

- **WHEN** Flyway runs the new migration on an empty `system` schema
- **THEN** `org_room` exists with the ruled place, status, and audit columns
- **AND** it carries no physical foreign key

### Requirement: Org Room Binding and Call Columns

`system.org_room` SHALL carry `dev_id` NULL as a service-enforced reference to
`org_device.id` and the 叫号 block `call_ip`, `call_port`, `call_mac`. No column of
the OPS inline shape (room-carried `dev_ae`/`dev_ip`/`dev_port`/box/capture) SHALL
appear on `org_room`.

#### Scenario: The room table stays reference-only

- **WHEN** a room row binds a device
- **THEN** only `dev_id` links them; `ae`, `ip`, `port`, and any box/capture column are
  absent from `org_room` (the device row is the single source)

### Requirement: Device and Room Dict Catalogs

The dict model SHALL carry the deterministic catalogs 1024 设备类型, 1025 设备状态,
and 1026 归属类型 in the V26 successor band, and SHALL RETIRE the V26 catalog
1021 检查类型 (guarded delete by name): 1024 设备类型 is the SINGLE modality
vocabulary serving both device and exam references (unification ruling
2026-11-12 - no duplicate vocabulary to keep in sync). The 1023 band slot stays
UNSEEDED (the dissolved 诊室类型 - the id is retired, not reused). Detail ids follow
`ca_id * 1000 + sort`, and every statement SHALL be idempotent so the shared hot
test DB and a fresh deployment converge.

#### Scenario: Fresh and hot databases converge on the same catalogs

- **WHEN** the seed migration runs on a fresh database, or is hand-applied to the
  already-migrated shared test DB
- **THEN** the three catalogs exist with the same deterministic ids and no duplicate
  detail rows (检查类型 retired, 1023 empty)

### Requirement: Device and Room Dict Values

The seeded vocabularies SHALL be: 设备类型 (1024, ALSO the 检查类型 source - one
catalog serves both) values DR / CT / MR / DSA / US / ENDO (admin codes 1-6; the
VALUE is the machine token - org_device.type stores it verbatim and future exam
tables join on the same token, ruling 2026-11-12); 设备状态 1 在用 / 2 停用 / 3 维修 /
4 报废; and 归属类型 10 自购 / 11 租赁 (the OPS numeric codes preserved). The frontend
renders the modality catalog by VALUE and the numeric catalogs via the existing
by-name/by-code dict lookup.

#### Scenario: Catalog values are stable across deployment

- **WHEN** an operator reads each new catalog after migration
- **THEN** the enabled detail codes and labels match the ruled vocabularies exactly

### Requirement: Device and Room Menu Pages

The console menu tree SHALL gain, under 组织架构 (menu 13): the C page 设备档案
(`system:device:list`, path `device`, component `system/device/index`, sort 3) and the
C page 诊室配置 (`system:room:list`, path `room`, component `system/room/index`,
sort 4). Both C-page inserts SHALL be CONTENT-guarded (NOT EXISTS among live rows by
up_id + type + perms) with serial ids and a setval tail - the V15 discipline, since
V14's setval tail means serial F/W nodes may already occupy any low explicit id on a
fresh deployment. Menu ids are data, never contract; the perms strings are.

#### Scenario: Administrator sees the new pages after migration

- **WHEN** the menu migration has run and the seed admin requests its menu tree
- **THEN** 设备档案 and 诊室配置 render as children of 组织架构
- **AND** the inserts re-run as no-ops on a database where later serial menus already
  advanced the id sequence

### Requirement: Device and Room Button Perms

The new pages SHALL gain F button nodes 新建/编辑/删除 under menu 18
(`system:device:create/update/delete`) and 新建/编辑/删除/启停 under menu 19
(`system:room:create/update/status/delete`). The F nodes SHALL be serially inserted
without fixed ids and NOT EXISTS-guarded; the administrator role SHALL be auto-granted
the new nodes via guarded inserts.

#### Scenario: Admin button perms arrive with the menus

- **WHEN** the menu migration has run and the seed admin requests its permission set
- **THEN** all device and room button perm strings are present and bound to role 1
  without any manual grant
