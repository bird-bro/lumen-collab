# auth-rbac Specification

## Purpose

Defines lumen-system's authentication and RBAC foundation: the JWT issuance/validation loop
(built on the copied framework security classes), the hierarchical org tree, users, roles,
and the menu model with page-level and button-level permissions (pattern adopted from the
随访 reference implementation), plus the `org_dep` department table that anchors medical
workflows to the org tree (lumen table naming per user decision 2026-11-03).

<!-- synced from: auth-rbac-foundation 2026-11-03 -->

## Requirements

<!-- synced from: password-transport-encryption-api 2026-11-09 -->

### Requirement: Login and Token Issuance

`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.
The login request's `password` field SHALL arrive as SM2 ciphertext (C1C3C2, hex —
coordination password-transport-encryption D4) and the service SHALL decrypt it with the
static configured key BEFORE the BCrypt match; the decrypted value is the only input to
the existing BCrypt verification, which is unchanged. A `password` field that is not
decryptable ciphertext (plaintext, non-hex, wrong length, wrong key) SHALL be rejected
with envelope code `"A0400"` (用户请求参数错误, the copied `ExceptionEnum` constant
reused verbatim — no OPS enum modification) and SHALL NOT be counted as a wrong-password
attempt (coordination D5). There is NO plaintext acceptance path — clean cutover, no
dual-track mode flag. `lumen-system` SHALL additionally expose `GET
/api/v1/system/auth-key` (anonymous) returning `{ keyId, publicKey }` per the api
capability's Authentication Endpoints requirement; the key pair is static configuration
(`lumen.security.password-crypto.{key-id, sm2-private-key}`, private key = raw scalar D
hex, public key derived, sensitive-config hygiene per coordination D2).
The cached login session SHALL carry the user's permission set (the `perms` strings
assembled from the granted menu rows) so every authenticated request can be authorized
server-side.

#### Scenario: Successful login

- **WHEN** a client posts valid credentials of the seeded admin — the `password` field
  being SM2 ciphertext of the raw password encrypted under the distributed `publicKey`
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`

#### Scenario: Invalid credentials

- **WHEN** a client posts a wrong password (correctly SM2-encrypted)
- **THEN** the response carries an `A`-class code (e.g. `"A0210"` 用户密码错误) with
  `data` null
- **AND** no token is issued

#### Scenario: Plaintext password rejected (clean cutover)

- **WHEN** a client posts `{"username":"admin","password":"admin123"}` — any value that
  is not SM2 ciphertext
- **THEN** the response is `code: "A0400"` with a descriptive `msg` and `data` null
- **AND** no token is issued and the account lookup outcome is not disclosed

#### Scenario: Tampered ciphertext rejected

- **WHEN** a client posts SM2 ciphertext that was altered after encryption (or encrypted
  under a foreign key)
- **THEN** the response is `code: "A0400"` with `data` null
- **AND** no token is issued

#### Scenario: Key endpoint is anonymous

- **WHEN** a client requests `GET /api/v1/system/auth-key` without a token
- **THEN** the envelope answers `code: "200"` with `data = { keyId, publicKey }` where
  `publicKey` is the 130-char hex uncompressed SM2 point derived from the configured
  private scalar

#### Scenario: Session carries the permission set

- **WHEN** a user logs in
- **THEN** the cached session holds that user's permission set exactly as the `/me`
  response reports it
- **AND** a session created before the permission set existed is treated as empty
  (deny) rather than trusted

