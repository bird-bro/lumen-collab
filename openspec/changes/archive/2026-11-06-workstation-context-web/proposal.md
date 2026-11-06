# Proposal: workstation-context-web

## Why

The backend `workstation-w-nodes-api` lands the corrected workstation model: the
workstation IS a menu node (`menu_info.type = 'W'`, L1 level) granted through the
existing role_menu flow — never directly to users. This change builds the frontend
half: the header chip derives the station list from the granted tree's W nodes, the
sidebar APPEND semantics render the active station's pages beside 系统管理, and the
roles-page assignment tree covers W nodes automatically (no per-user assignment
surface — the earlier drawer design is dropped per the user ruling 2026-11-06).

## What Changes

- **`src/config/workstations.ts` re-scoped**: no closed enum registry — the station
  list derives from the menu tree (`menus.filter(type === 'W')`); the module keeps
  the APPEND filter (admin-domain L1 menus resident + the active W node's children
  beside them; 控制台 = today's sidebar) and station helpers.
- **Header chip** (zone 2, title-left, the confirmed 定式): lists the granted W nodes
  (title/icon from the node, check on the active); switching stores the W node id
  (auth store `activeWorkstation: number | null`) and reroutes to the station's first
  leaf. Session-scoped: re-login returns to the console view (the primary-workstation
  concept is dropped with the wrong machinery — session-only selection).
- **Menu management**: type radio gains 工作站 (W — per the ruling, stations are
  creatable in the page); the type tag/labels carry W; the tree renders the W nodes
  as L1 entries.
- **Roles-page 菜单授权 tree**: W nodes render with a 工作站 tag automatically (grant
  flow unchanged — the existing full-replace covers them).
- **DROPPED**: the users-page workstation assignment drawer (per-user direct grant)
  and its API client/types — the earlier design is superseded.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-app-shell`: the ADDED chip/filter requirement is REVISED — station list
  derives from the granted tree's W nodes (not a payload array); APPEND semantics
  unchanged; re-login returns to the console view (no stored primary).
- `web-system-console`: the assignment-drawer requirement is DROPPED (REMOVED in the
  delta) — workstation grants happen in 角色管理's menu assignment (W nodes in the
  tree); the menu management type option gains 工作站.

## Impact

- `lumen-web`: workstations.ts (rewrite), auth store (activeWorkstation: number|null),
  BasicLayout/WorkstationChip (tree-derived), menus page (type W option/labels),
  roles page (W tag), users page (drawer removed), types/auth/users API (assignment
  bits removed), tests across all touched files.
- Depends on `workstation-w-nodes-api` (backend-first).
- Spec sync on archive: `web-app-shell` + `web-system-console`.
