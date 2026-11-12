# Proposal

## Why

The exam-workflow kernel anchors exam rows on ROOMS and integration routes DICOM by the
DEVICE's access point, but the platform has no device/room registry yet - the console's
组织架构 group only manages orgs and departments. A RIS department owns multiple rooms
and mixed device types (DR-1/DR-2, CT-1...), so the two concepts need separate registry
surfaces per the split-table ruling (2026-11-10).

## What Changes

- New `system.org_device` table (asset + DICOM access block `ae/ip/port` + Box/acquisition
  block) and `system.org_room` table (business place + 叫号 block + rebindable `dev_id`),
  schema `system`, migration `V27__org_device_room.sql`.
- Three new dict catalogs 1024 设备类型 / 1025 设备状态 / 1026 归属类型 (V23/V26 band
  style; 1023 retired with the dissolved 诊室类型), plus the guarded retirement of
  V26's 1021 检查类型: ONE catalog 1024 serves as 设备/检查类型 (no duplicate
  vocabulary). The modality catalog stores the VALUE token ('CT'/'DR') as the machine
  value - org_device.type is varchar saving the token (unification ruling
  2026-11-12).
- New `lumen-system` endpoints `/api/v1/system/org-devices` and `/api/v1/system/org-rooms`
  (page/list/detail/create/update/delete, rooms add the status toggle), button-perm gated
  (`system:device:*` / `system:room:*`). Second-round ruling: device `did`
  required (service-enforced), page `orgId` optional (root = all campuses).
- Menu seeds: C pages 设备档案 / 诊室配置 under 组织架构 (13, CONTENT-guarded serial
  ids per the V15 discipline) plus their F button nodes, admin auto-grant guarded.
- OPS baggage dropped per the 2026-11-12 ruling (车载/萤石云/租赁商/合同ID; room device
  shadow columns).

## Capabilities

### New Capabilities

(none - the feature extends existing capability boundaries)

### Modified Capabilities

- `api` - ADDED requirements: Device Registry Endpoints, Device Create Validation,
  Device Update and Delete, Exam Room Registry Endpoints, Exam Room Binding Validation,
  Exam Room Update and Delete.
- `system-data` - ADDED requirements: Org Device Core Columns, Org Device Access and
  Acquisition Columns, Org Room Core Columns, Org Room Binding and Call Columns, Device
  and Room Dict Catalogs, Device and Room Dict Values, Device and Room Menu Pages,
  Device and Room Button Perms.
- `web-system-console` - modified by the sibling `org-device-room-web` change; this
  API change only seeds the page/button perm strings.

## Impact

- `lumen-sev/lumen-system` - new entity/mapper/service/controller triplets + DTOs
  (`org.birdbro.lumen.system.*`), Flyway `V27__org_device_room.sql`.
- `openspec/specs/api` + `system-data` grow six/eight added requirements; the web
  console delta lives in `org-device-room-web`.
- No existing endpoint, table, or dict id is touched (additive-first; every insert
  ON CONFLICT-guarded per the hot-DB discipline).
