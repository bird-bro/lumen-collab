# web-system-console Specification

## Purpose

Defines the system administration console: pages that manage the entities lumen-system serves -
org tree, departments, users, roles and their menu grants, the menu tree, dictionaries, and
operation logs. Every page talks to the endpoints the `api` capability documents (mocked in
development until backend wiring) and follows the OPS console pattern: a left organization-tree
aside plus a right work area where applicable.

## Requirements

<!-- synced from: password-transport-encryption-web 2026-11-09 -->

### Requirement: User Management
The console SHALL provide a user list view (`GET /api/v1/system/users/list/profile`,
which returns a plain list - the view paginates client-side) with create
(`POST /api/v1/system/users`), profile detail (`GET /api/v1/system/users/{id}`), profile
edit (`PUT /api/v1/system/users/{id}`), delete (`DELETE /api/v1/system/users/{id}`),
password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form. The create request's `password` and the
reset request's `newPassword` SHALL be SM2-encrypted with the session-cached public key from
`GET /api/v1/system/auth-key` (C1C3C2 hex — coordination password-transport-encryption D4/D6)
inside the API-client boundary (`src/api/users.ts` keeps raw-password signatures; the typed
value never reaches the wire as plaintext). A backend `A0400` rejection SHALL surface
through the same message path as other `A`-class failures on the form. The list SHALL render
the columns 用户+账号 / 角色 / 科室 / 职称 / 手机号 / 邮箱 / 启用 / 创建人+创建时间 / 操作; the
role chips come from the row payload's `roleNames` (no session-profile substitute). The
create and edit dialogs SHALL carry the shared profile form (姓名/手机号/邮箱/性别/职称/
所属科室/角色/检查类型/审核权限/签名) — the edit dialog prefills from the detail endpoint
and keeps 登录账号 disabled. Gender/title/exam-type/audit-tier options SHALL resolve through
the dict-items-by-name lookup with raw-code fallback; the department options SHALL come from
the departments list client; the signature SHALL upload through the common image upload
endpoint as a controlled form control. Edit and delete affordances SHALL be gated by the
seeded `system:user:update` / `system:user:delete` button permissions; delete SHALL confirm
before calling the endpoint and a protected-account rejection SHALL surface the backend's
`msg`.

#### Scenario: Paged user list

- **WHEN** an administrator opens the user page
- **THEN** the user list renders and re-queries on filter/page change

#### Scenario: List columns render the profile payload

- **WHEN** the list returns rows with profile fields and `roleNames`
- **THEN** the 用户 column shows the display name above the account (the first-login
  password hint follows the name), the 手机号 and 启用 cells are centered, the 角色 chip
  text uses the table's body font size, and the 用户/角色/科室/职称/手机号/邮箱/创建人/
  创建时间 cells render the payload values (an unbound value renders as an empty dash,
  not an error); the operation verbs order 重置密码/修改/删除 and every icon-only action
  exposes its verb as a body-size styled hover tooltip

#### Scenario: Role chips come from the row

- **WHEN** any row carries `roleNames`
- **THEN** the chips render exactly those names — including rows that are not the
  current session user (the former 未提供 chip no longer appears)

#### Scenario: Duplicate username feedback

- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Create dialog carries the profile form

- **WHEN** the administrator opens the create dialog
- **THEN** it offers 用户名/姓名/手机号/邮箱/性别/职称/所属科室/角色/检查类型/审核权限/
  签名/初始密码 with the required-field markers on 用户名/姓名/初始密码, and a successful
  submit returns to the refreshed list

#### Scenario: Exam-type and audit-tier selects are dict-driven

- **WHEN** the 检查类型 multi-select or the 审核权限 select renders its options
- **THEN** the options resolve through the dict-by-name lookup (catalogs 检查类型 /
  审核权限) with the raw-code fallback on a missing catalog or failed fetch (the
  role-type-dict-web D4 precedent); 检查类型 submits a code array serialized as CSV

#### Scenario: Signature upload reuses the common transport

- **WHEN** the administrator picks a signature image in either dialog
- **THEN** the image flows through the common image upload endpoint, the control
  previews the stored path, and the dialog submits the path value (client-side
  extension/size validation mirrors the logo control; the path stays verbatim in the
  profile payload)

