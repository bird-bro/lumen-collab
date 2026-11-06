# Coordination: menu-management

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "menu-management"`.

## Why

The console menu page is currently a read-only tree (`web-system-console` § Menu Tree
Visibility) and the backend exposes only `GET /api/v1/system/menus/tree`. Menu structure is
owned by Flyway seeds, so every IA adjustment needs a migration. The menu model
(`menu_info`, M/C/F types, `perms`) is the permission backbone (role_menu grants + login/me
perms set + server-side `@PreAuthorize` page gates), and the button-level (F-node) half was
left awaiting materialization. This feature turns the page into full menu management:
node CRUD for directories/menus/buttons including button-permission authoring.

## Shared Design

Single source of truth for both stacks. The existing read endpoint stays untouched:
`GET /api/v1/system/menus/tree` (class-gated `@PreAuthorize("hasAuthority('system:menu:list')")`,
`MenuTree` POJO contract: mid/upId/type/title/perms/path/component/icon/sort/visible,
assembled by `MenuTreeService` with `ORDER BY sort,id` and TreeUtils orphan-drop).

### New endpoints (lumen-system, `/api/v1/system/menus/**`)

Same class-level gate as the tree (`system:menu:list` governs the whole family - the
established role/user/org pattern; no separate button-level backend enforcement).

| Method | Path | Body | Returns | Notes |
|:--|:--|:--|:--|:--|
| POST | `/api/v1/system/menus` | CreateMenuRequest | Integer (new id) | auto-grants the admin role |
| PUT | `/api/v1/system/menus/{id}` | UpdateMenuRequest | Boolean | full field update incl. move (upId) |
| DELETE | `/api/v1/system/menus/{id}` | - | Boolean | guarded soft delete, see semantics |

Request fields (both): `upId` (0 = top), `type` (`M`/`C`/`F`), `title` (required),
`perms`, `path`, `component`, `icon`, `sort`, `visible` (1/0). `perms` free text, no
format validation - convention `模块:资源:动作` (随访 recipe; the strings surface in the
login/me perms set and gate buttons/frontend `v-perm` + server endpoints that adopt them).

### Delete semantics

- Live children exist → reject with an `A`-class business error (`A0400`-style, descriptive
  `msg`); no cascade. Admin cleans the subtree first.
- Otherwise: soft-delete the row (`@TableLogic`) AND soft-clean `role_menu` bindings to the
  deleted id (same discipline as `RoleServiceImpl.delete` cascading to its bindings).
- Orphan safety is inherent: TreeUtils drops nodes whose live parent chain is broken, and a
  deleted leaf leaves no orphans.

### Move/cycle guard

`PUT` with a new `upId` must reject `upId = id` and any `upId` inside the node's own
descendant subtree (walk down from the candidate parent) - a cycle would corrupt the tree
and silently orphan the moved node via the TreeUtils drop.

### Structural guards

- Parent must be a live node; `F` nodes are leaves - creating/moving a child under an `F`
  parent is rejected.
- Create/update of any node auto-grants it to the admin role (role 1) on CREATE only:
  lumen keeps explicit `role_menu` grants (no `*:*:*` wildcard), so an ungranted new node
  would be invisible to the super admin. UPDATE never touches grants.
- Type is immutable-after-create is NOT enforced (type edits allowed; a `C`↔`M` flip is a
  plain field update) - keep the guard surface minimal.

### Out of scope (recorded decisions)

- Workstation mode: NO menu-model change. Workstation stays the session-level activation
  (typed enum + user primary/multi-station + `/me` payload + client-side refilter) tracked
  by its own future change; the menu tree only gains nodes via normal CRUD when department
  stage directories land (V10 placeholders stay disabled). 2026-11-05 user ruling.
- No drag-and-drop ordering; `sort` is a plain number field edited in the form.
- No perms format validation, no perms uniqueness constraint.
- `path`/`component` stay free text (frontend-registered-route mismatch is the admin's
  responsibility; a curated picker is a later UX change).

## Decisions

- Workstation mode does not enter this change; menu model unchanged - 2026-11-05.
- F-node perms authored as free text with the `模块:资源:动作` convention; server-side stays
  page-level (`system:menu:list`) - 2026-11-05.
- Delete = children-blocked + bindings soft-cleaned; create auto-grants admin role - 2026-11-05.
- Backend first; the frontend page (existing-page edit, prototype skipped per workflow)
  builds against the real API through the vite proxy - standing ruling 2026-11-05.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | menu-management-api | **archived 2026-11-06** | PASS (8/8, L1/L3 PASS, 82/82) |
| frontend | menu-management-web | **archived 2026-11-06** | PASS (4/4, L1/L3 PASS, 174/174) |

**Feature closed 2026-11-06** — all registered changes verify PASS and archived;
`web-system-console` main spec now carries § Menu Management (read-only
Menu Tree Visibility retired). Post-archive reviewer fix: backend test hygiene
(runAdminLoginBody migration + fixture prefixes registered, shared DB swept clean).

## Gate

Feature done = ALL registered changes verify PASS -> archive each.
