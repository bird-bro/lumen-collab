# org-device-room — Coordination (Cross-Stack Feature)

> Feature: the console grows the OPS-shaped device archive + exam-room configuration
> pair (reference OPS `/manage/org/device` and `/manage/org/room`, ops_web
> `src/views/system/org/{device,room}`): left org-tree + right table + drawer forms,
> re-modeled per the lumen split-table ruling. Origin: user request 2026-11-12
> ("可以开始设备档案和诊室配置这2个页面的开发了,参考ops项目中/manage/org/device和
> /manage/org/room"), field-trim + scope rulings same day (see Decisions).

## Why

A RIS cannot run without its resource registry: the department owns multiple rooms and
device types (DR-1/DR-2, CT-1, CT-2…), exam rows anchor on ROOMS, integration routes
MWL/MPPS/storage by the DEVICE's DICOM access point, and the future technician
「设备权限」 is a bound-room set. OPS stacks device access config onto `org_room`
(a historical one-table pile-up); lumen splits the concepts per the 2026-11-10 ruling.

## Shared Design

- **Storage** (schema `system`, migration `V27__org_device_room.sql`, table-REBUILD-free
  additive DDL in the V16/V17 audit-block-tail discipline):
  - `org_device` — the ASSET + DICOM access point. `id` PK (house style, integer
    auto-increment), `code` (设备编号), `name`, `type` (dict 1024 设备类型),
    `status` (dict 1025 设备状态), `tag` (dict 1026 归属类型, OPS codes 10 自购 /
    11 租赁 kept), `brand`, `model`, `spec`, `serial_num`, `manufacturer`,
    `production_date`, `purchase_date`, `acceptance_date`, `responsible`,
    `responsible_phone`, `start_date`, `end_date` (使用期限), `remark`, `oid`, `did`
    (nullable — a device may hang off the org without a department), plus the access
    block: `ae`, `ip`, `port`, and the acquisition block `box_adapter`, `box_ip`,
    `box_port`, `box_mac`, `box_print`, `capture_w`, `capture_h`, `prune` (boolean),
    `prune_u/d/l/r` (integer). EntityBase audit columns (additive-first, never physical
    FKs on lumen tables).
  - `org_room` — the BUSINESS place. `id` PK, `oid`, `did` (both NOT NULL — a room is
    anchored, unlike a device), `name`, `code` (诊室编码, replaces OPS's dev_iot
    pipe-number display), `type` (dict 1023 诊室类型), `enable` (dict 1000
    common_status, toggle endpoint per the OrgDepController precedent), 叫号 block
    `call_ip`, `call_port`, `call_mac`, and `dev_id` (nullable FK-by-convention to
    `org_device.id`, SERVICE-enforced like `user_info.dep_id` — rebindable anytime).
    EntityBase audit columns.
- **Dict catalogs** (deterministic ids, next free band after V26's 1021/1022; details
  id = `ca_id * 1000 + sort`, ON CONFLICT-guarded, V23/V26 house style):
  - 1023 诊室类型: 1 普通诊室 / 2 CT室 / 3 MR室 / 4 DR室 / 5 DSA室 / 6 超声室 /
    7 内镜室 (新增分类直接在此维护).
  - 1024 设备类型: 1 DR / 2 CT / 3 MR / 4 DSA / 5 US / 6 ENDO (DICOM-modality-shaped;
    independent from the user-visibility 检查类型 1021 — an asset list may carry
    non-exam equipment later).
  - 1025 设备状态: 1 在用 / 2 停用 / 3 维修 / 4 报废 (asset lifecycle, replaces the
    OPS deviceStatus dict).
  - 1026 归属类型: 10 自购 / 11 租赁 (OPS numeric codes preserved, V23 style).
- **API contracts** (`lumen-system`, envelope per `specs/api`):
  - `/api/v1/system/org-devices` — `GET /page` (orgId OPTIONAL - absent lists all
    campuses, the department page's root-means-all semantics; type/status/keyword
    optional), `GET /list?orgId=&depId=` (picker feed, department-scoped when depId
    is present), `GET /{id}`, `POST` create, `PUT /{id}`
    full update (nullable optional fields clear), `DELETE /{id}` logical delete. Perms
    `system:device:{list,create,update,delete}`.
  - `/api/v1/system/org-rooms` — `GET /page` (orgId OPTIONAL - absent lists all;
    depId/name optional),
    `GET /list?orgId=&depId=`, `GET /{id}` (echoes bound device summary — name/ae/ip/
    port resolved server-side), `POST` create, `PUT /{id}` full update (nullable
    optional fields clear, including `devId`), `PUT /{id}/status` toggle,
    `DELETE /{id}` logical delete. Perms `system:room:{list,create,update,delete,status}`.
  - Both resources: `dev_id` and the required `oid`/`did` (device `did` REQUIRED on
    create + update per the second-round ruling) are validated
    against live parent rows (dead or cross-org ids reject `A0400`, no-change pattern per
    the users D-contract); service-level uniqueness uses device `code` within the org and
    room `name` within the department (duplicate live pair rejects `A0400`).
- **Menu + button perms** (migration, V15/V22 CONTENT-guard discipline): C pages
  设备档案 (up 13 组织架构, `system:device:list`, path `device`, component
  `system/device/index`, sort 3) and 诊室配置 (up 13, `system:room:list`, path
  `room`, component `system/room/index`, sort 4), serial ids with a setval tail -
  NOT explicit ids, because V14's setval tail lets fresh-deployment serial F/W nodes
  occupy any low id (V26 header: "explicit ids were proven wrong by V15's own
  header"); F button nodes under each (新建/编辑/删除, serial-inserted without
  fixed ids); admin auto-grant guarded inserts keyed by perms.

## Decisions

- **Split tables** (user ruling 2026-11-10, memory `device-room-separate-tables-model`):
  `org_device` (asset + AE/IP/Port + Box/acquisition) and `org_room` (business + 叫号 +
  rebindable `dev_id`), NOT the RCP inline model. AE/IP/Port and Box are the DEVICE's
  port config; 叫号 is the ROOM's config (the queue screen hangs at the room door).
- **Field trim** (user ruling 2026-11-12): OPS baggage dropped — 车载DR三件套
  (license_plate/frame_no/engine_no), 萤石云视频 (video/video_out/ezviz_serial_in/out),
  租赁商 (lessor_name/lessor_contact), 合同ID (contract_id), OPS device.enable
  (预留), room.dev_iot/dev_tag/dev_about/dev_brand/dev_version (device-shadow columns —
  the bound device IS the source). Acquisition preset (capture/prune) KEPT with the
  Box block on the device.
- **技师绑定 Non-Goal** (user ruling 2026-11-12): the technician↔room binding (设备
  权限) is NOT in this feature — it lands with the workstation/worklist consumer.
  This feature delivers the two registry pages only.
- **Modality lives on the device** (design, sharpened 2026-11-12 third round): a
  room's exam type is whatever the bound device is; the room table carries NO type
  column AT ALL (诊室类型 dissolved - rooms hang off departments, the earlier
  room-classification reading is superseded).
- **设备强制定科 + 根=全部院区** (user ruling 2026-11-12, second round — prototype
  review question「诊室和设备是否要归属于院区下的科室?」): devices REQUIRE a
  department (`did` service-enforced A0400 on create/update, the hospital asset
  「使用科室」 convention and OPS's required did; the DB column stays NULLABLE -
  no V27 churn). The device table gains a 负责科室 column (name resolved
  client-side from the org-deps list, no new read field). BOTH pages adopt the
  department-page tree semantics: hospital root = ALL campuses (page query without
  orgId), a campus node filters to it - the original orgId-required page left the
  auto-selected root showing an empty table on multi-campus hospitals. The room's
  device picker feeds from the SAME department (`/org-devices/list?orgId=&depId=`,
  binding validation stays same-org as the server-side backstop).
- **Menu ids are data, not contract** (corrected 2026-11-12 during apply TDD): the
  original plan pinned C-node ids 18/19, but V14's setval tail means a FRESH
  deployment's V15 serial F nodes occupy ids 18+ - explicit low ids collide (the hot
  test DB happens to sit at 120+, masking the trap). The C pages seed CONTENT-guarded
  (up_id 13 + type + perms NOT EXISTS, serial + setval); only perms strings contract.
