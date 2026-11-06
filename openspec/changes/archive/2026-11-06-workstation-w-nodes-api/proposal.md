# Proposal: workstation-w-nodes-api

## Why

The archived `workstation-context-api` granted workstations DIRECTLY to users
(`user_workstation` table + per-user assignment endpoint) — the user ruled this
completely wrong (2026-11-06): **menu permissions flow only through roles** (role_menu
→ role → user binding), never directly to users. The workstation itself should be a
**menu node** — type `W` at L1 level — whose access is governed by the existing
role_menu mechanism. Zero new auth machinery.

## What Changes

- **Add migration `V22__workstation_w_nodes.sql`**:
  - `DROP TABLE IF EXISTS user_workstation` (the wrong direct-grant table);
  - delete the `system:user:assign-ws` F node + its role_menu grants (the per-user
    assignment button — no longer exists);
  - `ALTER TABLE menu_info DROP COLUMN IF EXISTS workstation` (the V21 root-marker
    column, superseded by the W node type);
  - delete the V21 placeholder L1s (放射科/超声科/内镜科 id 298-300, marker column
    gone) and seed three **type W** nodes — 放射工作站/超声工作站/内镜工作站 (up_id=0,
    admin auto-grant, V15 content-guard discipline);
- **Remove the per-user assignment surface**: `PUT /users/{id}/workstations` endpoint,
  `WorkstationService`, `UserWorkstation` entity/mapper, `AssignWorkstationsRequest`
  DTO, and the login/me `workstations`/`primaryWorkstation` payload fields (the menu
  tree already carries the W nodes — the frontend derives stations from it).
- **CreateMenuRequest/UpdateMenuRequest** `@Pattern` gains `W` (workstations are
  creatable in the menu management page per the user's ruling: 种子预置 + 页面可建).
- No other endpoint/payload changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `auth-rbac`: the ADDED "Workstation Authorization" requirement is REPLACED — the
  workstation is a type-W menu node granted through the existing role_menu flow (no
  user_workstation table, no per-user grant, no assign-ws button); V22 tears the wrong
  machinery down and seeds the W nodes.
- `api`: the ADDED "Workstation Context Endpoints" requirement is REPLACED — no
  assignment endpoint; login/me payloads revert; the menu tree carries W nodes.

## Impact

- `lumen-system`: V22 migration; UserInfoController/WorkstationService/entity/mapper/
  DTO/payload-field removals (all introduced by the archived workstation-context-api —
  a net simplification); menu form validation gains W.
- Spec re-sync on archive: `api` + `auth-rbac` (REPLACES the two workstation
  requirements synced from the archived change).
- Frontend: `workstation-context-web` (still in proposing after rewind) absorbs the
  correction — chip/append survive, data source becomes the tree's W nodes; the
  users-page assignment drawer is dropped.