### Requirement: Token Validation on Every Request
Every authenticated request to `lumen-system` SHALL pass through the copied
`JwtAuthenticationTokenFilter`: a missing, expired, or invalid token SHALL be rejected by
the copied `AuthenticationEntryPointImpl` with envelope code `"203"` (HTTP 200 - the copied
machine's recorded transport-auth semantic, user ruling 2026-11-02) and SHALL NOT reach any
controller; a valid token SHALL populate the security context with the user identity used
by `MetaObjectConfig` audit filling. The security context SHALL carry the session's
permission set as granted authorities so method-level checks can evaluate it.

#### Scenario: Request without token
- **WHEN** a request hits a protected endpoint without a token
- **THEN** it is rejected with envelope code `"203"` (HTTP 200, `data` null) and no
  controller method executes

#### Scenario: Audit identity from valid token
- **WHEN** an authenticated user creates data through any management endpoint
- **THEN** `MetaObjectConfig` fills `create_by` with that user's id

#### Scenario: Authorities derive from the session permission set
- **WHEN** a valid token populates the security context
- **THEN** the authentication's granted authorities equal the session's permission set
  (empty set yields empty authorities, never null)

<!-- synced from: org-management-redesign-api 2026-11-06 (replaces "Hierarchical Org Tree") -->

### Requirement: Hospital-Campus Organization Types

The on-premise organization model SHALL use `org_info.type = 0` for hospital and `type = 1`
for campus in v1. At most one live hospital root SHALL exist. A hospital MAY have campus
children, while a campus SHALL NOT have child `org_info` nodes.

#### Scenario: Fresh on-premise deployment

- **WHEN** migrations complete on a fresh database
- **THEN** one editable first-run hospital root and one default campus exist
- **AND** the hospital root uses the Option B bootstrap placeholder details

#### Scenario: Existing departments remain visible

- **WHEN** the migration moves seeded departments to the default campus
- **THEN** the three seeded departments remain live and enabled with their original codes
- **AND** they are returned for the default campus

#### Scenario: Root proliferation is blocked

- **WHEN** an ordinary administrator attempts to create a second live hospital root
- **THEN** the service rejects the request with an A-class business error

<!-- synced from: org-management-redesign-api 2026-11-06 -->

### Requirement: Organization Lifecycle Guards

Organization type SHALL be immutable after creation. Reparenting SHALL be rejected in this
change. Organization deletion SHALL be refused while the node has live child organizations
or live departments.

#### Scenario: Type and parent cannot be rewritten

- **WHEN** an administrator updates an organization with a new type or parent
- **THEN** the service rejects the request with an A-class business error
- **AND** the stored type and parent remain unchanged

#### Scenario: Dependent organization cannot be deleted

- **WHEN** an administrator deletes an organization with a live child or department
- **THEN** the service rejects the deletion with an A-class business error
- **AND** the organization and dependents remain live

<!-- synced from: org-management-redesign-api 2026-11-06 (replaces "Department Anchored to the Org Tree") -->

### Requirement: Campus-Anchored Departments

Departments SHALL belong to live, enabled campus nodes rather than arbitrary organization
nodes:

- Department create/update SHALL reject hospital-root and disabled-campus owners.
- Department code uniqueness SHALL be scoped to the owning campus among live rows.
- Department codes SHALL be immutable after creation.
- Department ids and existing codes SHALL remain stable through migration.

#### Scenario: Two campuses have the same department

- **WHEN** campus A and campus B each define `radiology`
- **THEN** both department rows persist
- **AND** each campus list returns only its own row

#### Scenario: Hospital cannot own a department

- **WHEN** a client creates or updates a department whose owner is the hospital root
- **THEN** the request is rejected with an A-class business error
- **AND** the department row is unchanged

#### Scenario: Legacy department migration preserves routing identity

- **WHEN** the seeded departments move from the hospital root to the default campus
- **THEN** their department ids and codes are unchanged

### Requirement: User Management
The service SHALL provide user CRUD on `user_info` (username unique, password stored
hashed, enable flag). Creating or modifying a user SHALL require an authenticated
administrator, and the audit fields SHALL be filled from the security context. The seeded
default administrator SHALL carry a must-change-password flag on first login.

#### Scenario: Administrator creates a user
- **WHEN** an authenticated administrator creates a user
- **THEN** the user persists with hashed password and `create_by` filled

#### Scenario: Duplicate username rejected
- **WHEN** a user is created with an existing username
- **THEN** the service rejects it with an `A`-class code

### Requirement: Roles and Menu-Button Permissions
The service SHALL provide `role_info`, `user_role`, `role_menu`, and `menu_info` (`upId`
tree, `type` = directory/menu/button, `perms` strings) - the model proven in the 随访
reference. Role-menu assignment SHALL be an endpoint; the login/me responses SHALL include
the user's permission set (menu tree + `perms` strings) so the frontend can render dynamic
menus and gate buttons. Every management endpoint family SHALL enforce its page-level
permission string server-side (login/me excluded): users→`system:user:list`,
roles→`system:role:list`, menus→`system:menu:list`, orgs→`system:org:list`,
deps→`system:dep:list`, dicts→`system:dict:list`, logs→`system:log:list`. City reference
queries stay authenticated-only (reference data any role may read). Button-level
granularity (per-button F-node gating) is the recorded follow-up once workstation job
roles exist - this requirement pins page-level granularity only.

#### Scenario: Role-menu assignment
- **WHEN** an administrator assigns menus to a role
- **THEN** the binding persists in `role_menu`
- **AND** users of that role receive those menus and perms at login

#### Scenario: Button permission in the perm set
- **WHEN** a menu row of `type = button` with `perms = "radiology:report:audit"` is bound
  to the user's role
- **THEN** the user's permission set contains that `perms` string

#### Scenario: Endpoint rejects a caller without the family permission
- **WHEN** an authenticated caller whose session lacks `system:role:list` calls any
  `/api/v1/system/roles/**` endpoint
- **THEN** the request is rejected with envelope code `"A0301"` and no controller method
  executes

#### Scenario: Administrator passes through
- **WHEN** the seeded administrator (whose role owns every menu) calls any management
  endpoint family
- **THEN** the request executes normally - the permission set covers all seven families

### Requirement: Seed Data
The Flyway migrations SHALL include idempotent seed data: the default administrator
(known initial credentials, must-change-password flag) and the three v1 departments under
the hospital root - so a fresh deployment can log in and configure without hand-inserted
rows.

#### Scenario: Fresh deployment can log in
- **WHEN** a fresh database receives the migrations
- **THEN** the default administrator can log in
- **AND** the three seeded departments are visible in the department list


<!-- synced from: menu-management-api 2026-11-06 -->

### Requirement: Menu Node Lifecycle Administration

Menu nodes (directories, menus, buttons) SHALL be administrable through the menu
management family while preserving the grant model:

- Creating a menu node SHALL also insert the node into the admin role's `role_menu`
  grant, so the super administrator immediately sees and can re-grant every new node
  (lumen keeps explicit `role_menu` grants - no wildcard authority). Grant rows SHALL be
  created only on CREATE; updating a node SHALL NOT alter any role's grants.
- Updating a node SHALL NOT be able to reparent it into itself or its own descendant
  subtree, and SHALL NOT allow any node to hang under a button (`type = F`) node.
- Deleting a menu node SHALL be refused while the node still has live children.
  Deleting a childless node SHALL soft-delete the node and SHALL soft-delete its
  `role_menu` binding rows, so no role keeps granting a permission whose owning node is
  gone.

#### Scenario: New node is immediately admin-visible

- **WHEN** an administrator creates any menu node
- **THEN** the admin role's grant contains the new node without a separate assignment step

#### Scenario: Update never touches grants

- **WHEN** an administrator renames, re-icons, or moves an existing node
- **THEN** the `role_menu` rows for that node are unchanged

#### Scenario: Deleted button stops granting its permission

- **WHEN** a button node (`type = F`) with a `perms` string is deleted
- **THEN** roles that had the button granted no longer receive the `perms` string in
  their login/me permission set


<!-- synced from: button-perm-enforcement-api 2026-11-06 -->

### Requirement: Button-Level Permission Enforcement

Every console write endpoint SHALL be enforced at button level: the endpoint carries a
method-level `@PreAuthorize` whose authority is the button node's `perms` string (the
method annotation replaces the class-level page gate for that method; read endpoints
keep the page gate). The button inventory is the coordination doc's perms table - 用户
(create/reset-pwd/enable), 角色 (create/update/delete/assign), 菜单 (create/update/
delete), 机构 (create/update/delete), 科室 (create/update/delete), 字典目录
(dict-catal:create/update/delete) and 字典明细 (dict-detail:create/update/delete).

