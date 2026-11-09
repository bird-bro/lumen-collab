# api Delta: role-type-dict-api

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Dict Items Lookup By Catalog Name

`lumen-system` SHALL expose a read-only lookup `GET /api/v1/system/dicts/items/{catalogName}`
that resolves one dict catalog by its unique `name` and returns its live, enabled
details as a slim `{code, value}[]` (the V9/V12 single-text model) in the standard
envelope. The endpoint is for label-resolution consumers such as the role console and is
authenticated-only (no permission string — see auth-rbac).

#### Scenario: Items returned for a known catalog

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/items/角色类型`
- **THEN** the envelope carries `code: "200"` with the catalog's live enabled
  details as `{code, value}[]` in `data`, ordered by `sort` ascending

#### Scenario: Unknown catalog resolves to an empty list

- **WHEN** an authenticated client requests `GET /api/v1/system/dicts/items/不存在目录`
- **THEN** the envelope carries `code: "200"` with an empty `data` array

#### Scenario: Disabled details are excluded

- **WHEN** a detail row of the catalog carries `enable = 0`
- **THEN** it is absent from the response

#### Scenario: Lookup requires authentication but no permission string

- **WHEN** an authenticated user WITHOUT `system:dict:list` calls the endpoint
- **THEN** the request succeeds (`code: "200"`) — the dict page gate does not apply
- **AND** an unauthenticated call is rejected like every other endpoint
