# Tasks: role-list-api

## 1. Red - role list contract tests (TDD)

- [x] 1.1 Add `AuthRoleListTests` (extend `AuthIntegrationTestBase`): seed roles + role_menu rows; assert `GET /api/v1/system/roles/list` returns envelope `code:"200"` with IPage shape (`records/total/size/current/pages`), rows carrying role fields plus `menuIds` echo (empty array for an ungranted role), ascending id order
- [x] 1.2 Same test class: name filter (substring) and type filter (equality) scenarios; confirm tests FAIL against the current code (405 today)

## 2. Green - list endpoint

- [x] 2.1 Add `RoleListQuery extends PageSizeConfig` (`name`, `type`) and `RoleItemDto` (role fields + `menuIds`); `RoleService.page(RoleListQuery)` + impl: `like`/`eq`/`orderByAsc(id)` page query, batched `role_menu` echo assembly grouped by role id
- [x] 2.2 `RoleController`: `GET /list` returning `IPage<RoleItemDto>` (class-level `system:role:list` gate applies); rerun 1.x tests green

## 3. MenuManageService dissolution (2026-11-06 ruling)

- [x] 3.1 Extract binding ops into the role family: `RoleService.grantNewMenuToAdmin(menuId)` (NOT-EXISTS-guarded auto-grant to role 1) + `RoleService.unbindMenu(menuId)` (logical-delete the menu's `role_menu` rows), implementations in `RoleServiceImpl`
- [x] 3.2 Rename `MenuManageService` → `MenuService` (menu-domain lifecycle + guards stay private, single live-map design intact), call the role-family binding ops inside its transactions; re-point `MenuController` (field type swap only)
- [x] 3.3 `AuthMenuManageTests` stay green unmodified (URL-level behavior unchanged); full `lumen-system` test run green

## 4. Convention + verification

- [x] 4.1 `.claude/rules/naming.md`: swap the service-package-layout precedent example MenuManageService → MenuService; record that `role_menu` binding writes live in the RoleService family
- [x] 4.2 `/opsx:verify`: L1 build + L2 WHEN/THEN (delta scenarios) + L3 full test evidence; manual smoke: `/system/roles` page loads against the real backend (no 405), grant dialog prefill shows the saved set
