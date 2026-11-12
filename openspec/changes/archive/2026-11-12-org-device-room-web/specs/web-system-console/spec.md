# Spec Delta: web-system-console

## ADDED Requirements

### Requirement: Device Archive Page Shell

The device page SHALL present the org tree as the selection context (shared `OrgTree`,
pure selection - the 科室 page shape) and a right-side paged device table for the
selected org. It SHALL support `type`, `status`, and `keyword` filtering with
server-side pagination. Labels for 设备类型 SHALL resolve by the dict VALUE token;
设备状态/归属类型 render through the by-code dict lookup with raw-code fallback.
Mutation affordances SHALL follow the `system:device:*` button perms.

#### Scenario: Org selection filters the table

- **WHEN** the administrator selects a campus node
- **THEN** the table queries that campus's devices only and the pager reports the
  matched total
- **WHEN** the hospital root is selected
- **THEN** the table queries without an org filter and shows every campus's devices
  (root = all campuses)

#### Scenario: Type and status filters

- **WHEN** the administrator filters by 设备类型 or 设备状态
- **THEN** the table queries with the type VALUE token and the status dict code, and
  resets to the first page

### Requirement: Device Archive Form and Delete

Device create/edit SHALL use a drawer organized as 基础信息 / 采购信息 / 接入与采集
sections. A successful create or edit SHALL refresh the table, and duplicate-code
rejection SHALL surface on the 编号 field with the drawer open. When the backend
rejects delete because live rooms still reference the device, the rejection message
(with the room count) SHALL surface to the user and the row SHALL remain.

#### Scenario: Create device through the drawer

- **WHEN** the administrator opens the create drawer, fills the required fields
  (编号/名称/类型/状态/归属类型/所属部门) and optional profile, access (`ae/ip/port`),
  and acquisition (`box*`, capture/prune) fields, and submits
- **THEN** the page posts to the real create endpoint, closes the drawer on success,
  and refreshes the current page

#### Scenario: Edit echoes and full-updates

- **WHEN** the administrator opens the edit drawer for a row
- **THEN** the form echoes the fetched detail and submitting performs the full update

#### Scenario: Delete guard surfaces the room count

- **WHEN** the administrator deletes a device still referenced by live rooms
- **THEN** the backend A0400 message (naming the room count) shows as an error
  notification and the table does not lose the row

### Requirement: Exam Room Page Shell

The room page SHALL present the org tree as the selection context and a right-side
paged room table (名称/编码, 状态, 绑定设备 with its AE, 负责科室) with name and
department filtering. It SHALL provide a row status toggle via the status endpoint
and delete. The status label renders through the dict lookup; the page carries no
类型 column (诊室类型 dissolved, ruling 2026-11-12). Mutation affordances SHALL
follow the `system:room:*` button perms (启停 gated by `system:room:status`).

#### Scenario: Room list shows the bound device by reference

- **WHEN** the table renders rooms for the selected org
- **THEN** each row shows the bound device's name and AE from the server-resolved
  summary (empty state when unbound)
- **AND** the page never renders device access fields from the room row itself

#### Scenario: Department filter narrows the list

- **WHEN** the administrator filters by 负责科室 or room name
- **THEN** the table queries with those parameters and resets to the first page

#### Scenario: Status toggle row action

- **WHEN** the administrator toggles a room's 启用 switch (perm-gated)
- **THEN** the page calls the status endpoint with the next dict code and reflects the
  server answer, reverting the switch on failure

### Requirement: Exam Room Device Binding

The room form SHALL be organized as 基础信息 / 绑定设备 / 叫号设置 sections. 绑定设备
SHALL pick from the org's live devices (picker dialog listing name/code/type) and
SHALL echo the bound device's AE/IP/Port read-only from the room detail response.
The page SHALL never invent or edit device access fields on the room form.

#### Scenario: Create room with device binding

- **WHEN** the administrator creates a room, selects 负责科室, optionally binds a
  device from the picker, and submits
- **THEN** the page posts to the real create endpoint and refreshes on success
- **AND** the drawer's device echo after save comes from a fresh detail fetch, not
  local state

#### Scenario: Rebind or clear the device

- **WHEN** the edit drawer submits a different device, or clears the binding
- **THEN** the room row re-renders with the new device summary (or empty state)
