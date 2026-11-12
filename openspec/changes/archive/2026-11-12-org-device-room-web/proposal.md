# Proposal

## Why

The console's 组织架构 group manages orgs and departments only - there is no surface
to register the department's devices (assets with their DICOM access points) or its
exam rooms (the places exams anchor on). The web half of the `org-device-room`
coordination feature builds the two registry pages against the REAL
`/api/v1/system/org-devices` and `/api/v1/system/org-rooms` backend delivered by
`org-device-room-api`.

## What Changes

- New console pages under 组织架构: 设备档案 (`system/device/index`) and
  诊室配置 (`system/room/index`), both the OPS-shaped left-org-tree +
  right-table + drawer-form layout reusing the shared `OrgTree` component as a pure
  selection context (科室 page precedent).
- 设备档案: paged table (设备名称/编号, 状态 tag, 类型, 归属类型, 使用期限,
  品牌/型号规格, 负责人/电话), filters type/status/keyword, create/edit drawer with
  基础信息 + 采购信息 + 接入与采集 (AE/IP/Port, Box, 图像预设) tabs, delete with
  the backend room-reference guard surfaced.
- 诊室配置: paged table (诊室名称/编码, 状态, 绑定设备, AE, 负责科室), filters
  dep/name, create/edit drawer with 基础信息 + 绑定设备 (org-scoped device picker +
  read-only AE/IP/Port echo) + 叫号设置 tabs, status toggle row action, delete.
- Typed API client modules `src/api/orgDevice.ts` / `src/api/orgRoom.ts` written from
  the api spec deltas; labels via the dict lookup (设备类型 by VALUE token,
  1025/1026 by code + common_status).
- Affordances gated by the button perms seeded by the api change
  (`system:device:*` / `system:room:*`); no invented strings.

## Capabilities

### New Capabilities

(none - the pages extend the web-system-console capability)

### Modified Capabilities

- `web-system-console` - ADDED requirements: Device Archive Page Shell, Device Archive
  Form and Delete, Exam Room Page Shell, Exam Room Device Binding.

## Impact

- `lumen-web/src/pages/system/device/` + `system/room/` (new), `src/api/orgDevice.ts`
  + `orgRoom.ts` (new), route/menu wiring per the seeded component paths.
- Depends on `org-device-room-api` being applied and user-verified first (backend-first
  ruling); `pnpm dev` proxies the real `lumen-system` (`.env.local` direct-service
  override when nginx is absent).
- New pages only - no existing page is edited; the shared `OrgTree` component is
  consumed as-is (composition, never modification).
