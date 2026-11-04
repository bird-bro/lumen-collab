# Spec Delta

## ADDED Requirements

### Requirement: User Management Endpoints
`lumen-system` SHALL expose user management under `/api/v1/system/users`: `POST /api/v1/system/users`
creates a user (duplicate live username rejected with an `A`-class code),
`GET /api/v1/system/users/list` returns the paged user list,
`PUT /api/v1/system/users/{id}/password` resets a user's password, and
`PUT /api/v1/system/users/{id}/enable` toggles the enable flag. All respond with the standard
envelope. (Implemented surface - documentation sync of shipped `lumen-system` code.)

#### Scenario: Paged user list
- **WHEN** an authenticated client requests `GET /api/v1/system/users/list`
- **THEN** it returns `code: "200"` with the paged user payload in `data`

#### Scenario: Create user
- **WHEN** an authenticated client posts a fresh username to `POST /api/v1/system/users`
- **THEN** it returns `code: "200"` with the new user id in `data`

#### Scenario: Duplicate username
- **WHEN** the posted username already exists live
- **THEN** the envelope carries the duplicate-username `A`-class code and `data` is null

#### Scenario: Password reset and enable toggle
- **WHEN** an authenticated client calls the password-reset or enable endpoint for a user id
- **THEN** it returns `code: "200"` and the mutation persists

### Requirement: Org Tree Endpoints
`lumen-system` SHALL expose the org hierarchy under `/api/v1/system/orgs`:
`GET /api/v1/system/orgs/tree` (parent-child nesting from `upId`, seeded hospital root included),
`POST /api/v1/system/orgs`, `PUT /api/v1/system/orgs/{id}`, and `DELETE /api/v1/system/orgs/{id}`.
All respond with the standard envelope. (Implemented surface - documentation sync.)

#### Scenario: Org tree query
- **WHEN** an authenticated client requests `GET /api/v1/system/orgs/tree`
- **THEN** it returns `code: "200"` with the nested tree, seeded hospital root included

#### Scenario: Org mutation verbs
- **WHEN** an authenticated client creates, updates, or deletes an org through the documented
  verbs
- **THEN** the envelope returns `code: "200"` and the tree reflects the change

### Requirement: Department Endpoints
`lumen-system` SHALL expose departments under `/api/v1/system/org-deps`:
`GET /api/v1/system/org-deps/list` (by org), `POST /api/v1/system/org-deps`,
`PUT /api/v1/system/org-deps/{id}`, and `DELETE /api/v1/system/org-deps/{id}`, all with the
standard envelope. (Implemented surface - documentation sync.)

#### Scenario: Org tree query
- **WHEN** an authenticated client requests `GET /api/v1/system/orgs/tree`
- **THEN** it returns `code: "200"` with the nested tree, seeded hospital root included

#### Scenario: Org mutation verbs
- **WHEN** an authenticated client creates, updates, or deletes an org through the documented
  verbs
- **THEN** the envelope returns `code: "200"` and the tree reflects the change

#### Scenario: Department list by org
- **WHEN** an authenticated client requests `GET /api/v1/system/org-deps/list` for an org
- **THEN** only that org's departments return in `data`

### Requirement: Role Management Endpoints
`lumen-system` SHALL expose role management under `/api/v1/system/roles`: create, update, delete,
the FULL-REPLACE menu grant (`PUT /{id}/menus`), and the paged list `GET /api/v1/system/roles/list`.
The list endpoint is **NOT yet implemented** (gap surfaced 2026-11-03 by the web console need):
the frontend mocks it per this contract, and a backend follow-up change implements it to this
scenario. (The other verbs are the implemented surface - documentation sync.)

#### Scenario: Duplicate role name rejected
- **WHEN** a role is created with an existing live name
- **THEN** the envelope carries the duplicate-name `A`-class code and `data` is null

#### Scenario: Delete cascades unbind
- **WHEN** a role is deleted
- **THEN** its `user_role` and `role_menu` rows are unbound

#### Scenario: Role-menu full-replace assignment
- **WHEN** an authenticated client puts a menu-id set to `PUT /api/v1/system/roles/{id}/menus`
- **THEN** it returns `code: "200"` and the posted set is the role's complete grant afterwards

#### Scenario: Role list (gap to implement)
- **WHEN** an authenticated client requests `GET /api/v1/system/roles/list`
- **THEN** it returns `code: "200"` with the paged role payload in `data`
- **AND** until the backend implements it, the dev mock serves this contract

### Requirement: Menu Tree Endpoint
`lumen-system` SHALL expose `GET /api/v1/system/menus/tree` returning the directory/menu/button
hierarchy with each node's `perms` strings. (Implemented surface - documentation sync.)

#### Scenario: Menu tree query
- **WHEN** an authenticated client requests `GET /api/v1/system/menus/tree`
- **THEN** it returns `code: "200"` with the directory/menu/button hierarchy and `perms` strings
