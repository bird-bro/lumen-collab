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

The console SHALL provide a user list view (`GET /api/v1/system/users/list`, which returns
a plain list - the view paginates client-side) with create
(`POST /api/v1/system/users`), password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form. The create request's `password` and the
reset request's `newPassword` SHALL be SM2-encrypted with the session-cached public key from
`GET /api/v1/system/auth-key` (C1C3C2 hex — coordination password-transport-encryption D4/D6)
inside the API-client boundary (`src/api/users.ts` keeps raw-password signatures; the typed
value never reaches the wire as plaintext). A backend `A0400` rejection SHALL surface
through the same message path as other `A`-class failures on the form.

#### Scenario: Paged user list

- **WHEN** an administrator opens the user page
- **THEN** the user list renders and re-queries on filter/page change

#### Scenario: Duplicate username feedback

- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Password and enable actions

- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

#### Scenario: Encrypted password fields on create and reset

- **WHEN** the create dialog submits or the reset action confirms
- **THEN** the captured request body's `password` / `newPassword` value is a hex SM2
  ciphertext that differs from the typed value
- **AND** the backend accepts it (decrypt-then-BCrypt), so the operation succeeds exactly
  as the plaintext flow did before password-transport-encryption

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

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

### Requirement: Operation Log Query
<!-- synced from: audit-log-unify-web 2026-11-10 (the merged audit page; the former
     Sign-in Log Query requirement is retired with the /sign endpoint and menu 17) -->
The console SHALL provide a SINGLE paged audit-log view of `GET /api/v1/system/logs` at
the route `/system/logs`, reachable as the 审计日志 menu entry in the 安全 group
(supersedes the separate 日志查询 / 登录日志 entries), gated by `system:log:list`,
rows newest first, no write affordances (append-only data). The page SHALL show BOTH
event families in one table with: 时间, 类别 (dict-driven label from the
「审计日志类型」 catalog, raw-code fallback), 标题 (title; sign rows carry
Sign-IN/Sign-OUT), 描述, 操作人, IP, 操作系统, 浏览器, and the operation-only
方法/URL columns rendering — for sign rows. The filter bar SHALL offer: 类别
(dict-driven Select of the audit type codes), 操作人ID (uid), 账号 (account), and
月份 (dateYm); filters AND-combine server-side. The page SHALL NOT render any
`session` field (credential anchor, server-excluded).

#### Scenario: Filtered log query
- **WHEN** an administrator queries the audit page with any filter combination
- **THEN** rows from both event families return newest-first with paging

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
