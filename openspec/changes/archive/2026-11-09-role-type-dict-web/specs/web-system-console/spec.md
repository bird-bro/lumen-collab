# web-system-console Delta: role-type-dict-web

## MODIFIED Requirements

### Requirement: Role Management and Menu Assignment
The console SHALL provide role management backed by `GET /api/v1/system/roles/list`,
create (`POST /api/v1/system/roles`), edit (`PUT /api/v1/system/roles/{id}`), and delete
(`DELETE /api/v1/system/roles/{id}`). For each role, menus SHALL be assignable via a tree
with checkboxes that full-replaces the grant through `PUT /api/v1/system/roles/{id}/menus`.
The list is served by the real backend endpoint (the former dev-mock arrangement is
retired); its rows carry the `menuIds` grant echo that drives the assignment dialog's
initial checked set. The 类型 vocabulary SHALL be resolved from the data dictionary
(catalog 「角色类型」 via `GET /api/v1/system/dicts/items/{catalogName}`) instead of a
compile-time label map: the column renders the dict `value` matched by the row's `type`
code, and the create/edit drawers offer the dict's items as a Select (option value =
`code`, initialValue 10). When the dictionary lacks a code or the lookup fails, the
column falls back to raw-code rendering and one error message is surfaced — the page
never blocks, and the drawer's Select stays empty without breaking the CRUD round-trip
(the backend accepts the int as-is).

#### Scenario: Role CRUD round-trip
- **WHEN** an administrator creates, renames, and deletes a role
- **THEN** the documented endpoints are called and the list reflects the result

#### Scenario: Menu assignment is full-replace
- **WHEN** an administrator checks a set of menus for a role and saves
- **THEN** the checked set is sent as the role's complete grant
- **AND** reopening the dialog shows exactly that set

#### Scenario: Type label resolved from the dict
- **WHEN** the roles list loads with the 「角色类型」 dict carrying
  code 10 → 系统角色 and code 11 → RCP角色
- **THEN** each row's 类型 cell shows the dict value matched by its `type`
  with the established tag chrome (code 10 blue, other codes cyan)

#### Scenario: Unknown code falls back to the raw number
- **WHEN** a row's `type` has no entry in the dict items
- **THEN** the 类型 cell renders the raw numeric code — no blank cell

#### Scenario: Dict fetch failure degrades without blocking
- **WHEN** the dict lookup request fails
- **THEN** one error message is surfaced, the table still renders with raw
  codes, and the drawer's 类型 Select is empty but the CRUD round-trip still works

#### Scenario: Drawer offers dict categories
- **WHEN** the create drawer opens with the dict loaded
- **THEN** 类型 is a Select whose options are the dict items
  (label = value, value = code) with 系统角色 preselected (initialValue 10)
- **AND** submitting posts the selected numeric code unchanged

#### Scenario: One wire call per catalog per session
- **WHEN** the roles page re-lists, reopens a drawer, or the user navigates
  away and back within the session
- **THEN** the dict lookup resolves from the session cache without re-fetching

#### Scenario: Status switch reflects and toggles the enable flag
- **WHEN** the roles table renders with the 状态 column and a row carries
  `enable = 1` (or `0`)
- **THEN** the centered switch shows 启用 (on) or 停用 (off) with the
  aria-label 启用 {role name}
- **AND** toggling sends `PUT /api/v1/system/roles/{id}/enable` with
  `{"enable": 1|0}` and the list re-reads the persisted value

#### Scenario: Toggle failure reverts the switch
- **WHEN** the toggle call fails (backend rejection or network error)
- **THEN** the switch reverts to its previous visual state and the backend
  message is surfaced — no optimistic persistence
