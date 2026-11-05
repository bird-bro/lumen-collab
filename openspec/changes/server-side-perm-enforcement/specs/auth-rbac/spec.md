# Spec Delta

## MODIFIED Requirements

### Requirement: Login and Token Issuance
`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.
The cached login session SHALL carry the user's permission set (the `perms` strings
assembled from the granted menu rows) so every authenticated request can be authorized
server-side.

#### Scenario: Successful login
- **WHEN** a client posts valid credentials of the seeded admin
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`

#### Scenario: Invalid credentials
- **WHEN** a client posts a wrong password
- **THEN** the response carries an `A`-class code (e.g. `"A0210"` 用户密码错误) with
  `data` null
- **AND** no token is issued

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
