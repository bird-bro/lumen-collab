# Design: menu-management-web

## Context

The page (`src/pages/system/menus/index.tsx`) currently renders an antd `Tree` of
`menuTree()` inside the shared `ConsolePanel` with load/error handling and type/perms
tags. The backend family (`menu-management-api`) adds POST/PUT/DELETE on the same
`system:menu:list` gate. Console pages are table-first (users/roles/dicts/logs), the
`usePerm()` hook gates affordances client-side (roles-page pattern), and there is no
mock layer - requests ride the vite proxy to the real backend.

## Goals / Non-Goals

**Goals:**
- One page, one tree table, one create/edit dialog - smallest surface that covers the
  spec scenarios (tree render, CRUD round-trip incl. button nodes, rejection surfacing,
  parent-choice guards).

**Non-Goals:**
- No workstation UI (recorded decision), no icon picker (plain text input, optional),
  no drag ordering (`sort` is a number field), no page prototype step (existing-page
  edit), no changes to shared components, router, or other pages.

## Decisions

- **D1 - `Table` with `treeData`, not the `Tree` component.** Row actions need a stable
  per-node affordance column (新增下级/编辑/删除); `Table` gives that plus the console's
  table idiom. The node title cell keeps the existing type tag + monospace perms tag.
  `defaultExpandAll` on first load; children inline via `Table`'s tree mode.
- **D2 - One dialog, type-aware fields.** `类型` radio drives visibility: 按钮 F → 标题 +
  `perms` (hinted `模块:资源:动作`); 菜单 C / 目录 M → 标题 + `path`/`component`
  (optional for M, mirroring V5 seeds where groups have `path = NULL`) + `icon`/`sort`/
  `visible` always. Parent `TreeSelect` builds from the loaded tree minus `F` nodes and
  (when editing) minus the node's own subtree; edit mode pre-fills from the row.
- **D3 - API client is additive**: `createMenu`/`updateMenu`/`removeMenu` join
  `src/api/menus.ts`; request types land in `src/api/types.ts` next to `MenuNode`
  (`CreateMenuRequest`/`UpdateMenuRequest` per the api delta). Existing `menuTree` and
  types untouched.
- **D4 - Rejections ride the existing error path**: `ApiError.msg` → `messageApi.error`
  (same as every console page); delete confirms via `Popconfirm` before the call.
- **D5 - Affordance gating with `usePerm('system:menu:list')`**: when absent, the action
  column renders nothing - the page itself is still route-gated by the perms set and the
  server remains authoritative (roles-page symmetry).
- **D6 - Tests stub at the `vi.mock('@/api/menus')` seam**: rewrite
  `menus-page.test.tsx` - render case (types + perms visible), create/edit round-trip
  calls + tree refresh, delete confirm + rejection `msg` passthrough, parent-select
  guards (no `F` parents; no self/descendant when editing). Mirrors the roles-page test
  structure.

## Risks / Trade-offs

- [Tree table density with deep nesting] → console trees are shallow (≤4 levels by IA
  ruling); indentation + expand controls are native to `Table` tree mode.
- [Page rewrite touches the only menu surface] → the file is self-contained (no shared
  component edits) and the test rewrite covers the previous read-only scenario (tree +
  perms visible), so no regression hole opens.
- [Free-text `path`/`component` can 404] → accepted trade-off recorded in the
  coordination doc; a curated route picker is a later UX change.

## Migration Plan

Single-page swap behind the existing route; deploy with the backend family live
(backend-first). Rollback = revert the page commit; no data or contract change.

## Open Questions

(none - scope rulings recorded in `openspec/coordination/menu-management.md`)
