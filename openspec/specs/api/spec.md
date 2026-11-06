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


<!-- synced from: org-management-redesign-api 2026-11-06 (replaces "Org Tree Endpoints") -->

### Requirement: Controlled Organization Management Endpoints

`lumen-system` SHALL expose organization management under `/api/v1/system/orgs` for the
controlled hospital/campus model. `GET /tree` SHALL return the hospital root and campuses
with profile fields. `POST` SHALL create only a bootstrap hospital root or a campus under
that hospital. `PUT` SHALL update profile fields and status only. `DELETE` SHALL be guarded
by live dependents. The family remains gated by `system:org:list`.

#### Scenario: Controlled tree query

- **WHEN** an authenticated client requests the organization tree
- **THEN** it returns the single live hospital root and its campus children with profile
  fields

#### Scenario: Campus creation

- **WHEN** an authorized client posts a campus record under the live hospital
- **THEN** the endpoint returns the new campus id
- **AND** the tree contains that campus under the hospital

#### Scenario: Second hospital root is rejected

- **WHEN** a client posts another `upId = 0` hospital while a live root exists
- **THEN** the response is an A-class business error with a descriptive message
- **AND** no organization row is created

#### Scenario: Campus cannot contain a child organization

- **WHEN** a client posts an organization whose parent is a campus
- **THEN** the response is an A-class business error
- **AND** no organization row is created

#### Scenario: Type and parent are immutable

- **WHEN** a client updates an organization with a different type or a different parent id
- **THEN** the response is an A-class business error
- **AND** the stored type and parent remain unchanged

#### Scenario: Organization delete guard

- **WHEN** a client deletes an organization that has a live child organization or live
  department
- **THEN** the response is an A-class business error
- **AND** the organization and its dependents remain live

<!-- synced from: org-management-redesign-api 2026-11-06 -->

### Requirement: Existing Department List Compatibility

`lumen-system` SHALL continue to expose `GET /api/v1/system/org-deps/list`. An absent
`orgId` SHALL return all live departments. A present `orgId` SHALL filter departments by
the owning organization. The endpoint SHALL remain gated by `system:dep:list`.

#### Scenario: Existing list without a campus filter

- **WHEN** an authenticated client requests the existing department list without `orgId`
- **THEN** all live departments return in `data`

#### Scenario: Existing list with a campus filter

- **WHEN** an authenticated client requests the existing department list with a campus
  `orgId`
- **THEN** only that campus's departments return in `data`

<!-- synced from: org-management-redesign-api 2026-11-06 (replaces "Department Endpoints") -->

### Requirement: Department Table Query

`lumen-system` SHALL expose `GET /api/v1/system/org-deps/page` for the department table.
The query SHALL support optional `orgId`, `keyword`, `status`, `pageNum`, and `pageSize`.
An absent `orgId` SHALL mean all campuses; a present `orgId` SHALL be a campus. Results
SHALL be paginated and deterministically ordered. The endpoint SHALL remain gated by
`system:dep:list`.

#### Scenario: Department table query returns all campuses

- **WHEN** the department page query is requested without `orgId`
- **THEN** departments from every campus are returned in the page result
- **AND** the total count covers all matching campus rows

#### Scenario: Department table query filters a campus

- **WHEN** the department page query is requested with a campus `orgId`
- **THEN** only that campus's matching departments are returned
- **AND** the result is ordered deterministically

#### Scenario: Department table query filters keyword and status

- **WHEN** the department page query is requested with a keyword and a status
- **THEN** only live departments whose code or name matches the keyword and whose status
  matches the requested value are returned

<!-- synced from: org-management-redesign-api 2026-11-06 -->

### Requirement: Campus-Scoped Department Mutations

`POST /api/v1/system/org-deps` and `PUT /api/v1/system/org-deps/{id}` SHALL require a live,
enabled campus owner. A hospital root or disabled campus SHALL NOT own a department. A
department code SHALL be immutable after creation and unique among live departments within
the same campus but MAY repeat across campuses.

