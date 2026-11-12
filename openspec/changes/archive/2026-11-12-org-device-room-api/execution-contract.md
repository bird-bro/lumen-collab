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

- **Change name**: org-device-room-api
- **Problem to solve**:
The exam-workflow kernel anchors exam rows on ROOMS and integration routes DICOM by the
DEVICE's access point, but the platform has no device/room registry yet - the console's
组织架构 group only manages orgs and departments. A RIS department owns multiple rooms
and mixed device types (DR-1/DR-2, CT-1...), so the two concepts need separate registry
surfaces per the split-table ruling (2026-11-10).

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: V27 migration (`system.org_device` + `system.org_room` additive DDL,
  dict catalogs 1024-1026 (1023 + V26's 检查类型 1021 retired; 1024 doubles as
  检查类型), the two C pages + F nodes + admin grant);
  device endpoints
  `/api/v1/system/org-devices` (page/list/detail/create/update/logical-delete with the
  live-room-reference delete guard); room endpoints `/api/v1/system/org-rooms`
  (page/list/detail with device-summary echo/create/update/status/delete); entities +
  mappers + concrete services + DTOs in `org.birdbro.lumen.system`; TDD per scenario.
  Backend only - the web half is the sibling change `org-device-room-web`.
- **Out of scope**: 技师↔检查室绑定 (设备权限, workstation feature); 叫号/Box runtime
  protocols; cross-service Feign contracts; MWL/MPPS consumption; OPS data import;
  modifying any existing endpoint, table, or dict id.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
5:### Requirement: Device Registry Endpoints
14:#### Scenario: Paged device list scoped by org
22:#### Scenario: Keyword filters name or code
28:#### Scenario: Device list feeds the room picker
34:### Requirement: Device Create Validation
42:#### Scenario: Create device
52:#### Scenario: Device create rejects foreign or duplicate identifiers
59:### Requirement: Device Update and Delete
66:#### Scenario: Update device full-replaces the editable surface
72:#### Scenario: Delete device is logical and reference-guarded
79:### Requirement: Exam Room Registry Endpoints
89:#### Scenario: Paged room list with bound device summary
97:#### Scenario: Room detail echoes the bound device
103:#### Scenario: Room status toggles without a full body
109:### Requirement: Exam Room Binding Validation
117:#### Scenario: Create room binds a device by reference
124:#### Scenario: Room create rejects dead or foreign references
131:### Requirement: Exam Room Update and Delete
137:#### Scenario: Rebind or clear the room's device
143:#### Scenario: Delete room is logical
- `specs/system-data/spec.md`
5:### Requirement: Org Device Core Columns
14:#### Scenario: Fresh deployment creates the device asset columns
20:### Requirement: Org Device Access and Acquisition Columns
27:#### Scenario: Device is the access and acquisition source
34:### Requirement: Org Room Core Columns
41:#### Scenario: Fresh deployment creates the room registry
47:### Requirement: Org Room Binding and Call Columns
54:#### Scenario: The room table stays reference-only
60:### Requirement: Device and Room Dict Catalogs
67:#### Scenario: Fresh and hot databases converge on the same catalogs
74:### Requirement: Device and Room Dict Values
82:#### Scenario: Catalog values are stable across deployment
87:### Requirement: Device and Room Menu Pages
95:#### Scenario: Administrator sees the new pages after migration
100:### Requirement: Device and Room Button Perms
108:#### Scenario: Admin button perms arrive with the menus

## Design Constraints

- **Architecture/Decisions**:
### D1: Package + layering (naming.md service-package-layout)
`org.birdbro.lumen.system` additions: `entity/OrgDevice`, `entity/OrgRoom`; concrete
lumen services `service/OrgDeviceService`, `service/OrgRoomService` (single impl, no
interface - MenuService precedent); mappers `mapper/OrgDeviceMapper`, `OrgRoomMapper`;
DTOs `dto/deviceroom/` package (`DeviceCreateRequest`, `DeviceUpdateRequest`,
`DevicePageQuery`, `RoomCreateRequest`, `RoomUpdateRequest`, `RoomPageQuery`,
`RoomStatusRequest`, response shape = entity + device-summary fields on the room read
DTO `RoomDetail`/`RoomRow`). Controllers `controller/OrgDeviceController`
(`/api/v1/system/org-devices`), `controller/OrgRoomController` (`/api/v1/system/org-rooms`),
shaped 1:1 on `OrgDepController` (class gate + method perm pins).
### D2: Binding is by reference, reads join (the split-model consequence)
Room create/update stores ONLY `dev_id`; AE/IP/Port are never copied onto the room.
- **Technical constraints**: additive-first (no existing endpoint/table/dict touched;
  every insert ON CONFLICT-guarded for the hot test DB); EntityBase audit columns +
  Lombok combo on entities; zero physical FKs (service-level A0400 checks, D3/D4);
  room stores only `dev_id` - AE/IP/Port resolved by LEFT JOIN reads, never copied (D2);
  JDK 11 reactor build from `lumen-sev/` (explicit JAVA_HOME); Flyway only for DDL.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
10:- [ ] 1.1 Write `V27__org_device_room.sql`: `CREATE TABLE system.org_device` and
21:- [ ] 1.2 Add entities `OrgDevice` / `OrgRoom` (`entity/`, `@TableName`, Lombok combo
29:- [ ] 2.1 DTOs `dto/deviceroom/`: `DeviceCreateRequest`, `DeviceUpdateRequest`
33:- [ ] 2.2 `OrgDeviceService` (concrete, service-package-layout) with `getPage`,
42:- [ ] 2.3 TDD the perm pins: `@PreAuthorize` strings `system:device:create/update/
48:- [ ] 3.1 DTOs: `RoomCreateRequest` (oid/did/name/type/enable required; devId,
53:- [ ] 3.2 `OrgRoomService` with `getPage` (LEFT JOIN device summary - D2, no N+1),
62:- [ ] 3.3 TDD the perm pins incl. `system:room:status`. Verify: test green.
66:- [ ] 4.1 Full module suite `./mvnw -pl lumen-system -am test -q` green (existing
69:- [ ] 4.2 Spec-alignment self-check against the api delta WHEN/THEN list (L2

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: V27 migration (tables/columns/catalogs/
  menus exist + re-run row-count no-op); device page org-scoping + keyword/type/status
  filters; device create (duplicate-code + foreign-dep A0400 no-row) and delete guard
  (live-room count in msg); room page rows carry the device summary (nulls when
  unbound, no N+1); room create binds by reference (dead/foreign devId/oid/did A0400
  no-row, no device field copied); device/room full update clears nullable optional
  fields; room update rebind + clear; status toggle mutates enable only; perm-pin
  assertions for both controllers.
- **Required edge cases**: unbound room renders null device fields, not error; device
  with no department (did NULL) pages and edits fine; delete of an unbound device
  succeeds; logical deletes keep rows queryable only through live filters; dict codes
  serialize as JSON numbers.
- **Regression-sensitive areas**: existing OrgDep/Org/User/Dict/Menu endpoints
  byte-stable; V26 migration shape untouched; admin menu tree gains only the two new C pages and their F nodes.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 10 tasks, single module (lumen-system), sequential deps
  (migration → entities → DTOs → services → controllers), no risk indicators
  (user-edit-delete-profile precedent)

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: V27 migration idempotence on the shared hot test DB
  (hand-apply first, Flyway re-run = no-op) before any service boot against it; the
  room LEFT JOIN query reviewed for the no-N+1 contract
- **Blocking categories**: any edit to `openspec/specs/`; any physical FK or schema
  placement outside `system`; copying device access fields onto `org_room`

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
