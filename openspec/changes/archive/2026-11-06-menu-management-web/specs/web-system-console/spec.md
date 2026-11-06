# web-system-console Delta: menu-management-web

## REMOVED Requirements

### Requirement: Menu Tree Visibility

**Reason**: superseded by full menu management - the page is no longer a read-only
surface; its tree-render duty (directory/menu/button hierarchy with `perms` visible)
continues inside the Menu Management requirement below.

**Migration**: none for users - the page keeps the same route and tree view; the
management affordances are additive. The read-only `GET /api/v1/system/menus/tree`
endpoint and its gating are unchanged.

## ADDED Requirements

### Requirement: Menu Management

The console SHALL provide menu management backed by the menu family
(`GET /api/v1/system/menus/tree`, `POST /api/v1/system/menus`,
`PUT /api/v1/system/menus/{id}`, `DELETE /api/v1/system/menus/{id}`) so administrators
can shape the directory/menu/button tree and author button permission strings:

- The page SHALL render the full tree (标题/类型 tag/`perms`/排序/可见) with per-node
  actions 新增下级, 编辑, 删除, gated client-side by the `system:menu:list` permission.
- The create/edit dialog SHALL collect type (目录 M / 菜单 C / 按钮 F), parent node
  (offering no `F` nodes and, when editing, not the node's own subtree), 标题, `perms`
  (buttons; free text with the `模块:资源:动作` convention), `path`/`component`
  (directories/menus), `icon`, `sort`, and `visible`.
- Deleting a node SHALL require confirmation; backend rejection messages (e.g. live
  children) SHALL surface on the page.

#### Scenario: Tree renders with types and perms

- **WHEN** an administrator opens the menu page
- **THEN** the directory/menu/button hierarchy renders with each node's type tag and,
  where present, its `perms` string

#### Scenario: Button node CRUD round-trip

- **WHEN** an administrator creates a button node under a menu with a `perms` string,
  edits it, and deletes it
- **THEN** each operation calls the documented endpoint and the tree reflects the result

#### Scenario: Delete rejection surfaces

- **WHEN** an administrator deletes a node that still has live children
- **THEN** the backend's rejection message is shown and the node remains in the tree

#### Scenario: Dialog guards the parent choice

- **WHEN** the create/edit dialog renders its parent TreeSelect
- **THEN** button nodes are not selectable as parents
- **AND** when editing a node, the node itself and its descendants are not selectable