#### Scenario: Edit opens prefilled with detail

- **WHEN** the administrator presses 编辑 on a row
- **THEN** the dialog opens prefilled from `GET /api/v1/system/users/{id}` with 登录账号
  disabled, and a successful submit calls the profile update and refreshes the list

#### Scenario: Edit failure keeps the dialog

- **WHEN** the update is rejected (A-class msg or validation)
- **THEN** the dialog stays open with the backend `msg` on the form; no list reload

#### Scenario: Delete confirms then removes

- **WHEN** the administrator presses 删除 and confirms the Popconfirm
- **THEN** the delete endpoint is called and the row disappears from the list

#### Scenario: Protected-account delete rejection surfaces

- **WHEN** the backend rejects a delete (self or seed admin) with an `A`-class msg
- **THEN** the message surfaces as an error toast and the row stays

#### Scenario: Dict-driven gender and title with raw-code fallback

- **WHEN** the 性别/职称 options resolve (dict catalogs 性别/职称)
- **THEN** the selects render dict labels for stored codes; a code missing from the dict
  or a failed fetch renders the raw code as the label and one error message, never
  blocking the page (the role-type-dict-web D4 precedent)

#### Scenario: Department select lists departments

- **WHEN** the create or edit dialog opens
- **THEN** the 所属科室 select lists the live departments from the departments list
  client (label = department name, value = department id)

#### Scenario: Password and enable actions

- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

#### Scenario: Encrypted password fields on create and reset

- **WHEN** the create dialog submits or the reset action confirms
- **THEN** the captured request body's `password` / `newPassword` value is a hex SM2
  ciphertext that differs from the typed value
- **AND** the backend accepts it (decrypt-then-BCrypt), so the operation succeeds exactly
  as the plaintext flow did before password-transport-encryption

#### Scenario: Edit and delete follow the new button permissions

- **WHEN** the session holds (or lacks) `system:user:update` / `system:user:delete`
- **THEN** the 编辑 / 删除 affordances render (or hide) accordingly, matching the
  button-perm-enforcement gating of the other row actions

<!-- synced from: user-edit-delete-profile-web 2026-11-11 -->

### Requirement: Role Management and Menu Assignment
The console SHALL provide role management backed by `GET /api/v1/system/roles/list`,
create (`POST /api/v1/system/roles`), edit (`PUT /api/v1/system/roles/{id}`), and delete
(`DELETE /api/v1/system/roles/{id}`). For each role, menus SHALL be assignable via a tree
with checkboxes that full-replaces the grant through `PUT /api/v1/system/roles/{id}/menus`.
The list is served by the real backend endpoint (the former dev-mock arrangement is
retired); its rows carry the `menuIds` grant echo that drives the assignment dialog's
initial checked set. The 类型 vocabulary SHALL be resolved from the data dictionary
(catalog 「角色类型」 via `GET /api/v1/system/dicts/items/{catalogName}`) instead of a
compile-time label map: the column renders the dict `value` matched by the row's `type`
code, and the create/edit drawers offer the dict's items as a Select (option value =
`code`, initialValue 10). When the dictionary lacks a code or the lookup fails, the
column falls back to raw-code rendering and one error message is surfaced — the page
never blocks, and the drawer's Select stays empty without breaking the CRUD round-trip
(the backend accepts the int as-is).

#### Scenario: Role CRUD round-trip
- **WHEN** an administrator creates, renames, and deletes a role
- **THEN** the documented endpoints are called and the list reflects the result

#### Scenario: Menu assignment is full-replace
- **WHEN** an administrator checks a set of menus for a role and saves
- **THEN** the checked set is sent as the role's complete grant
- **AND** reopening the dialog shows exactly that set

#### Scenario: Type label resolved from the dict
- **WHEN** the roles list loads with the 「角色类型」 dict carrying
  code 10 → 系统角色 and code 11 → RCP角色
- **THEN** each row's 类型 cell shows the dict value matched by its `type`
  with the established tag chrome (code 10 blue, other codes cyan)

#### Scenario: Unknown code falls back to the raw number
- **WHEN** a row's `type` has no entry in the dict items
- **THEN** the 类型 cell renders the raw numeric code — no blank cell

