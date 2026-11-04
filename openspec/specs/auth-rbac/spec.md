# auth-rbac Specification

## Purpose

Defines lumen-system's authentication and RBAC foundation: the JWT issuance/validation loop
(built on the copied framework security classes), the hierarchical org tree, users, roles,
and the menu model with page-level and button-level permissions (pattern adopted from the
随访 reference implementation), plus the `org_dep` department table that anchors medical
workflows to the org tree (lumen table naming per user decision 2026-11-03).

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
## Requirements

<!-- synced from: rename-auth-to-system 2026-11-04 -->
### Requirement: Login and Token Issuance
`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.

#### Scenario: Successful login
- **WHEN** a client posts valid credentials of the seeded admin
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`

#### Scenario: Invalid credentials
- **WHEN** a client posts a wrong password
- **THEN** the response carries an `A`-class code (e.g. `"A0210"` 用户密码错误) with
  `data` null
- **AND** no token is issued

<!-- synced from: rename-auth-to-system 2026-11-04 -->
### Requirement: Token Validation on Every Request
Every authenticated request to `lumen-system` SHALL pass through the copied
`JwtAuthenticationTokenFilter`: a missing, expired, or invalid token SHALL be rejected by
the copied `AuthenticationEntryPointImpl` with envelope code `"203"` (HTTP 200 - the copied
machine's recorded transport-auth semantic, user ruling 2026-11-02) and SHALL NOT reach any
controller; a valid token SHALL populate the security context with the user identity used
by `MetaObjectConfig` audit filling.

#### Scenario: Request without token
- **WHEN** a request hits a protected endpoint without a token
- **THEN** it is rejected with envelope code `"203"` (HTTP 200, `data` null) and no
  controller method executes

#### Scenario: Audit identity from valid token
- **WHEN** an authenticated user creates data through any management endpoint
- **THEN** `MetaObjectConfig` fills `create_by` with that user's id

### Requirement: Hierarchical Org Tree
The service SHALL provide `org_info` - a self-referencing hierarchy (`upId`, `level`,
`type`) supporting multi-organisation deployment: the local product defaults to a single
root (the hospital), and multiple roots serve the cloud product. CRUD endpoints SHALL
maintain the tree and expose it as a tree response (pattern: 随访 `UnitTree`, lumen tables
renamed `org_info`/`org_dep`/`role_info` - user decision 2026-11-03).

#### Scenario: Tree response
- **WHEN** the unit tree is queried
- **THEN** the response reflects parent-child nesting from `upId`
- **AND** the seeded hospital root is present

#### Scenario: Multi-root capable
- **WHEN** a second root org is created
- **THEN** the tree holds two independent roots without schema change

### Requirement: Department Anchored to the Org Tree
Departments SHALL live in their own `org_dep` table with a foreign key to an `org_info`
node - keeping medical attributes (added by later changes) out of the org tree. The seed
SHALL create three departments (radiology, ultrasound, endoscopy) under the hospital root.

#### Scenario: Seeded departments
- **WHEN** the seed migration completes
- **THEN** three departments exist, each FK-linked to a unit node

#### Scenario: Department CRUD
- **WHEN** an administrator creates a department for a unit node
- **THEN** it persists with the FK and is listed under that unit

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
menus and gate buttons.

#### Scenario: Role-menu assignment
- **WHEN** an administrator assigns menus to a role
- **THEN** the binding persists in `role_menu`
- **AND** users of that role receive those menus and perms at login

#### Scenario: Button permission in the perm set
- **WHEN** a menu row of `type = button` with `perms = "radiology:report:audit"` is bound
  to the user's role
- **THEN** the user's permission set contains that `perms` string

### Requirement: Seed Data
The Flyway migrations SHALL include idempotent seed data: the default administrator
(known initial credentials, must-change-password flag) and the three v1 departments under
the hospital root - so a fresh deployment can log in and configure without hand-inserted
rows.

#### Scenario: Fresh deployment can log in
- **WHEN** a fresh database receives the migrations
- **THEN** the default administrator can log in
- **AND** the three seeded departments are visible in the department list
