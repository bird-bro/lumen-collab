# api Delta: menu-management-api

## ADDED Requirements

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
