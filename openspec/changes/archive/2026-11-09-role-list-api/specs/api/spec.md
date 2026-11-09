# api Delta: role-list-api

## MODIFIED Requirements

### Requirement: Role Management Endpoints

`lumen-system` SHALL expose role management under `/api/v1/system/roles`: create, update,
delete, the FULL-REPLACE menu grant (`PUT /{id}/menus`), and the paged list
`GET /api/v1/system/roles/list`. The list responds with the standard envelope whose `data`
is an IPage-shaped payload; each record carries the role fields plus the `menuIds` grant
echo, under the controller's `system:role:list` page gate. The former gap state (list
declared unimplemented 2026-11-03, served by the dev mock) is retired by this change.

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
- **AND** each record carries the role fields (`id`, `name`, `type`, `remark`, audit
  fields) plus `menuIds`, the ids of the role's live `role_menu` bindings (an empty
  array for an ungranted role), in ascending `id` order

#### Scenario: Role list name and type filters

- **WHEN** the list request carries a `name` query param and/or a `type` query param
- **THEN** only live roles whose name contains the given value and/or whose type equals
  the given value are returned, in ascending `id` order
- **AND** pagination defaults to `pageNum=1`/`pageSize=20` when the params are absent