#### Scenario: Department belongs to a campus

- **WHEN** an authorized client creates a department for a live, enabled campus
- **THEN** it returns the new department id
- **AND** the department list for that campus contains the department

#### Scenario: Hospital owner is rejected

- **WHEN** a client creates or updates a department whose owning org is the hospital root
- **THEN** the response is an A-class business error
- **AND** no department ownership change is persisted

#### Scenario: Disabled campus cannot receive a department

- **WHEN** a client creates or updates a department whose owning org is a disabled campus
- **THEN** the response is an A-class business error
- **AND** no department ownership change is persisted

#### Scenario: Department code is immutable

- **WHEN** a client updates a department with a code different from the stored code
- **THEN** the response is an A-class business error
- **AND** the stored code remains unchanged

#### Scenario: Duplicate code within a campus

- **WHEN** a client creates or updates a department with a code that already exists live in
  the same campus
- **THEN** the response is an A-class duplicate-code error

#### Scenario: Same code across campuses

- **WHEN** campuses A and B each have a department with code `radiology`
- **THEN** both departments persist
- **AND** querying each campus returns only its own department


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


<!-- synced from: menu-management-api 2026-11-06 -->

### Requirement: Menu Management Endpoints

`lumen-system` SHALL expose a menu administration family under `/api/v1/system/menus/**`,
gated by the same class-level `system:menu:list` authority as the Menu Tree Endpoint:

- `POST /api/v1/system/menus` with body `{ upId, type, title, perms, path, component,
  icon, sort, visible }` SHALL create a node (`upId = 0` = top level; `type` = `M`/`C`/`F`;
  `title` required; `perms`/`path`/`component`/`icon` optional free text; `visible` 1/0)
  and return the new node id. Creation SHALL also grant the new node to the admin role.
- `PUT /api/v1/system/menus/{id}` with the same body SHALL update the node's fields,
  including reparenting via `upId`, and return success.
- `DELETE /api/v1/system/menus/{id}` SHALL soft-delete the node and return success.

Structural rejections (an `A`-class code with a descriptive `msg`):

- a child created or moved under a `type = F` parent SHALL be rejected (buttons are leaves);
- `upId` set to the node itself or to any node inside the node's own descendant subtree
  SHALL be rejected (cycle protection);
- a parent `upId` that does not reference a live node SHALL be rejected.

`perms` strings SHALL be stored verbatim (free text, no format validation or uniqueness
constraint). No other menu-model field semantics change; the Menu Tree Endpoint and the
login/me menu payloads are unchanged.

#### Scenario: Create a button node under a menu

- **WHEN** an authorized client posts a node with `type = "F"`, `title = "删除"`,
  `perms = "system:role:delete"`, under a live `C` parent
- **THEN** the response returns the new node id and the tree endpoint shows the button
  with its `perms` string
- **AND** the admin role's next login/me permission set contains `system:role:delete`

#### Scenario: Create rejects a button parent

- **WHEN** a client posts a node whose `upId` references a `type = "F"` node
- **THEN** the response is an `A`-class business error with a descriptive `msg`
- **AND** no node is created

#### Scenario: Move rejects a cycle

- **WHEN** a client puts a node with `upId` pointing at one of its own descendants
- **THEN** the response is an `A`-class business error and the tree is unchanged

#### Scenario: Delete cleans role bindings

- **WHEN** a client deletes a leaf menu node that is bound to roles via `role_menu`
- **THEN** the node soft-deletes
- **AND** its `role_menu` bindings no longer grant the perms string to any role's
  login/me permission set

#### Scenario: Delete rejects a parent node

- **WHEN** a client deletes a menu node that still has live children
- **THEN** the response is an `A`-class business error with a descriptive `msg`
- **AND** the node and its children remain live

