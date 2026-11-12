# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: org-device-room-web
- **Problem to solve**:
The console's 组织架构 group manages orgs and departments only - there is no surface
to register the department's devices (assets with their DICOM access points) or its
exam rooms (the places exams anchor on). The web half of the `org-device-room`
coordination feature builds the two registry pages against the REAL
`/api/v1/system/org-devices` and `/api/v1/system/org-rooms` backend delivered by
`org-device-room-api`.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: prototype-first build of the 设备档案 + 诊室配置 console pages - OrgTree
  selection context, paged tables with dict-coded filters, drawer forms (device:
  基础/采购/接入与采集; room: 基础/绑定设备/叫号设置), org-scoped device picker modal
  with read-only AE/IP/Port echo, status toggle + delete with guard surfacing; typed
  API clients `src/api/orgDevice.ts` / `src/api/orgRoom.ts`; lazy routes per the seeded
  menu components; component tests mocking the API seam.
- **Out of scope**: backend work (sibling `org-device-room-api` - applied + verified
  2026-11-12); modifying the shared `OrgTree` component or any existing page;
  device-access editing on the room form; 技师绑定 UI; a mock data layer (ruling
  2026-11-05 - real backend only at runtime).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
5:### Requirement: Device Archive Page Shell
14:#### Scenario: Org selection scopes the table
21:#### Scenario: Type and status filters
26:### Requirement: Device Archive Form and Delete
34:#### Scenario: Create device through the drawer
42:#### Scenario: Edit echoes and full-updates
47:#### Scenario: Delete guard surfaces the room count
53:### Requirement: Exam Room Page Shell
62:#### Scenario: Room list shows the bound device by reference
69:#### Scenario: Department filter narrows the list
74:#### Scenario: Status toggle row action
80:### Requirement: Exam Room Device Binding
87:#### Scenario: Create room with device binding
95:#### Scenario: Rebind or clear the device


## Design Constraints

- **Architecture/Decisions**:
### D1: Layout reuses the org-tree selection pattern
Both pages mount `<OrgTree>` as a pure selection context (no `actions` prop - the
科室 page shape) owning the `orgs.orgTree()` request; the right pane renders
filter bar + AntD Table (server pagination) + drawer forms. Deviation from OPS:
the tree is org-only (lumen's deps are a separate page), so the room page's
department filter is a `TreeSelect` fed by the existing org-deps list endpoint,
not a second tree column.
### D2: Drawer sections replace OPS's tabs where the content is short
Device drawer: single drawer, AntD `Tabs` with 基础信息 / 采购信息 / 接入与采集
(acquisition group includes the Box block + 图像预设 per the trim ruling). Room
drawer: `Tabs` 基础信息 / 绑定设备 / 叫号设置. 归属类型 = 自购/租赁 prefill 使用期限
(today → 2099-12-31) on ADD only (OPS behavior kept).
- **Technical constraints**: React 18 + TS strict + AntD 5 tokens (no ad-hoc hex; no
  red as primary); never-modify existing components - compose `OrgTree` as-is;
  requests only through the API client seam (no URL assembly in components); unit
  tests stub `@/api/orgDevice` / `@/api/orgRoom` modules; runtime always the real
  backend via the vite proxy; new pages are lazy route modules; dict labels via
  `dictItemsByName` by the exact catalog NAMES (design D5).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
10:- [ ] 1.1 Build the 设备档案 + 诊室配置 UI prototype with the `frontend-design` skill
18:- [ ] 2.1 Write `src/api/orgDevice.ts` + `src/api/orgRoom.ts` (page/list/detail/create/
21:- [ ] 2.2 Route + menu wiring: lazy routes `system/device/index` and `system/room/index`
27:- [ ] 3.1 Page shell: OrgTree selection + filter bar (type/status dict Selects +
31:- [ ] 3.2 Drawer: 基础信息 / 采购信息 / 接入与采集 (ae/ip/port + box block + 图像预设)
35:- [ ] 3.3 Delete with guard: Popconfirm → DELETE → backend room-count rejection shows as
41:- [ ] 4.1 Page shell: OrgTree selection + filter bar (department TreeSelect from
45:- [ ] 4.2 Drawer: 基础信息 (科室/名称/类型/启用) / 绑定设备 (picker modal over
49:- [ ] 4.3 Status toggle + delete: status endpoint call with revert-on-failure; logical
55:- [ ] 5.1 `pnpm build` + `pnpm test` green against the real-backend dev proxy
58:- [ ] 5.2 Spec-alignment self-check against the web delta WHEN/THEN list (L2 dry-run).

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: org selection scopes both tables (and
  the empty-context state); device create posts then refreshes, duplicate-code
  rejection surfaces on the 编号 field with the drawer open; device delete guard shows
  the room-count error and keeps the row; room rows render the server device summary
  with an unbound empty state; rebind/clear re-renders from a fresh detail fetch;
  status toggle reverts on failure; filters reset to page 1; perm-gated affordances
  hide.
- **Required edge cases**: unbound room (null device fields) renders placeholders,
  not crashes; device with no department; empty org device list in the picker; dict
  raw-code fallback when a catalog value is missing.
- **Regression-sensitive areas**: existing console pages and shell untouched; OrgTree
  behavior identical to its 科室 page usage; api client modules additive only.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 10 tasks, single module (lumen-web), sequential deps
  (prototype → clients → device page → room page), no risk indicators
  (user-edit-delete-profile-web precedent)

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: prototype visuals confirmed by the user BEFORE
  implementation (frontend-design gate); the real-backend smoke round-trip
  (create/bind/toggle/delete) against the verified api before verify
- **Blocking categories**: any edit to `openspec/specs/` or the shared `OrgTree`
  component; any mock data layer reintroduction

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