#### Scenario: Dict fetch failure degrades without blocking
- **WHEN** the dict lookup request fails
- **THEN** one error message is surfaced, the table still renders with raw
  codes, and the drawer's 类型 Select is empty but the CRUD round-trip still works

#### Scenario: Drawer offers dict categories
- **WHEN** the create drawer opens with the dict loaded
- **THEN** 类型 is a Select whose options are the dict items
  (label = value, value = code) with 系统角色 preselected (initialValue 10)
- **AND** submitting posts the selected numeric code unchanged

#### Scenario: One wire call per catalog per session
- **WHEN** the roles page re-lists, reopens a drawer, or the user navigates
  away and back within the session
- **THEN** the dict lookup resolves from the session cache without re-fetching

#### Scenario: Status switch reflects and toggles the enable flag
- **WHEN** the roles table renders with the 状态 column and a row carries
  `enable = 1` (or `0`)
- **THEN** the centered switch shows 启用 (on) or 停用 (off) with the
  aria-label 启用 {role name}
- **AND** toggling sends `PUT /api/v1/system/roles/{id}/enable` with
  `{"enable": 1|0}` and the list re-reads the persisted value

#### Scenario: Toggle failure reverts the switch
- **WHEN** the toggle call fails (backend rejection or network error)
- **THEN** the switch reverts to its previous visual state and the backend
  message is surfaced — no optimistic persistence

<!-- synced from: role-type-dict-web 2026-11-09 -->

<!-- synced from: role-list-api 2026-11-09 -->

<!-- synced from: menu-management-web 2026-11-06 - REPLACES the former read-only
     "Menu Tree Visibility" requirement (see archive/menu-management-web delta). -->

### Requirement: Menu Management

The console SHALL provide menu management backed by the menu family
(`GET /api/v1/system/menus/tree`, `POST /api/v1/system/menus`,
`PUT /api/v1/system/menus/{id}`, `DELETE /api/v1/system/menus/{id}`) so administrators
can shape the directory/menu/button tree and author button permission strings:

- The page SHALL render the full tree (标题/类型 tag/`perms`/排序/可见) with per-node
  actions 新增下级, 编辑, 删除, gated client-side by the `system:menu:list` permission.
- The create/edit dialog SHALL collect type (目录 M / 菜单 C / 按钮 F), parent node
  (offering no `F` nodes and, when editing, not the node's own subtree), 标题, `perms`
  (buttons; free text with the `模块:资源:动作` convention), `path`/`component`
  (directories/menus), `icon`, `sort`, and `visible`.
- Deleting a node SHALL require confirmation; backend rejection messages (e.g. live
  children) SHALL surface on the page.

#### Scenario: Tree renders with types and perms

- **WHEN** an administrator opens the menu page
- **THEN** the directory/menu/button hierarchy renders with each node's type tag and,
  where present, its `perms` string

#### Scenario: Button node CRUD round-trip

- **WHEN** an administrator creates a button node under a menu with a `perms` string,
  edits it, and deletes it
- **THEN** each operation calls the documented endpoint and the tree reflects the result

#### Scenario: Delete rejection surfaces

- **WHEN** an administrator deletes a node that still has live children
- **THEN** the backend's rejection message is shown and the node remains in the tree

#### Scenario: Dialog guards the parent choice

- **WHEN** the create/edit dialog renders its parent TreeSelect
- **THEN** button nodes are not selectable as parents
- **AND** when editing a node, the node itself and its descendants are not selectable

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Dictionary Management
The console SHALL provide dictionary catalog management
(`/api/v1/system/dicts/catal/**`: list, create, edit, delete with duplicate-`code` feedback) and
per-catalog detail management (`/api/v1/system/dicts/detail/**`: list ordered by `sort`, create,
edit, delete).

#### Scenario: Catalog list and duplicate feedback
- **WHEN** an administrator creates a catalog with an existing live `code`
- **THEN** the backend's duplicate message is shown
- **AND** fresh codes persist and appear in the list

#### Scenario: Details ordered by sort
- **WHEN** an administrator opens a catalog's details
- **THEN** details return ordered by `sort` and CRUD round-trips through the documented endpoints

<!-- synced from: web-foundation-auth-console 2026-11-05 -->
<!-- synced from: audit-log-unify-web 2026-11-10 (the merged audit page) -->
<!-- synced from: log-query-date-range 2026-11-12 (日期段 filter bar, uid/月份 removed) -->

### Requirement: Operation Log Query
The console SHALL provide a SINGLE paged audit-log view of `GET /api/v1/system/logs` at
the route `/system/logs`, reachable as the 审计日志 menu entry in the 安全 group
(supersedes the separate 日志查询 / 登录日志 entries), gated by `system:log:list`,
rows newest first, no write affordances (append-only data). The page SHALL show BOTH
event families in one table with: 日期, 操作人, 类别 (dict-driven label from the
「审计日志类型」 catalog, raw-code fallback), 标题, 描述, IP, 操作系统, 浏览器, and
the operation-only 方法/URL columns rendering — for sign rows. The filter bar SHALL
offer: 类别 (dict-driven Select whose options render the dict VALUES — never the raw
codes), 账号 (account), and 日期段 (an antd RangePicker with the presets 今日 / 近7天 /
近30天 / 本月); the former 操作人ID (uid) and 月份 (dateYm) filters are REMOVED from
the page. The page SHALL send `beginDate`/`endDate` (`yyyy-MM-dd`) only when both
bounds are picked; filters AND-combine server-side. The page SHALL NOT render any
`session` field (credential anchor, server-excluded).

#### Scenario: Filtered log query
- **WHEN** an administrator queries the audit page with any filter combination
  (类别, 账号, 日期段)
- **THEN** rows from both event families return newest-first with paging

#### Scenario: Date-range drives beginDate/endDate
- **WHEN** the administrator picks a range and clicks 查询
- **THEN** the query carries `beginDate` and `endDate` as `yyyy-MM-dd` strings and the
  list resets to page 1

#### Scenario: Presets populate both bounds
- **WHEN** the administrator picks the 今日 preset
- **THEN** both bounds fill with today's date (近7天/近30天/本月 populate their spans
  the same way)

