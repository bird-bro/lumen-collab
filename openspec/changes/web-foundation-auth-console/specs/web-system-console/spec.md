# Spec Delta

## Purpose

Defines the system administration console: pages that manage the entities lumen-system serves -
org tree, departments, users, roles and their menu grants, the menu tree, dictionaries, and
operation logs. Every page talks to the endpoints the `api` capability documents (mocked in
development until backend wiring) and follows the OPS console pattern: a left organization-tree
aside plus a right work area where applicable.

## ADDED Requirements

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

### Requirement: User Management
The console SHALL provide a paged user list (`GET /api/v1/system/users/list`) with create
(`POST /api/v1/system/users`), password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form.

#### Scenario: Paged user list
- **WHEN** an administrator opens the user page
- **THEN** users return paged and the list re-queries on filter/page change

#### Scenario: Duplicate username feedback
- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Password and enable actions
- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

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

### Requirement: Menu Tree Visibility
The console SHALL provide a read-only menu tree view of `GET /api/v1/system/menus/tree`
(directory/menu/button structure with `perms` strings) so administrators can see the
permission model that roles and dynamic navigation draw from.

#### Scenario: Menu tree renders
- **WHEN** an administrator opens the menu page
- **THEN** the directory/menu/button hierarchy renders with each node's `perms` string visible

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