- **Dict bands 1024-1026, single modality catalog** (id discipline + third-round
  ruling 2026-11-12): V26 stopped at 1022; 1023 is RETIRED with the dissolved 诊室类型
  (never seeded); the device vocabularies take 1024-1026. The user's FINAL
  unification: NO duplicate vocabulary - 1024 设备类型 ALSO serves as 检查类型 (the
  users page's modalitys multi-select reads it; future exam tables join it), and V26's
  检查类型 catalog 1021 is deleted by V27 (guarded by name). The modality catalog
  stores the detail VALUE ('CT'/'DR') as the machine token - org_device.type is
  varchar(16) saving the token verbatim. 设备状态/归属类型 keep the integer-code
  convention.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | org-device-room-api | implemented 2026-11-12, awaiting verify | - |
| frontend | org-device-room-web | implemented 2026-11-12, awaiting verify + user smoke | - |

## Gate

Verify each stack independently. When ALL registered changes have an acceptable verify
credential (`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and
batch-archive them.

## Non-Goals

- 技师↔检查室绑定 (设备权限) — workstation/worklist feature; service-side enforcement
  shape to be decided there (RCP uses a frontend dropdown filter; lumen may harden).
- 叫号 actual calling protocol (config columns only; no queue integration).
- Box 采集 runtime (config columns only; no capture service).
- Department-room/device scoping in radiology/us/endo flows (integration consumes the
  registry by device AE later).