#### Scenario: Clearing the range removes both params
- **WHEN** the administrator clears the 日期段 picker and queries
- **THEN** the query carries neither `beginDate` nor `endDate`

#### Scenario: Dict values render in the 类别 dropdown (guard)
- **WHEN** the 类别 Select opens with the loaded 「审计日志类型」 catalog
- **THEN** each option displays its dict value (新增 / 登录失败 / …), not the raw code

#### Scenario: Dict-driven category filter and labels
- **WHEN** the page loads the 「审计日志类型」 dict
- **THEN** the 类别 filter is a Select of the catalog's items and the 类别 column
  renders the matching label
- **AND** when the dict fetch fails or lacks an entry, the column degrades to the raw
  code and one error message surfaces — the page never blocks

#### Scenario: Sign rows show em-dash for operation-only columns
- **WHEN** the table renders a sign-family row (type 10/11/12)
- **THEN** the 方法/URL cells show — ; an operation row's cells show the values

#### Scenario: Read-only surface
- **WHEN** the audit page renders
- **THEN** it offers no mutation controls for log rows

#### Scenario: Menu-driven visibility follows the page permission
- **WHEN** a signed-in user's permission set lacks `system:log:list`
- **THEN** the 审计日志 menu entry is not visible to that user

### Requirement: Hospital Profile and Campus Table Management

The organization page SHALL render a hospital profile card on the left and a campus table on
the right. The hospital card SHALL show key profile fields and an edit action. The campus
table SHALL support keyword/status filtering and campus create/edit/default/delete actions.
The page SHALL NOT offer generic root creation, reparenting, or type changes during normal
operation. Mutation affordances SHALL follow `system:org:list`.

#### Scenario: Hospital and campuses render

- **WHEN** an administrator opens organization management
- **THEN** the left hospital profile card renders
- **AND** the right campus table renders one row per campus with code, default state, and
  status distinguishable

#### Scenario: Campus CRUD round-trip

- **WHEN** an administrator creates a campus, edits its profile, and deletes an empty campus
- **THEN** each operation calls the documented endpoint and the campus table reflects the
  result

#### Scenario: Root creation is not a normal action

- **WHEN** a hospital administrator opens the organization page
- **THEN** no generic new-root affordance is available
- **AND** creating another root through the API remains rejected by the backend

#### Scenario: Organization rejection surfaces

