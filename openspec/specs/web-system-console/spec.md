# web-system-console Specification

## Purpose

Defines the system administration console: pages that manage the entities lumen-system serves -
org tree, departments, users, roles and their menu grants, the menu tree, dictionaries, and
operation logs. Every page talks to the endpoints the `api` capability documents (mocked in
development until backend wiring) and follows the OPS console pattern: a left organization-tree
aside plus a right work area where applicable.

## Requirements

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: User Management
The console SHALL provide a user list view (`GET /api/v1/system/users/list`, which returns
a plain list - the view paginates client-side) with create
(`POST /api/v1/system/users`), password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form.

#### Scenario: Paged user list
- **WHEN** an administrator opens the user page
- **THEN** the user list renders and re-queries on filter/page change

#### Scenario: Duplicate username feedback
- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Password and enable actions
- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Role Management and Menu Assignment
The console SHALL provide role management backed by `GET /api/v1/system/roles/list` (mocked
until the backend implements it), create (`POST /api/v1/system/roles`), edit
(`PUT /api/v1/system/roles/{id}`), and delete (`DELETE /api/v1/system/roles/{id}`). For each role,
menus SHALL be assignable via a tree with checkboxes that full-replaces the grant through
`PUT /api/v1/system/roles/{id}/menus`.

#### Scenario: Role CRUD round-trip
- **WHEN** an administrator creates, renames, and deletes a role
- **THEN** the documented endpoints are called and the list reflects the result

#### Scenario: Menu assignment is full-replace
- **WHEN** an administrator checks a set of menus for a role and saves
- **THEN** the checked set is sent as the role's complete grant
- **AND** reopening the dialog shows exactly that set

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
The console SHALL provide a paged operation-log view of `GET /api/v1/system/logs` with the
documented filters (`type`, `uid`, `dateYm`), newest first, and no write affordances
(append-only data).

#### Scenario: Filtered log query
- **WHEN** an administrator queries logs with a type or month filter
- **THEN** matching rows return newest-first and paging works

#### Scenario: Read-only surface
- **WHEN** the log page renders
- **THEN** it offers no mutation controls for log rows

### Requirement: Sign-in Log Query
The console SHALL provide a paged sign-in log view of `GET /api/v1/system/logs/sign` at the
route `/system/logs/sign`, reachable as the 登录日志 menu entry in the 安全 group (sibling
of 日志查询), with the documented filters (`account`, `uid`, `dateYm`), newest first, and
no write affordances (append-only data). The page SHALL render the audit columns: 账号,
事件, 描述, IP, 操作系统, 浏览器, 操作时间.

#### Scenario: Filtered sign-log query
- **WHEN** an administrator queries the sign-in log page with an account or month filter
- **THEN** matching rows return newest-first and paging works

#### Scenario: Read-only surface
- **WHEN** the sign-in log page renders
- **THEN** it offers no mutation controls for log rows

#### Scenario: Menu-driven visibility follows the page permission
- **WHEN** a signed-in user's permission set lacks `system:signlog:list`
- **THEN** the 登录日志 menu entry is not visible to that user

<!-- synced from: sign-log-query 2026-11-05 -->

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
