# Proposal: menu-management-api

## Why

The menu tree - the permission backbone (role_menu grants, login/me perms set, server-side
`@PreAuthorize` page gates) - can only be shaped by Flyway seeds; `lumen-system` exposes a
single read endpoint (`GET /api/v1/system/menus/tree`). Every IA adjustment therefore needs
a migration, and the button-level (F-node) permission half of the model, though 1:1 ready
since the 随访-recipe foundation, has no authoring surface.

## What Changes

- **Add `POST /api/v1/system/menus`** - create a menu node (`upId`/`type` M/C/F/`title`/
  `perms`/`path`/`component`/`icon`/`sort`/`visible`), returns the new id. Creation
  auto-grants the node to the admin role (role 1) because lumen keeps explicit
  `role_menu` grants (no wildcard) and an ungranted node would be invisible to the super
  admin.
- **Add `PUT /api/v1/system/menus/{id}`** - full field update including move (`upId`).
  Guards: `upId = id` and `upId` inside the node's own descendant subtree are rejected
  (cycle would corrupt the tree and orphan the node via the TreeUtils drop); a child under
  an `F` (button) parent is rejected - buttons are leaves.
- **Add `DELETE /api/v1/system/menus/{id}`** - guarded soft delete: live children reject
  with an `A`-class business error (no cascade); otherwise the row soft-deletes and the
  node's `role_menu` bindings soft-clean (same discipline as `RoleServiceImpl.delete`).
- Family gate unchanged: the class-level `system:menu:list` authority governs the new
  endpoints too (established role/user/org pattern; no button-level backend enforcement -
  F-node perms stay free text per the `模块:资源:动作` convention).
- No menu-model/schema change, no workstation coupling (recorded decision - workstation
  stays its own future change), tree endpoint untouched.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: the lumen-system endpoint contract gains the menu create/update/delete family
  next to the existing Menu Tree Endpoint requirement (request/response shapes above).
- `auth-rbac`: menu node lifecycle administration becomes a requirement - create
  auto-grants the admin role, update enforces the move/cycle and button-leaf guards,
  delete blocks on live children and cleans `role_menu` bindings.

## Impact

- `lumen-system`: `MenuController` (+3 endpoints), new `MenuService` (or extension of
  `MenuTreeService`) + `MenuInfoMapper`/`RoleMenuMapper` use, new request DTOs
  (`CreateMenuRequest`/`UpdateMenuRequest`).
- Spec sync on archive: `openspec/specs/api/spec.md` (menu family), `openspec/specs/auth-rbac/spec.md`
  (lifecycle requirement).
- Shared design source: `openspec/coordination/menu-management.md` (cross-stack parent;
  the frontend change `menu-management-web` builds against these endpoints).
- Existing seeds/tests untouched; no DB migration needed (the model is unchanged).