- **WHEN** the backend rejects a create, update, or delete because of controlled-tree rules
- **THEN** the page shows the backend message
- **AND** the hospital card and campus table are unchanged until a successful mutation

### Requirement: First-run Organization Profile Completion

The console SHALL support Option B initialization by rendering the seeded editable hospital
root and default campus. While the hospital root remains in its bootstrap state, the page
SHALL show a first-run completion hint. Administrators SHALL complete hospital details through
the existing hospital edit form; the page SHALL NOT offer root creation.

#### Scenario: First-run state renders

- **WHEN** an administrator opens organization management on a fresh deployment
- **THEN** the first-run hospital card and default campus row render
- **AND** a completion hint is shown without a generic root-creation control

#### Scenario: First-run profile is completed

- **WHEN** an administrator updates the hospital name, code, and profile through the edit form
- **THEN** the refreshed hospital card and campus table show the completed values
- **AND** the first-run hint is no longer shown

### Requirement: Campus Profile Management

The organization page SHALL collect and display hospital/campus profile fields:

- hospital and campus records: code, name, short name, status, province/city/district,
  address, contact, phone, email, logo URL, and sort;
- campus records additionally support marking one default campus per hospital.

Code SHALL be required for a new hospital or campus. Status SHALL default to enabled. Type
and parent SHALL not be edited in the form.

#### Scenario: Create campus profile

- **WHEN** an administrator submits a campus with code, name, region, address, contact, and
  status
- **THEN** the new campus appears in the campus table with those values

#### Scenario: Code is required

- **WHEN** an administrator submits a new campus without a code
- **THEN** the form blocks submission with a field-level required message

#### Scenario: Default campus remains unique

- **WHEN** an administrator marks a different campus as the default campus
- **THEN** the hospital has exactly one default campus after refresh

<!-- synced from: org-status-toggle-web 2026-11-06 -->

### Requirement: Organization Status Switch Uses Status Endpoint

The organization campus table SHALL render status as a switch consistent with user
management. Toggling the switch SHALL call `PUT /api/v1/system/orgs/{id}/status` with
`status: 0` or `status: 1`, then refresh the organization tree.

#### Scenario: Campus status toggle

- **WHEN** an administrator flips an enabled campus switch off
- **THEN** the client calls the dedicated status endpoint with `status: 0`
- **AND** the refreshed tree determines the switch state

### Requirement: Department Campus Filter Tree

The department page SHALL render a left hospital/campus tree as a filter. The tree SHALL
contain only the hospital and campus nodes, never departments. Selecting the hospital SHALL
mean "all campuses"; selecting a campus SHALL filter departments to that campus.

#### Scenario: Departments of a selected campus

- **WHEN** an administrator selects a campus in the filter tree
- **THEN** the department table queries and displays only that campus's departments

#### Scenario: Hospital selection means all campuses

- **WHEN** an administrator selects the hospital root in the filter tree
- **THEN** the department table queries all campuses
- **AND** cross-campus rows show their campus names

#### Scenario: Tree is not a department editor

- **WHEN** an administrator views the left hospital/campus tree
- **THEN** no department nodes are rendered in the tree
- **AND** department actions are available from the right table context rather than tree
  department nodes

### Requirement: Department Table Management

The department page SHALL render a right-side department table with server-side pagination,
keyword filtering, and status filtering. It SHALL allow department creation and editing
through a dialog that requires a live, enabled campus owner. Mutation affordances SHALL
follow `system:dep:list`.

#### Scenario: Department table is paginated

- **WHEN** many matching departments exceed the page size
- **THEN** the table shows only the requested page
- **AND** the pager reports the total matched department count

#### Scenario: Keyword and status filtering

- **WHEN** an administrator filters by keyword and status
- **THEN** the table queries matching live departments by code/name and status
- **AND** changing the filter resets to the first page

#### Scenario: Department create campus context

- **WHEN** an administrator creates a department from a campus-filtered view
- **THEN** the dialog prefills that campus and requires it to be a live, enabled campus
- **AND** when the hospital-root view is selected, the dialog requires an explicit campus

#### Scenario: Hospital cannot own a department

- **WHEN** the selected node is the hospital root
- **THEN** the create dialog does not treat the hospital as the owner
- **AND** it requires a campus before submission

### Requirement: Department Code Handling

