# Proposal: workstation-context-api

## Why

The three-layer orthogonal assembly (patient-domain decision #8) is two thirds done -
role_menu and deployment are in, the workstation layer is not. Department pages cannot
open without a session-level activation station: a user authorized for several
departments would see every department's pages at once. This change lands the
workstation data model, the /me + login payload extension, and the assignment endpoint
(the frontend change `workstation-context-web` builds the chip + filtering on it).

## What Changes

- **Add migration `V21__user_workstation.sql`**: table `system.user_workstation`
  (`user_id` FK user_info, `workstation` varchar(16) typed enum admin/radiology/us/endo,
  `is_default` 1/0, EntityBase audit block) with a partial unique index allowing one
  live `is_default=1` row per user; the new F node `system:user:assign-ws`
  (content-guarded insert + admin auto-grant, the V15 discipline); AND the menu-tree
  station roots — `menu_info` gains a nullable `workstation` column set ONLY on the
  four L1 roots (existing 系统管理 root = `admin`; new 放射科/超声科/内镜科 placeholder
  roots), so a child's station = its ancestor root's value (the tree is the mapping).
- **Menu-tree nodes carry the marker**: the Menu Tree Endpoint's nodes include the
  nullable `workstation` field (root-level enum) so the frontend filters by root
  grouping without any path-prefix rules.
- **Add `PUT /api/v1/system/users/{id}/workstations`** (full-replace grant, mirroring
  the role-menu assignment pattern), pinned by `system:user:assign-ws`.
- **Extend login/me payloads**: `workstations: string[]` (the user's live stations,
  `["admin"]` when the user holds none - bootstrap semantics, no backfill) and
  `profile.primaryWorkstation` (the `is_default=1` row, else `admin`).
- No changes to existing endpoints' shapes beyond the payload extension and the
  tree-node `workstation` marker (see the coordination doc's revised switching
  semantics: workstation = menu-tree L1 grouping, user ruling 2026-11-06).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: ADDED requirement - the workstation assignment endpoint and the login/me
  payload extension (`workstations` + `profile.primaryWorkstation`).
- `auth-rbac`: ADDED requirement - the workstation authorization model
  (`user_workstation`, typed enum, default semantics, bootstrap fallback, assignment
  enforcement).

## Impact

- `lumen-system`: one migration, one new endpoint (+pin), AuthServiceImpl payload
  assembly, MeResponse/LoginResponse/ProfileDto field additions.
- Spec sync on archive: `openspec/specs/api/spec.md`, `openspec/specs/auth-rbac/spec.md`.
- Cross-stack: `workstation-context-web` consumes the payloads and adds the chip/
  filtering/assignment UI (see `openspec/coordination/workstation-context.md`).
