# Tasks

Frontend half of the coordination feature `org-device-room`; builds against the REAL
backend delivered by `org-device-room-api` (backend-first ruling 2026-11-05). Starts
only after that change is applied and user-verified. Shared contracts:
`openspec/coordination/org-device-room.md`.

## 1. Prototype (new pages - frontend-design first)

- [x] 1.1 Build the 设备档案 + 诊室配置 UI prototype with the `frontend-design` skill
  (org-tree selection context, paged tables, drawer forms with the ruled tabs, device
  picker modal, read-only AE/IP/Port echo), static hard-coded demo data NOT via
  `src/api/`, pages identifiable as prototype. Verify: text/DOM checks pass; user
  confirms visuals. REVISED same day (second-round ruling): device 所属部门 required,
  device table gains 负责科室 column, tree root = all campuses hints; user confirmed
  the ruling covering the prototype deltas.

## 2. API clients (typed, from the spec deltas)

- [x] 2.1 Write `src/api/orgDevice.ts` + `src/api/orgRoom.ts` (page/list/detail/create/
  update/remove, room adds status) with TS types matching the api delta payloads
  (RoomRow device summary, raw dict codes). Verify: `pnpm build` type-checks green.
- [x] 2.2 Route + menu wiring: lazy routes `system/device/index` and `system/room/index`
  per the seeded menu components, gated by `system:device:list` / `system:room:list`.
  Verify: routes render for the seed admin (the C pages arrive via the api migration).

## 3. Device Archive page

- [x] 3.1 Page shell: OrgTree selection (root = all campuses, campus filters - D10
  semantics) + filter bar (type/status dict Selects + keyword) + paged table
  (设备名称/编号 composite cell, 状态 tag, 类型 (VALUE-token select), 归属类型, 负责科室, 使用期限,
  品牌/型号规格, 负责人/电话, 操作; dep names from the org-deps list map). Verify:
  RED component test per delta scenario (root-all vs campus filter, filter reset to
  page 1), then GREEN.
- [x] 3.2 Drawer: 基础信息 / 采购信息 / 接入与采集 (ae/ip/port + box block + 图像预设)
  with validation, 归属类型 prefill of 使用期限 on ADD (自购 → today~2099-12-31), edit
  echo via detail fetch, submit create/update, duplicate-code rejection surfaced on the
  编号 field. Verify: RED→GREEN tests (create refresh, echo, rejection stays open).
- [x] 3.3 Delete with guard: Popconfirm → DELETE → backend room-count rejection shows as
  error notification, row retained. Perm-gate affordances. Verify: RED→GREEN test
  (mock `@/api/orgDevice` rejection path).

## 4. Exam Room Configuration page

- [x] 4.1 Page shell: OrgTree selection (root = all campuses) + filter bar
  (department TreeSelect from org-deps list + name) + paged table (名称/编码, 状态
  switch or tag, 绑定设备 + AE, 负责科室, 操作 - no 类型 column, 诊室类型 dissolved). Verify: RED→GREEN tests
  (root-all vs campus filter, device summary renders, unbound empty state).
- [x] 4.2 Drawer: 基础信息 (科室/名称/启用) / 绑定设备 (picker modal over
  `/org-devices/list?orgId=&depId=` scoped to the selected 负责科室, read-only
  AE/IP/Port echo, 清除绑定) / 叫号设置
  (callIp/callPort/callMac). Create/update submit; post-save echo from fresh detail
  fetch. Verify: RED→GREEN tests (bind by reference, rebind, clear).
- [x] 4.3 Status toggle + delete: status endpoint call with revert-on-failure; logical
  delete with Popconfirm. Perm-gate incl. `system:room:status`. Verify: RED→GREEN
  tests.

## 5. Verify readiness

- [x] 5.1 `pnpm build` + `pnpm test` green against the real-backend dev proxy
  (manual smoke: create/bind/toggle/delete round-trip on the shared test DB via the
  user). Redirect output to `logs/build-web.log` / `logs/test-web.log`.
- [x] 5.2 Spec-alignment self-check against the web delta WHEN/THEN list (L2 dry-run).