Department creation SHALL include the campus context, and the department code SHALL be
immutable after creation. Same-campus duplicate-code rejection SHALL surface on the code
field. Campus reassignment MAY be allowed, but the target campus SHALL be validated and the
code SHALL remain unchanged.

#### Scenario: Same code across campuses

- **WHEN** campus A and campus B each contain a department with code `radiology`
- **THEN** selecting campus A shows only campus A's department
- **AND** selecting campus B shows only campus B's department

#### Scenario: Code cannot be edited after creation

- **WHEN** an administrator opens the edit dialog for an existing department
- **THEN** the code field is read-only
- **AND** submitting the edit does not change the stored code

#### Scenario: Duplicate code in one campus

- **WHEN** an administrator creates a second `radiology` in the same campus
- **THEN** the backend rejection message appears on the code field
- **AND** the dialog remains open

<!-- synced from: button-perm-enforcement-web 2026-11-06 -->

### Requirement: Button-Level Affordance Gating

Console management pages SHALL gate each action affordance by its own button-level
permission string (the coordination inventory): the action renders only when the
session permission set contains that string. The action column itself remains gated by
the page permission. Affordances sharing one endpoint share one perm string (新建机构
with 新建根机构; 新建根节点 with 新增下级). Disabled-until-selected affordances keep
their existing disabled logic and additionally require the perm. Read-only pages (log
queries) carry no gated affordances.

#### Scenario: Ungranted action stays hidden

- **WHEN** a user's session holds the 角色管理 page perm but not `system:role:delete`
- **THEN** the role list renders with 新建角色/编辑角色/菜单授权 (as granted) but no
  删除角色 affordance

#### Scenario: Granted action appears

- **WHEN** the session additionally contains `system:role:delete`
- **THEN** the 删除角色 affordance renders for role rows

#### Scenario: Page perm still gates the column

- **WHEN** a session holds button perms but somehow lacks the page perm
- **THEN** no action column renders (the page surface itself is route-gated by the
  page perm; the server remains authoritative either way)

<!-- synced from: workstation-context-web 2026-11-06 - W-node model (user ruling
     2026-11-06: workstation = menu tree node type W, granted through roles; the
     per-user assignment requirement drafted in this change was dropped before
     implementation - see archive/workstation-context-web) -->

### Requirement: Workstation Type in Menu Management

The menu management type option SHALL include 工作站 (type W) so administrators can
create workstation nodes as L1 entries; the type tags SHALL label W as 工作站. The
per-user workstation assignment affordance in 用户管理 SHALL be absent (workstation
access flows through roles — the roles-page 菜单授权 tree covers W nodes).

#### Scenario: Workstation nodes are menu content

- **WHEN** an administrator creates an L1 node with the 工作站 type
- **THEN** the node renders with the 工作站 type tag in the tree
- **AND** no workstation assignment affordance exists in 用户管理

<!-- synced from: org-logo-upload-web 2026-11-06 -->

### Requirement: Organization Logo Upload Control

The organization profile dialogs (hospital + campus) SHALL bind the logo field to an
image upload control backed by `POST /api/v1/system/common/upload/image` (api spec §
Common Image Upload Endpoint): an AntD picture-card Upload, one image, validated
client-side (png/jpg/jpeg/webp, ≤2MB) BEFORE any request, with the successful
response's `data.path` stored as the form's `logoUrl` value (the org-save contract is
unchanged — still the `logoUrl` field of the org create/update body). The control
SHOULD render the current value as the tile image; a legacy absolute `http(s)`
value displays as-is, a stored relative `/profile/...` value resolves through the
static read-back segment. The former `Logo 地址` URL text input SHALL NOT remain.

#### Scenario: Upload round-trip fills the form value

- **WHEN** an administrator picks a valid 1MB png in the logo tile and the upload
  succeeds
- **THEN** `uploadImage` was called exactly once and the form's `logoUrl` equals the
  returned `data.path`
- **AND** saving the dialog submits that path in the org body (existing endpoint)

#### Scenario: Client-side validation rejects before any request

- **WHEN** the picked file is a `.gif` (or >2MB)
- **THEN** the rejection message surfaces and no network request is made

#### Scenario: Existing logo renders as the tile image

