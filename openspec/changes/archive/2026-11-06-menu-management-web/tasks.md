# Tasks: menu-management-web

## 1. API client extension (red → green)

- [x] 1.1 Add `CreateMenuRequest`/`UpdateMenuRequest` to `src/api/types.ts` and `createMenu`/`updateMenu`/`removeMenu` to `src/api/menus.ts` typed from the api delta (POST `/api/v1/system/menus`, PUT/DELETE `/{id}`) - verify: existing `api-clients.test.ts` contract cases extended and passing (`pnpm test`).
- [x] 1.2 Confirm `menuTree` and `MenuNode` are untouched (additive only) - verify: tsc clean (`pnpm build` type pass) and prior client tests unchanged-green.

## 2. Menu page rewrite (red → green)

- [x] 2.1 Failing render tests: tree table shows 标题/类型 tag/`perms`/排序/可见 per node (supersedes the old read-only `Tree` assertions) - verify: rewritten `menus-page.test.tsx` red, then green once the `Table` treeData render lands.
- [x] 2.2 Create/edit dialog with type-aware fields (F → `perms` with `模块:资源:动作` hint; C/M → `path`/`component`; parent TreeSelect, `icon`/`sort`/`visible` common) and edit pre-fill from the row - verify: dialog tests pass (field visibility per type, pre-fill).
- [x] 2.3 Parent-choice guards in the TreeSelect: no `F` nodes offered; when editing, the node and its descendants excluded - verify: test constructs a tree and asserts option exclusion.
- [x] 2.4 CRUD wiring: 新增下级/编辑 call POST/PUT then refresh the tree; 删除 `Popconfirm` calls DELETE then refreshes; `ApiError.msg` rejection (live children) surfaces via the message API and the tree is unchanged - verify: mocked-seam tests pass.
- [x] 2.5 `usePerm('system:menu:list')` hides the action column when absent - verify: test with an empty perm set renders no row actions.

## 3. Full verification

- [x] 3.1 Unit suite + type check - verify: `pnpm test` and `pnpm build` pass in `lumen-web/`.
- [x] 3.2 Real-backend walkthrough with `menu-management-api` deployed (`pnpm dev`, vite proxy): create a button node under a role menu, see it in the tree and in 角色管理's assignment tree; delete a parent with children and see the rejection msg - verify: observable behavior matches every WHEN/THEN of the `web-system-console` delta.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/web-system-console/spec.md` at archive time; gate = both registered changes verify PASS (see `openspec/coordination/menu-management.md`).
- Backend-first sequencing: start this change's real-backend steps only after `menu-management-api` lands.
