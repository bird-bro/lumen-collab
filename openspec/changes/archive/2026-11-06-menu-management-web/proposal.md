# Proposal: menu-management-web

## Why

The console menu page (`/system/menus`) is a read-only tree; menu structure could only be
shaped by backend seeds. The backend change `menu-management-api` (see
`openspec/coordination/menu-management.md`) lands the create/update/delete family, so the
page can become the management surface - including authoring the button (F-node)
permission strings that role grants and client/server gates consume.

## What Changes

- **Upgrade the existing menu page from read-only tree to management table-tree** (antd
  `Table` with `treeData`): columns for 标题, 类型 tag (目录/菜单/按钮), `perms`
  (monospace), 排序, 可见, and row actions 新增下级 / 编辑 / 删除 - replacing the plain
  `Tree` render while keeping the `ConsolePanel` shell and load/error flow.
- **Create/edit dialog** with type-aware fields: 类型 radio (目录 M / 菜单 C / 按钮 F),
  上级节点 TreeSelect (excludes the node itself + its descendants when editing, and
  never offers `F` nodes - buttons are leaves), 标题, `perms` (free text; shown for
  buttons with the `模块:资源:动作` hint), `path`/`component` (menus/directories),
  `icon`, `sort`, `visible` switch.
- **Delete affordance** with confirm; backend rejections (live children, etc.) surface
  the `A`-class `msg` via the page's message API.
- **API client grows additively**: `src/api/menus.ts` gains `createMenu`/`updateMenu`/
  `removeMenu` typed from the api spec delta - no mock layer; requests go through the
  vite proxy to the real backend (standing ruling).
- **Client-side affordance gating** via the existing `usePerm()` hook
  (`system:menu:list`), matching the roles-page pattern; the server gate remains
  authoritative.
- Workstation mode: no UI - recorded decision, workstation stays its own future change.
- Existing-page edit: the `frontend-design` prototype step is skipped per workflow.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-system-console`: § Menu Tree Visibility (read-only view) is superseded by a full
  menu-management requirement - tree render with perms visible stays, CRUD round-trips
  and button-perm authoring are added, and backend rejections surface on the page.

## Impact

- `lumen-web`: `src/pages/system/menus/index.tsx` (page rewrite in place),
  `src/api/menus.ts` (+3 functions), `src/api/types.ts` (+ request types),
  `src/test/menus-page.test.tsx` (rewrite: CRUD round-trip + rejection passthrough cases,
  stubbing at the `vi.mock('@/api/menus')` seam).
- Depends on the `menu-management-api` endpoints being live (backend-first; `pnpm dev`
  proxy precondition).
- Spec sync on archive: `openspec/specs/web-system-console/spec.md`.
