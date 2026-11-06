# web-system-console Specification

## Purpose

Defines the system administration console: pages that manage the entities lumen-system serves -
org tree, departments, users, roles and their menu grants, the menu tree, dictionaries, and
operation logs. Every page talks to the endpoints the `api` capability documents (mocked in
development until backend wiring) and follows the OPS console pattern: a left organization-tree
aside plus a right work area where applicable.

## Requirements

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Org Tree Management
The console SHALL provide an org page that renders the organization hierarchy from
`GET /api/v1/system/orgs/tree`, supports creating a child org on any node, editing and
deleting an org, and reflects multi-root capability (a second root can be created and shown
alongside the first).

#### Scenario: Tree renders with seeded root
- **WHEN** an administrator opens the org page
- **THEN** the hierarchy returns with the seeded hospital root visible

#### Scenario: Org CRUD round-trip
- **WHEN** an administrator creates, edits, and deletes an org
- **THEN** each operation calls the documented endpoint and the tree reflects the result


<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Department Management
The console SHALL provide department management anchored to the org tree: departments are
listed for a selected org (`GET /api/v1/system/org-deps/list`), and can be created, edited, and
deleted. The page SHALL present the org tree as the selection context (left aside, right list
pattern).

#### Scenario: Departments of a selected org
- **WHEN** an administrator selects an org node
- **THEN** only that org's departments are listed

#### Scenario: Department CRUD round-trip
- **WHEN** an administrator creates, edits, and deletes a department under an org
- **THEN** the documented endpoints are called and the list reflects the result


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
