# auth-rbac Delta: role-type-dict-api

## MODIFIED Requirements

### Requirement: Roles and Menu-Button Permissions
The service SHALL provide `role_info`, `user_role`, `role_menu`, and `menu_info` (`upId`
tree, `type` = directory/menu/button, `perms` strings) - the model proven in the 随访
reference. Role-menu assignment SHALL be an endpoint; the login/me responses SHALL include
the user's permission set (menu tree + `perms` strings) so the frontend can render dynamic
menus and gate buttons. Every management endpoint family SHALL enforce its page-level
permission string server-side (login/me excluded): users→`system:user:list`,
roles→`system:role:list`, menus→`system:menu:list`, orgs→`system:org:list`,
deps→`system:dep:list`, dicts→`system:dict:list`, logs→`system:log:list`. City reference
queries and the dict items lookup by catalog name stay authenticated-only (read-only
reference data any role may read — the lookup consumers are role managers holding only
`system:role:list`). Button-level granularity (per-button F-node gating) is the recorded
follow-up once workstation job roles exist - this requirement pins page-level granularity
only.

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

#### Scenario: Dict items lookup stays authenticated-only
- **WHEN** a user holding only `system:role:list` calls
  `GET /api/v1/system/dicts/items/{catalogName}`
- **THEN** the call succeeds without any dict permission string
- **AND** an unauthenticated call is still rejected
