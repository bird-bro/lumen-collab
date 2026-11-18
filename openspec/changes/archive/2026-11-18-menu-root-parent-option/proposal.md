# Proposal — menu-root-parent-option

## Why

Bug report 2026-11-13: on `/system/menus`, adding a menu offers no 根目录 (root)
choice, so a first-level menu cannot be added explicitly. Concretely:

1. The toolbar 添加 button (`openCreate(null)`) opens the drawer with the
   上级菜单 field hidden entirely — the create body hardcodes `upId: 0`, but the
   user sees no parent selector at all and cannot tell (or control) that the
   node lands at top level.
2. In edit mode the parent TreeSelect only lists existing non-button nodes —
   there is no 根目录 option, so a top-level node displays the raw value `0` and
   a node can never be moved back to top level.

The backend contract already defines `upId = 0` as top level
(`MenuService.create`, "upId = 0" = top level) and accepts it on create/update —
no backend change needed.

## What Changes

- **lumen-web only** (`src/pages/system/menus/index.tsx` + its test):
  - `buildParentOptions` prepends a `{ title: '根目录', value: 0 }` root option
    (never excluded, never a subtree member).
  - The 上级菜单 TreeSelect renders in BOTH create paths (toolbar 添加 and row
    添加子菜单) and in edit — one unconditional form item replaces the two
    conditional ones.
  - Toolbar 添加 prefills `upId: 0` (根目录 selected); row 添加子菜单 keeps
    prefilling the clicked node.
  - Submit drops the hardcoded-0 special case — `upId` always comes from the
    (now always-present) form field.
- Existing-page edit → no UI prototype (per workflow); user confirms visuals on
  the real backend.

## Affected Stacks

- single-stack: lumen-web

## Depends-On

- none - standalone

## Scope

- In scope: menu page parent-selector behavior (create + edit), tests.
- Out of scope: backend `upId` handling (already correct), other tree pages
  (org/dept), menu tree API shape.

## Success Criteria

- Toolbar 添加 → drawer shows 上级菜单 prefilled 根目录; saving calls
  `createMenu` with `upId: 0`.
- Row 添加子菜单 → drawer shows 上级菜单 prefilled with the clicked node; the
  dropdown additionally offers 根目录.
- Edit a top-level node → the selector displays 根目录 (not `0`); the dropdown
  offers 根目录 so a node can be moved back to top level.
- Button-node / own-subtree parent guards unchanged; existing tests stay green.

## Constraints

- No new API contract, no schema change (bypass tier: single lightweight
  proposal, lumen-web only).
- Spec `web-system-console` § Menu Management wording stays satisfied: parent
  choices still exclude `F` nodes and (when editing) the node's own subtree —
  根目录 is an addition, not a relaxation.

## Risks

- 根目录 selectable together with type 按钮 → backend permits an `F` node at top
  level today (no parent check for `upId = 0`); this matches the pre-existing
  create-path freedom and is left to a later ruling if undesired.
  → Mitigation: none needed now; behavior identical to the hardcoded path.
