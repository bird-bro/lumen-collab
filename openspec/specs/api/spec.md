# API Contract

## Purpose
Authoritative API contract. Frontend mocks from this; backend implements to this. Defines base URL, response envelope, and endpoint conventions.

## Requirements

### Requirement: API Response Envelope
All API responses MUST use the standard envelope of the OPS `R` contract:
`{ "timestamp": "...", "code": "...", "msg": "...", "data": {} }`, where `timestamp` is the
server time formatted `yyyy-MM-dd hh:mm:ss.SSS`, `code` is a string, and `msg` is a
human-readable description. Base URL: `/api/v1`.

#### Scenario: Successful response
- **WHEN** a request succeeds and has content to return
- **THEN** `code` is `"200"` and the typed payload is in `data`

#### Scenario: Successful response without payload
- **WHEN** a request succeeds but has no content to return
- **THEN** `code` is `"204"` and `data` is null

#### Scenario: Error response
- **WHEN** a request fails
- **THEN** `code` carries the error code string defined by the Error Code Scheme and `msg`
  describes the failure
- **AND** `data` is null


<!-- synced from: auth-rbac-foundation 2026-11-03 -->
### Requirement: Endpoint Documentation
Each endpoint MUST be documented as a Requirement with WHEN/THEN scenarios. Add new
Requirements for each resource. All endpoints live under `/api/v1/<service>/**` and respond
with the standard envelope (string `code`).

#### Scenario: Resource retrieval
- **WHEN** a client requests `GET /api/v1/[resource]`
- **THEN** returns `code: "200"` with the typed payload in `data`

### Requirement: Gateway Path Convention
All endpoints MUST be exposed through the platform's unified API entry (nginx in the
hospital form; Spring Cloud Gateway optional in the cloud form) under
`/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`,
`/api/v1/radiology/exam-orders`). Individual services MUST NOT be called directly by the
frontend.

#### Scenario: Service routing
- **WHEN** the unified-entry routing contract is inspected (deployment config arrives with
  the deferred `docker/` change; the frontend arrives with `init-web-skeleton`)
- **THEN** every documented endpoint path lives under `/api/v1/<service>/**`
- **AND** no contract, code, or config routes the frontend directly to a backend service
  port

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
### Requirement: ID Serialization
Entity IDs SHALL be Integer auto-increment (user decision 2026-11-02, OPS/随访 lineage; the
former snowflake-bigint clause is withdrawn together with the data capability's delta in
this change). IDs SHALL serialize as JSON numbers.

#### Scenario: ID in JSON payload
- **WHEN** a response contains an entity ID (e.g. the seeded admin's `uid`)
- **THEN** the JSON value is the Integer number `1`, never a string

<!-- synced from: rename-auth-to-system 2026-11-04 -->
### Requirement: Authentication Endpoints
`lumen-system` SHALL document and expose the auth endpoints as the platform's first concrete
endpoints: `POST /api/v1/system/login` (credentials → JWT + profile) and
`GET /api/v1/system/me` (valid token → profile + permission set). Both respond with the
standard envelope and the error-code scheme of `openspec/specs/errors/spec.md`.

#### Scenario: Login endpoint documented and reachable
- **WHEN** a client posts valid credentials to `POST /api/v1/system/login`
- **THEN** it returns `code: "200"` with the JWT and profile
- **AND** this endpoint requires no token itself

#### Scenario: Current-user endpoint
- **WHEN** a client requests `GET /api/v1/system/me` with a valid token
- **THEN** it returns `code: "200"` with the profile and permission set (menus + perms
  strings) of the authenticated user

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: User Management Endpoints
`lumen-system` SHALL expose user management under `/api/v1/system/users`: `POST /api/v1/system/users`
creates a user (duplicate live username rejected with an `A`-class code),
`GET /api/v1/system/users/list` returns the user list (implemented surface: a plain
`List`, no page wrapper),
`PUT /api/v1/system/users/{id}/password` resets a user's password, and
`PUT /api/v1/system/users/{id}/enable` toggles the enable flag. All respond with the standard
envelope. (Implemented surface - documentation sync of shipped `lumen-system` code.)

#### Scenario: Paged user list
- **WHEN** an authenticated client requests `GET /api/v1/system/users/list`
- **THEN** it returns `code: "200"` with the user payload in `data` (plain list, per the
  implemented surface - a later backend change may add a page wrapper)

#### Scenario: Create user
- **WHEN** an authenticated client posts a fresh username to `POST /api/v1/system/users`
- **THEN** it returns `code: "200"` with the new user id in `data`

#### Scenario: Duplicate username
- **WHEN** the posted username already exists live
- **THEN** the envelope carries the duplicate-username `A`-class code and `data` is null

#### Scenario: Password reset and enable toggle
- **WHEN** an authenticated client calls the password-reset or enable endpoint for a user id
- **THEN** it returns `code: "200"` and the mutation persists


<!-- synced from: web-foundation-auth-console 2026-11-05 -->

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


<!-- synced from: web-foundation-auth-console 2026-11-05 -->

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


<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Role Management Endpoints
`lumen-system` SHALL expose role management under `/api/v1/system/roles`: create, update, delete,
the FULL-REPLACE menu grant (`PUT /{id}/menus`), and the list `GET /api/v1/system/roles/list`.
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
- **THEN** it returns `code: "200"` with the role payload in `data`
- **AND** until the backend implements it, the dev mock serves this contract


<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Menu Tree Endpoint
`lumen-system` SHALL expose `GET /api/v1/system/menus/tree` returning the directory/menu/button
hierarchy with each node's `perms` strings. (Implemented surface - documentation sync.)

#### Scenario: Menu tree query
- **WHEN** an authenticated client requests `GET /api/v1/system/menus/tree`
- **THEN** it returns `code: "200"` with the directory/menu/button hierarchy and `perms` strings

