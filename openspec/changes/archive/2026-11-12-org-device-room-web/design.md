# Design

## Context

Frontend half of `org-device-room` (coordination: `openspec/coordination/
org-device-room.md`). Reference: OPS `ops_web/src/views/system/org/{device,room}`
(Vue + Element Plus) re-expressed in the lumen console idiom (React 18 + AntD 5).
Backend endpoints land first via `org-device-room-api` - the pages build against the
real API only (no mock layer, ruling 2026-11-05).

## Goals / Non-Goals

- Goals: the two console pages per the web-system-console delta, typed API clients,
  dict-driven labels, perm-gated affordances, UI prototype (frontend-design skill,
  new pages) → user confirms → then implementation.
- Non-Goals: editing the shared `OrgTree` component (consume as-is), 技师绑定 UI,
  any device-access editing on the room form, menu management edits (the api change
  seeds the two C pages; the SPA routes follow the seeded component paths).

## Decisions

### D1: Layout reuses the org-tree selection pattern

Both pages mount `<OrgTree>` as a pure selection context (no `actions` prop - the
科室 page shape) owning the `orgs.orgTree()` request; the right pane renders
filter bar + AntD Table (server pagination) + drawer forms. Deviation from OPS:
the tree is org-only (lumen's deps are a separate page), so the room page's
department filter is a `TreeSelect` fed by the existing org-deps list endpoint,
not a second tree column. SECOND-ROUND RULING (2026-11-12): tree selection FOLLOWS
the department page semantics - the hospital root queries WITHOUT orgId (all
campuses), a campus node filters; the device table carries a 负责科室 column whose
name resolves from the org-deps list map (id -> name, no new read field).

### D2: Drawer sections replace OPS's tabs where the content is short

Device drawer: single drawer, AntD `Tabs` with 基础信息 / 采购信息 / 接入与采集
(acquisition group includes the Box block + 图像预设 per the trim ruling); 所属部门
is REQUIRED (second-round ruling - the 使用科室 anchor). Room drawer: `Tabs` 基础
信息 / 绑定设备 / 叫号设置. 归属类型 = 自购/租赁 prefill 使用期限 (today →
2099-12-31) on ADD only (OPS behavior kept).

### D3: Device picker is a modal over the org device list

绑定设备 opens a modal fed by `GET /org-devices/list?orgId=&depId=` scoped to the
form's selected 负责科室 (single-select; row shows name/code/type-label). After binding, the drawer echoes AE/IP/Port from the
room detail response (read-only Text) - never editable on the room form (the split
model's core consequence). Rebind = pick again; clear = a 清除绑定 button.

### D4: API clients follow the api-spec deltas

`src/api/orgDevice.ts` (page/list/detail/create/update/remove) and
`src/api/orgRoom.ts` (page/list/detail/create/update/status/remove) typed from the
delta, same client seam as the existing system modules (page passes params; no URL
assembly in components). Room read DTO carries devId/devName/devAe/devIp/devPort.

### D5: Dict labels via the existing lookup

`dictItemsByName` by the exact catalog names (设备类型 / 设备状态 / 归属类型 /
通用状态) with raw-code fallback chips; the modality pair (设备类型, and 检查类型
when a consumer arrives) renders by VALUE token via `resolveDictLabelByValue`, the
numeric catalogs by code (`resolveDictLabel`)
(role-type-dict D4 shape; the lookup contract is by NAME, not English aliases). 状态
tag colors: keep AntD defaults, no red as a primary (product note).

### D6: Perm gating

Row affordances render by `system:device:create/update/delete` /
`system:room:create/update/delete/status` from the session perm set
(button-perm-enforcement precedent); the 添加 button hides without create perm; the
page itself is route-guarded by the pages' perms (`system:device:list` / `system:room:list`).

## Risks / Trade-offs

- Two new left-tree pages grow the console bundle; both are lazy route modules per
  the SPA plan (departments pattern), so the cost is two chunks, not the shell.
- The room form's read-only device echo depends on the api detail echo; if the
  backend change slips, the page fails visibly (proxy error) - by design
  (no mock layer).

## Migration Plan

Prototype (frontend-design, text/DOM checks) → user confirms visuals → implement
with TDD (component tests stub `@/api/orgDevice` / `@/api/orgRoom` at the seam) →
`pnpm build` + suite green → verify against the delta WHEN/THEN.

## Open Questions

- (none - field trim, binding semantics, and scope ruled 2026-11-12)