- **WHEN** the dialog opens for an org whose `logoUrl` is `/profile/avatar/.../x.png`
- **THEN** the tile shows that image (resolved same-origin via the static segment)
- **AND** an absolute `https://...` legacy value still renders as-is

#### Scenario: Upload failure keeps the dialog usable

- **WHEN** the upload request fails (backend `A0400` or transport `B0001`)
- **THEN** the error message surfaces on the page
- **AND** the previously stored `logoUrl` value is unchanged and the dialog stays
  open for retry

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

### Requirement: Exam Item Page

The console SHALL render the 检查项目 page (menu component `setting/exam-item/index`)
against the real `/api/v1/setting/exam-items` endpoints: a filter bar (检查类型 select
from dict 1021, 部位分组 select from `exam-body-groups/list`, 常用项目 toggle, keyword
search over 名称/编码), a table (项目名称 / 项目编码 / 检查部位 / 检查方法 / 单价 /
折扣 / 诊断计费数量 / 诊断得分 / 审核得分 / 创建时间), row 编辑/删除 affordances gated
by `setting:item:*`, and a Drawer form with 检查类型 readout, 项目编码, 项目名称,
部位分组 → 检查部位 cascade (group select drives the body tree select), 检查方法
multi-select, 排序, 单价, 折扣, 诊断计费数量, 诊断得分, 审核得分, 常用项目 switch.

#### Scenario: Group selection cascades the body options

- **WHEN** the drawer's 部位分组 select changes
- **THEN** the 检查部位 tree select reloads with that group's bodies and the stale
  body value clears

#### Scenario: Create posts to the real backend

- **WHEN** the user saves a valid new item
- **THEN** the page POSTs to `/api/v1/setting/exam-items` and refreshes the table from
  the response of a fresh page query — no mock data anywhere in the flow

### Requirement: Exam Body Page with In-Page Group Management

The console SHALL render the 检查部位 page (component `setting/exam-body/index`): a
left column managing 部位分组 for the selected 检查类型 (list + 新建/编辑/删除 gated
by `setting:body:*`), and a right table of bodies (部位名称 / 部位编码 / 上级部位 /
排序 / 创建时间) filtered by group, with a Drawer form (检查类型 readout, 上级部位
optional tree select within the group, 部位名称, 部位编码, 排序). Group deletion that
the backend refuses (bodies present) surfaces the backend's blocking message.

#### Scenario: Left column manages groups without leaving the page

- **WHEN** the user creates a group in the left column
- **THEN** the group list and the page's group filter refresh in place

#### Scenario: Blocked group delete shows the reason

- **WHEN** a group delete is refused by the backend because bodies exist
- **THEN** the page shows the backend's refusal message and keeps the group

### Requirement: Exam Method Page

The console SHALL render the 检查方法 page (component `setting/exam-method/index`):
检查类型 filter, 名称/编码 keyword search, table (方法名称 / 方法编码 / 检查类型 /
创建时间), Drawer form (检查类型 readout, 方法名称, 方法编码, 排序), affordances gated
by `setting:method:*`.

#### Scenario: Method CRUD round-trips the real API

- **WHEN** the user creates, edits, then deletes a method
- **THEN** each action hits `/api/v1/setting/exam-methods` and the table reflects the
  server state after each action

### Requirement: Report Template Page

The console SHALL render the 报告模板 page (component `setting/rep-template/index`)
with a 公共/私有 tab toggle switching the scope `type` (1/2): a left pane with 检查类
型 select (dict 1021) and the folder/template tree (node-level 新建子级/编辑/删除),
and a right pane with the template table (模板名称 / 影像学表现 / 影像学意见) paged
under the selected node plus name search and a Drawer form (名称, 上级 folder tree
select, 目录/模板 flag — flag=1 reveals 影像学表现/影像学意见 textareas). Private-tab
writes use the real owner binding; the page never offers another user's private rows.

#### Scenario: Tab toggle switches scope without cross-leak

- **WHEN** the user switches from 私有 to 公共 tab
- **THEN** tree and table reload with type=1 data, and switching back reloads the
  user's own type=2 tree

#### Scenario: Directory node creates a child template

- **WHEN** the user uses a tree node's 新建 action with flag=模板
- **THEN** the drawer opens pre-bound to that node as 上级 and saving shows the new
  template in the right table under that node