The Flyway seed SHALL insert the 22 button nodes (`type = F`) under their console C
pages with the inventory's `perms` strings, and SHALL grant every new node to the admin
role (role 1) - idempotently (guarded inserts + sequence resync, the V5 discipline), so
the super administrator sees and can re-grant every button without a manual step.

#### Scenario: Write endpoint rejects without the button perm

- **WHEN** an authenticated user holding only the page perm (`system:user:list`, via a
  role granted just the 用户管理 page) calls `POST /api/v1/system/users`
- **THEN** the request is denied with the `A0301` envelope and no user row is created

#### Scenario: Button perm alone authorizes the write

- **WHEN** a role is granted only the 新建用户 button node (not the page) and its user
  calls `POST /api/v1/system/users`
- **THEN** the request executes (the button perm is the action authority)
- **AND** the same user's `GET /api/v1/system/users/list` is still denied by the
  class-level page gate

#### Scenario: Admin sees every button after seeding

- **WHEN** the migrations run on a database where the admin role owned every menu
- **THEN** the 22 button nodes exist under their console pages with their `perms`
  strings
- **AND** the admin role's login/me permission set contains every button perms string

#### Scenario: Re-running the seed is a no-op

- **WHEN** `V15` runs again on an already-migrated database
- **THEN** no duplicate menu or role_menu rows appear (guarded inserts)


<!-- synced from: workstation-w-nodes-api 2026-11-06 - REPLACES the workstation
     authorization requirement (per-user direct grant removed by the W-node
     correction; see archive/workstation-w-nodes-api delta) -->

### Requirement: Workstation Nodes in the Menu Tree

The menu tree SHALL carry workstation nodes as `type = W` entries at L1 level, granted
through the existing role_menu mechanism (roles own menu permissions; users bind
roles):

- V22 SHALL seed 放射工作站/超声工作站/内镜工作站 W nodes (admin auto-grant, content-
  guard discipline) and SHALL drop the wrong machinery (`user_workstation` table, the
  per-user assignment endpoint's button node, the `menu_info.workstation` column).
- The menu management page SHALL offer the 工作站 type (the station set grows only
  through the menu tree, NOT through a per-user grant surface).
- A role granted a W node SHALL pass that workstation's subtree to every bound user's
  login/me menu tree; a user whose roles hold no W node SHALL see the console only.

#### Scenario: W nodes seeded and role-granted

- **WHEN** the migrations run and the admin opens 角色管理's 菜单授权 tree
- **THEN** the three W nodes render (with their type tag) among the grantable nodes

#### Scenario: Station access is role-driven

- **WHEN** a role is granted the 放射工作站 W node and a user binds that role
- **THEN** the user's next login/me menu tree contains the W node and its subtree
- **AND** a user bound only to a role WITHOUT the W node sees no workstation in the tree

#### Scenario: Wrong machinery removed

- **WHEN** the migrations run
- **THEN** `user_workstation` does not exist, the workstation column is dropped, and
  the `system:user:assign-ws` node is gone from the tree and grants
