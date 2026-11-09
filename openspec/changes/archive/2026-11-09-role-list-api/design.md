# Design: role-list-api

## Context

`RoleController` (auth-rbac-foundation task 4.4) serves POST `/{id}` CRUD + PUT `/{id}/menus`
under a class-level `system:role:list` page gate; the paged list was specced as a gap
(api spec 2026-11-03) and the frontend (`roles.ts`, `/system/roles` page) is already
built against the typed contract: `Page<RoleItem>` with optional row-level `menuIds`.
The 随访 (fu) system is the user-designated reference (2026-11-09): its
`RoleController` pages via `like(name)`/`eq(type)`/`orderByAsc(rid)` returning
`IPage<RoleInfoEntity>` directly, and prefills the grant dialog from a separate
`GET /menu/{rid}`. `MenuManageService` is the recorded 寄住 form (user ruling
2026-11-06): menu-node lifecycle + tree guards + the role_menu binding logic
(auto-grant admin, binding cleanup) that belongs to the role domain.

## Goals / Non-Goals

**Goals:**
- Close the list gap to the delta contract (IPage + `menuIds` echo + name/type filters).
- Complete role-domain ownership of `role_menu` writes; dissolve MenuManageService.

**Non-Goals:**
- No frontend change (query-bar server-side wiring is a follow-up web change).
- No schema/migration, no new error codes, no enable/dataScope columns (随访 has them;
  lumen's `role` table deliberately does not - auth-rbac-foundation data model stands).

## Decisions

**D1 - GET with query DTO (not 随访's POST /page).** Lumen's typed client and the
Gateway Path Convention fixed `GET /system/roles/list` long ago; 随访's POST-page is its
own house style. Port the filtering/ordering pattern, keep the verb: a small
`RoleListQuery extends PageSizeConfig` (fields `name`, `type`) mirrors the copied
`OrgDepPageQuery` convention. Alternative rejected: bare `@RequestParam`s - loses the
copied pagination-defaults convention.

**D2 - Rows are `RoleItemDto`, echo assembled batched.** The entity stays clean (no
`@TableField(exist=false)` projection field); `RoleItemDto` carries the 1:1 role fields
(`BeanUtil.copyProperties(request, dto, true)` house idiom) plus explicitly-set
`menuIds`. Echo = one `selectList(role_id IN <page ids>)` on `role_menu` (live rows via
`@TableLogic`), grouped by role, ids sorted ascending. Console-scale data; one extra
query per page (same tradeoff the menu tree's single-map read already accepts).
Alternative rejected: `GET /roles/{id}/menus` per-role prefill (随访's shape) - breaks
the typed contract and the 授权数 column, needs a web change (D3).

**D3 - Grant echo on list rows (deliberate 随访 divergence).** User decision 2026-11-09
(brainstorm): echo on rows, not a separate prefill endpoint. Rationale: the frontend
contract and page were built to row-level `menuIds`; the 授权数 column needs it per row;
zero frontend change. 随访's separate `GET /menu/{rid}` stays documented as the
alternative.

**D4 - MenuManageService dissolution split.** Per the 2026-11-06 ruling ("开发 role 功能
时整类并入 role 相关类；菜单树守卫的最终归属在该功能提案时定"):
- `role_menu` binding ops move into the RoleService family: `RoleService` gains
  `grantNewMenuToAdmin(Integer menuId)` (NOT-EXISTS-guarded auto-grant to role 1) and
  `unbindMenu(Integer menuId)` (logical-delete the menu's `role_menu` rows).
  RoleServiceImpl already owns the family's other `role_menu` writes (assignMenus,
  delete cascade) - this completes the ownership (随访 parity: `IRoleInfoService` owns
  `grantMenu`).
- Menu-node lifecycle + tree guards stay menu-domain: the class is renamed
  `MenuService` (top-level concrete business service, service-package-tidy shape),
  guards stay private inside it (the single live-map read serves parent validation,
  cycle walk and child check with zero extra queries - splitting them into
  `service/support/` breaks that design). Its transactions call the role-family binding
  ops. The ruling's Why ("授权/绑定本质是角色域逻辑") is satisfied; guards guard
  `menu_info` structure, not grants, so menu-domain is their natural home.
- `MenuController` re-points (field type swap; no public signature changes -
  never-modify rule untouched).

**D5 - Naming.md precedent swap.** The service-package-layout entry cites
MenuManageService as the concrete-class example: swap the example to `MenuService` and
record that `role_menu` binding writes live in the RoleService family (ruling's 换例
requirement).

**D6 - Ordering and filters.** `orderByAsc(id)`, name = LIKE contains, type = EQ -
verbatim 随访 `RoleInfoServiceImpl.getPage` semantics (user reference direction).

## Risks / Trade-offs

- [Service rename touches menu write tests] → `AuthMenuManageTests` exercises HTTP URLs,
  not class names; behavior is unchanged, so they must stay green unmodified (or move
  only if they import the class).
- [Row echo adds a query per page load] → batched single IN query, console-scale data;
  acceptable (same tradeoff as the tree single-map read).
- [PageSizeConfig binding/validation quirks] → copy the `OrgDepPageQuery` usage exactly
  (same base class, same binding path).

## Migration Plan

No DDL, no config. Deploy = rebuild + restart `lumen-system`. Rollback = redeploy prior
jar (the new endpoint is additive; the service rename is internal).

## Open Questions

(none - both scope questions were answered in brainstorm 2026-11-09: echo on rows;
merge folded into this change)
