## Modified Requirements

### Requirement: Menu Management

The console SHALL provide menu management backed by the menu family
(`GET /api/v1/system/menus/tree`, `POST /api/v1/system/menus`,
`PUT /api/v1/system/menus/{id}`, `DELETE /api/v1/system/menus/{id}`) so administrators
can shape the directory/menu/button tree and author button permission strings:

- The page SHALL render the full tree (标题/类型 tag/`perms`/排序/可见) with per-node
  actions 新增下级, 编辑, 删除, gated client-side by the `system:menu:list` permission.
- The create/edit dialog SHALL collect type (目录 M / 菜单 C / 按钮 F), parent node
  (offering 根目录 (`upId = 0`) plus every non-`F` node and, when editing, not the
  node's own subtree), 标题, `perms`
  (buttons; free text with the `模块:资源:动作` convention), `path`/`component`
  (directories/menus), `icon`, `sort`, and `visible`.
- The 上级菜单 selector SHALL render on every create entry (toolbar 添加 defaults to
  根目录; row 添加子菜单 defaults to the clicked node) and in edit, where a top-level
  node displays 根目录.
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

#### Scenario: Toolbar create defaults to root

- **WHEN** an administrator opens the 添加 drawer from the toolbar
- **THEN** the 上级菜单 selector is visible and prefilled with 根目录
- **AND** saving sends `upId = 0`

#### Scenario: Root stays selectable in edit

- **WHEN** an administrator edits a top-level node
- **THEN** the 上级菜单 selector displays 根目录
- **AND** 根目录 remains a selectable choice so a node can be moved back to top level
