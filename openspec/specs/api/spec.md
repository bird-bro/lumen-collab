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

<!-- synced from: org-logo-upload-api 2026-11-06 (adds the /profile/** static read-back segment) -->
### Requirement: Gateway Path Convention

All endpoints MUST be exposed through the platform's unified API entry (nginx in the
hospital form; Spring Cloud Gateway optional in the cloud form) under
`/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`,
`/api/v1/radiology/exam-orders`). Individual services MUST NOT be called directly by
the frontend. Uploaded static files are the ONE sanctioned segment outside
`/api/v1`: the `/profile/**` tree is served by the owning service (v1: lumen-system)
as anonymous GET read-back for uploaded files, and both the unified entry and the dev
proxy MUST route it to the same owning service (coordination: org-logo-upload D7).

#### Scenario: Service routing

- **WHEN** the unified-entry routing contract is inspected (deployment config arrives
  with the deferred `docker/` change; the frontend arrives with `init-web-skeleton`)
- **THEN** every documented endpoint path lives under `/api/v1/<service>/**`
- **AND** no contract, code, or config routes the frontend directly to a backend
  service port

#### Scenario: Static read-back segment

- **WHEN** a client GETs a stored upload path such as `/profile/avatar/2026/11/06/uuid.png`
- **THEN** the owning service (v1: lumen-system) serves the file bytes anonymously —
  no Authorization header, no envelope, direct `image/*` response
- **AND** writes under `/profile/**` are not possible (read-only static mapping)

### Requirement: ID Serialization
Entity IDs SHALL be Integer auto-increment (user decision 2026-11-02, OPS/随访 lineage; the
former snowflake-bigint clause is withdrawn together with the data capability's delta in
this change). IDs SHALL serialize as JSON numbers.

#### Scenario: ID in JSON payload
- **WHEN** a response contains an entity ID (e.g. the seeded admin's `uid`)
- **THEN** the JSON value is the Integer number `1`, never a string

<!-- synced from: password-transport-encryption-api 2026-11-09 -->

### Requirement: Authentication Endpoints

`lumen-system` SHALL document and expose the auth endpoints as the platform's first concrete
endpoints: `POST /api/v1/system/login` (credentials → JWT + profile) and
`GET /api/v1/system/me` (valid token → profile + permission set). Both respond with the
standard envelope and the error-code scheme of `openspec/specs/errors/spec.md`.
`lumen-system` SHALL additionally expose `GET /api/v1/system/auth-key` as the ANONYMOUS
password-crypto key distribution endpoint: it responds with the standard envelope whose
`data` is `{ "keyId": "<configured key-id>", "publicKey": "<130-char hex uncompressed SM2
point 04||x||y>" }`, the public key derived server-side from the configured private scalar
D (never separately configured; coordination password-transport-encryption D2/D3). The
login request's `password` field SHALL carry SM2 ciphertext — C1C3C2 mode, hex encoding —
instead of plaintext; the server decrypts it before the BCrypt match (auth-rbac capability).
A request whose password field is not decryptable ciphertext SHALL be rejected with
envelope code `"A0400"` (用户请求参数错误, the copied `ExceptionEnum` constant reused
verbatim) and a descriptive `msg` (coordination D5).

#### Scenario: Login endpoint documented and reachable

- **WHEN** a client posts valid credentials — SM2-encrypted password ciphertext — to
  `POST /api/v1/system/login`
- **THEN** it returns `code: "200"` with the JWT and profile
- **AND** this endpoint requires no token itself

#### Scenario: Current-user endpoint

- **WHEN** a client requests `GET /api/v1/system/me` with a valid token
- **THEN** it returns `code: "200"` with the profile and permission set (menus + perms
  strings) of the authenticated user

#### Scenario: Auth-key endpoint is anonymous

- **WHEN** a client requests `GET /api/v1/system/auth-key` without any token
- **THEN** the envelope answers `code: "200"` with `data.keyId` and `data.publicKey`
- **AND** the publicKey is the 130-char hex uncompressed point (starts with `04`)

#### Scenario: Login password field carries ciphertext

- **WHEN** the login request body is inspected
- **THEN** the `password` value is a hex SM2 ciphertext (C1C3C2), never the raw password
- **AND** a plaintext password value is rejected with `code: "A0400"`, `data` null

<!-- synced from: password-transport-encryption-api 2026-11-09 -->

### Requirement: User Management Endpoints
`lumen-system` SHALL expose user management under `/api/v1/system/users`: `POST /api/v1/system/users`
creates a user (duplicate live username rejected with an `A`-class code),
`GET /api/v1/system/users/list` returns the user list (implemented surface: a plain
`List`, no page wrapper),
`GET /api/v1/system/users/{id}` returns one user's full profile detail,
`PUT /api/v1/system/users/{id}` partial-updates the profile (name, phone, email, gender,
title, depId, roleIds, modalitys, repAudit, signature — `username` and `password` are NOT
updatable through it),
`DELETE /api/v1/system/users/{id}` logically deletes the user and unbinds its
`user_role` rows, plus the existing `PUT /api/v1/system/users/{id}/password` reset and
`PUT /api/v1/system/users/{id}/enable` toggle. All respond with the standard envelope.
The create request's `password` field and the reset request's `newPassword` field
SHALL carry SM2 ciphertext (C1C3C2, hex) instead of plaintext, decrypted server-side
before BCrypt encoding; values that are not decryptable ciphertext SHALL be rejected with
envelope code `"A0400"` and a descriptive `msg` (coordination password-transport-encryption
D5/D6).

#### Scenario: Paged user list

- **WHEN** an authenticated client requests `GET /api/v1/system/users/list`
- **THEN** it returns `code: "200"` with the user payload in `data` (plain list, per the
  implemented surface - a later backend change may add a page wrapper)

#### Scenario: List rows carry the profile and role names

- **WHEN** the list is returned for a user holding profile values and bound roles
- **THEN** the row carries `phone`, `email`, `gender`, `title`, `depId`, `depName`,
  `modalitys`, `repAudit`, `signature`, and `roleNames` (the live role names bound
  through `user_role`)
- **AND** the BCrypt hash never serializes

#### Scenario: Create user

- **WHEN** an authenticated client posts a fresh username to `POST /api/v1/system/users`
- **THEN** the user is created with the BCrypt hash of the DECRYPTED initial password and
  `code: "200"` with the new user id in `data`

#### Scenario: Create carries the profile fields

- **WHEN** the create body additionally carries profile fields (`phone`, `email`,
  `gender`, `title`, `depId`, `modalitys`, `repAudit`, `signature`)
- **THEN** they bind 1:1 onto the new row in the same transaction (a `depId` that
  misses a live department rejects `A0400` and no row is created); absent fields
  stay NULL — the console's one-drawer create submits the full form in one call

#### Scenario: Duplicate username

- **WHEN** the posted username already exists live
- **THEN** the envelope carries the duplicate-username `A`-class code and `data` is null

#### Scenario: User detail for the edit dialog

- **WHEN** an authenticated client requests `GET /api/v1/system/users/{id}` for a live user
- **THEN** the envelope carries `code: "200"` with the full profile (username, name,
  phone, email, gender, title, depId, roleIds, modalitys, repAudit, signature) in `data`
- **AND** the password hash is absent

#### Scenario: Detail of a missing user

- **WHEN** the detail or update or delete endpoint is called for a non-live user id
- **THEN** the envelope answers the B-class not-found code with `data` null

#### Scenario: Profile update

- **WHEN** an authenticated client PUTs profile fields to `/api/v1/system/users/{id}`
- **THEN** the live user's mutable fields update in one transaction and the response is
  `code: "200"`
- **AND** a `roleIds` field REPLACES the user's role bindings (full-replace, the
  role-menu assignment precedent); fields absent from the body stay unchanged

#### Scenario: Signature stores the uploaded path

- **WHEN** the update body carries a `signature` value
- **THEN** the stored value is the common image upload's returned path (a
  `/profile/...` relative path usable as `<img src>`), stored verbatim — no byte
  storage, no table bookkeeping (the logo precedent)

#### Scenario: Username is immutable through the update

- **WHEN** the update body carries a `username` different from the stored one
- **THEN** the stored username is unchanged (the field is ignored, not applied)

#### Scenario: Update validates role ids and department

- **WHEN** the update body's `roleIds` contains a non-live role id, or its `depId` a
  non-live department id
- **THEN** the response is an `A`-class rejection and no field of the user changes

#### Scenario: User logical delete with cascade unbind

- **WHEN** an authorized client DELETEs a live user (neither self nor the seed admin)
- **THEN** the envelope returns `code: "200"` with `data: true`
- **AND** the row persists with `deleted = 1`, disappears from list/detail, and its
  `user_role` bindings are unbound (logical delete)
- **AND** the username becomes re-creatable (the partial unique index covers live rows)

#### Scenario: Self-delete rejected

- **WHEN** the authenticated caller DELETEs their own user id
- **THEN** the response is an `A`-class rejection and no row changes

#### Scenario: Seed-admin delete rejected

- **WHEN** a client DELETEs user id 1 (the seeded bootstrap administrator)
- **THEN** the response is an `A`-class rejection and no row changes

#### Scenario: Password reset and enable toggle

- **WHEN** an authenticated client calls the password-reset or enable endpoint for a user id
- **THEN** it returns `code: "200"` and the mutation persists

#### Scenario: Encrypted password fields

- **WHEN** the create request's `password` or the reset request's `newPassword` is a hex
  SM2 ciphertext produced with the distributed `publicKey`
- **THEN** the operation proceeds exactly as the plaintext flow did before this change
  (BCrypt of the decrypted raw value, `must_change_password = 1` on reset)

#### Scenario: Plaintext or undecryptable value rejected

- **WHEN** `password` / `newPassword` arrives as plaintext, non-hex text, or ciphertext
  undecryptable under the configured key
- **THEN** the envelope answers `code: "A0400"` with a descriptive `msg` and `data` null
- **AND** no user row is created or modified

#### Scenario: New button permissions gate the new endpoints

- **WHEN** a caller without `system:user:update` calls the profile update, or one without
  `system:user:delete` calls the delete endpoint
- **THEN** the copied permission machine rejects the request with `A0301` before any
  controller logic runs

<!-- synced from: user-edit-delete-profile-api 2026-11-11 -->

### Requirement: Controlled Organization Management Endpoints

`lumen-system` SHALL expose organization management under `/api/v1/system/orgs` for the
controlled hospital/campus model. `GET /tree` SHALL return the hospital root and campuses
with profile fields. `POST` SHALL create only a bootstrap hospital root or a campus under
that hospital. `PUT` SHALL update profile fields and status only. `DELETE` SHALL be guarded
by live dependents. Reads are gated by `system:org:list`; the write verbs additionally
require their `system:org:*` button perms.

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

<!-- synced from: org-status-toggle-api 2026-11-06 -->

### Requirement: Organization Status Toggle Endpoint

`lumen-system` SHALL expose `PUT /api/v1/system/orgs/{id}/status` to set a live
organization's lifecycle status. The request body SHALL contain `status` equal to `0`
or `1`; any other value SHALL be rejected without mutation. The endpoint SHALL require
the existing `system:org:update` authority and return a standard success envelope with
`data: true`.

#### Scenario: Valid status toggle

- **WHEN** an authorized client sends `{"status":0}` to an existing organization
- **THEN** the response is a standard `200` envelope with `data: true`
- **AND** the organization status is persisted as `0`

#### Scenario: Invalid status body

- **WHEN** a client omits `status` or sends a value other than `0` or `1`
- **THEN** the request is rejected
- **AND** the stored organization status is unchanged

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

<!-- synced from: org-management-redesign-api 2026-11-06 (spec follow-up: the removed
"Department Endpoints" requirement had documented the DELETE verb; the delta's four
replacements did not carry it forward) -->

### Requirement: Department Logical Delete

`DELETE /api/v1/system/org-deps/{id}` SHALL soft-delete the department and return success in
the standard envelope; the row SHALL never be physically removed. It SHALL disappear from
both read shapes, and because code uniqueness is scoped to live rows the freed code SHALL
become reusable on the same campus. The endpoint requires the `system:dep:delete` button
perm.

#### Scenario: Delete hides the department and keeps the row

- **WHEN** an authorized client deletes a department
- **THEN** the envelope returns `code: "200"` with `data: true`
- **AND** the row persists with `deleted = 1` (the EntityBase logical-delete contract)
- **AND** neither `/list` nor `/page` returns the department afterwards

#### Scenario: A deleted department's code is reusable

- **WHEN** a client creates a new department with the same code on the same campus after the
  original was deleted
- **THEN** the create succeeds

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Role Management Endpoints

`lumen-system` SHALL expose role management under `/api/v1/system/roles`: create, update,
delete, the FULL-REPLACE menu grant (`PUT /{id}/menus`), the paged list
`GET /api/v1/system/roles/list`, and the enable toggle `PUT /{id}/enable`. The list
responds with the standard envelope whose `data` is an IPage-shaped payload; each record
carries the role fields plus the `enable` flag and the `menuIds` grant echo, under the
controller's `system:role:list` page gate. The former gap state (list declared
unimplemented 2026-11-03, served by the dev mock) is retired by this change.

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
- **AND** the gap is closed by role-list-api: the backend implements the list and the
  dev-mock arrangement is retired

#### Scenario: Role list returns paged rows with grant echo
- **WHEN** an authenticated client requests `GET /api/v1/system/roles/list?pageNum=1&pageSize=20`
- **THEN** the envelope carries `code: "200"` with the IPage-shaped payload
  (`records` / `total` / `size` / `current` / `pages`) in `data`
- **AND** each record carries the role fields (`id`, `name`, `type`, `remark`, `enable`,
  audit fields) plus `menuIds`, the ids of the role's live `role_menu` bindings (an empty
  array for an ungranted role), in ascending `id` order

#### Scenario: Role list name and type filters
- **WHEN** the list request carries a `name` query param and/or a `type` query param
- **THEN** only live roles whose name contains the given value and/or whose type equals
  the given value are returned, in ascending `id` order
- **AND** pagination defaults to `pageNum=1`/`pageSize=20` when the params are absent

#### Scenario: Enable toggle persists 0 or 1

- **WHEN** an authorized client sends `{"enable":0}` (or `{"enable":1}`) to
  `PUT /api/v1/system/roles/{id}/enable` for a live role
- **THEN** the response is a standard `200` envelope with `data: true`
- **AND** the role's `enable` is persisted as the posted value

#### Scenario: Enable toggle rejects other values

- **WHEN** the toggle body carries anything other than `0` or `1`
- **THEN** the request is rejected without mutation

<!-- synced from: role-type-dict-api 2026-11-09 -->

### Requirement: Dict Items Lookup By Catalog Name

<!-- synced-path correction: audit-log-unify-api 2026-11-10 — the role-type-dict
     archive recorded `/dicts/items/{catalogName}` but the wired endpoint (commit
     925c852), the frontend client, and that change's own archived verify all use
     `/dicts/detail/items/{catalogName}`; the spec now matches the implementation -->
`lumen-system` SHALL expose a read-only lookup `GET /api/v1/system/dicts/detail/items/{catalogName}`
that resolves one dict catalog by its unique `name` and returns its live, enabled
details as a slim `{code, value}[]` (the V9/V12 single-text model) in the standard
envelope. The endpoint is for label-resolution consumers such as the role console and is
authenticated-only (no permission string — see auth-rbac).

#### Scenario: Items returned for a known catalog

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/detail/items/角色类型`
- **THEN** the envelope carries `code: "200"` with the catalog's live enabled
  details as `{code, value}[]` in `data`, ordered by `sort` ascending

#### Scenario: Unknown catalog resolves to an empty list

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/detail/items/不存在目录`
- **THEN** the envelope carries `code: "200"` with an empty `data` array

#### Scenario: Disabled details are excluded

- **WHEN** a detail row of the catalog carries `enable = 0`
- **THEN** it is absent from the response

#### Scenario: Lookup requires authentication but no permission string

- **WHEN** an authenticated user WITHOUT `system:dict:list` calls the endpoint
- **THEN** the request succeeds (`code: "200"`) — the dict page gate does not apply
- **AND** an unauthenticated call is rejected like every other endpoint

<!-- synced from: role-type-dict-api 2026-11-09 -->

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

<!-- synced from: workstation-w-nodes-api 2026-11-06 - REPLACES the per-user
     workstation-grant requirement (removed by the W-node correction; see
     archive/workstation-w-nodes-api delta) -->

### Requirement: Workstation Menu Nodes

The menu tree SHALL carry workstation nodes as `type = W` entries at L1 level (放射
工作站/超声工作站/内镜工作站 seeded by V22 with the admin auto-grant; new stations
creatable through the menu management page's type option). The login/me menu tree
carries the W nodes with their subtrees — the frontend derives the workstation list
from the granted tree (a user whose roles hold no W node sees the console only).
The menu create/update validation SHALL accept the `W` type.

#### Scenario: W nodes are seeded and granted

- **WHEN** the migrations run
- **THEN** three type-W nodes (放射工作站/超声工作站/内镜工作站) exist at L1
- **AND** the admin role's grant contains them

#### Scenario: Grant flows through roles

- **WHEN** a role is granted the 放射工作站 W node and a user binds that role
- **THEN** the user's next login/me menu tree contains the W node with its subtree

#### Scenario: Wrong machinery is gone

- **WHEN** a client calls `PUT /api/v1/system/users/{id}/workstations`
- **THEN** the endpoint does not exist (404)
- **AND** `user_workstation` no longer exists as a table

<!-- synced from: org-logo-upload-api 2026-11-06 -->
### Requirement: Common Image Upload Endpoint

lumen-system SHALL expose `POST /api/v1/system/common/upload/image` as the common
image transport (OPS `CommonController.uploadImage` 1:1; coordination org-logo-upload
D1–D5): authenticated callers only (no business permission bound — D3), one
`file` multipart part, stored server-side under the configured upload root, answered
with the standard envelope whose `data` is `{ "path": <stored path> }`. The endpoint is
transport-only: it records nothing in any table (logo is a 1:1 column value; the
sys_attach bookkeeping layer is a recorded future direction, D2).

#### Scenario: Upload succeeds

- **WHEN** an authenticated caller POSTs a valid image (≤2MB) as the `file` part
- **THEN** the envelope answers `{ "code": "200", "msg": ..., "data": { "path": "/profile/avatar/<yyyy/MM/dd>/<uuid>.<ext>" } }`
- **AND** the bytes exist on disk under `${upload.home}` at that relative path

#### Scenario: Invalid image is rejected with A0400

- **WHEN** the posted file's extension is outside `png/jpg/jpeg/webp`, exceeds 2MB,
  or is empty
- **THEN** the envelope answers with code `A0400` and a descriptive msg
- **AND** no file is written to disk

#### Scenario: Unauthenticated caller

- **WHEN** a request without a valid token hits the endpoint
- **THEN** the copied entry point answers the transport-auth semantic (`"203"`)
  before any controller logic runs

#### Scenario: Stored path is readable back anonymously

- **WHEN** an upload has returned `data.path = /profile/avatar/.../x.png`
- **THEN** `GET /profile/avatar/.../x.png` (no token) serves the identical bytes
- **AND** the same path is stable for reuse as `<img src>` (the logo column value)

#### Scenario: Other services are unaffected

- **WHEN** a lumen service whose config does not define `upload.home` boots
- **THEN** its `/profile/**` registration stays without locations (today's
  OPS-parity behavior) and nothing about its request handling changes
