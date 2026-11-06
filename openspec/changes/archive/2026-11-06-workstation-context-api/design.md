# Design: workstation-context-api

## Context

The permission backbone is complete (role_menu grants, /me perms, server-side pins at
page AND button level). `user_info` carries no workstation data; `MeResponse` is
profile/menus/perms. The frontend (workstation-context-web) needs: the effective
station list, the default, and an assignment surface (see the coordination doc's
shared design). See proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- Typed, FK-backed grant table with default uniqueness; payloads extended in place;
  one pinned assignment endpoint; seed for the new button perm.

**Non-Goals:**
- No workstation field on menu_info (client-side filtering by route domains - the
  recorded decision); no department page routes; no `remote` station (v2); no Nacos/
  config-service involvement; no changes to existing endpoint shapes beyond payload
  additions.

## Decisions

- **D1 - Separate grant table, not a column.** `user_workstation(user_id, workstation,
  is_default)` - one user holds several stations (a column list would need CSV parsing
  and can't carry per-row defaults cleanly); mirrors the `user_role`/`role_menu`
  association-table lineage. Partial unique index: one live `is_default=1` per user
  (the role_menu partial-unique precedent).
- **D2 - Bootstrap fallback in the payload assembler, not the DB.** Zero live rows ⇒
  `workstations = ["admin"]`, `primaryWorkstation = "admin"` - computed where the
  payloads are assembled (AuthServiceImpl), no migration backfill, existing accounts
  untouched.
- **D3 - Full-replace assignment mirrors role-menu.** `PUT /users/{id}/workstations`
  takes `{workstations: [{workstation, isDefault}]}`, soft-deletes the user's live rows
  and inserts the posted set in one transaction; rejects two defaults with A0400
  (service-side) behind the partial unique backstop. Pinned `system:user:assign-ws`.
- **D4 - The enum lives in code + a DB CHECK-less varchar.** A `varchar(16)` column
  with service-side validation against the enum (Spring `@Pattern` on the request
  DTO) - a DB CHECK would need a migration per future station; the closed set is
  enforced where requests enter.
- **D4a - Station ownership on L1: `menu_info.workstation` (nullable), L1-level
  only.** (Two-step user refinement 2026-11-06: workstation sits ONE LEVEL ABOVE L1 —
  realized as an OWNERSHIP FIELD + grouped rendering in the 菜单管理 page, NOT as fake
  tree nodes: no tree re-parenting migration, the role_menu ancestor-chain discipline
  stays untouched, and no un-assignable "shell" rows appear.) V21 marks the existing
  系统管理 L1 `admin` and seeds 放射科/超声科/内镜科 placeholder L1s (type M, no
  children) with their codes; a child's station = its ancestor L1's value. The
  management page renders group header rows per station (rendering grouping, not
  menu_info nodes) and the L1 form carries a fixed 4-value station dropdown
  (@Pattern-validated server-side). `MenuTreeService.toTreeNode` maps the new column
  additively. The frontend never derives stations from paths.
- **D5 - Payload assembly is one query.** The assembler loads the user's live
  workstation rows (join user_id, @TableLogic filters) once per login/me - the same
  shape as `permissionSet(uid)`; added to `MeResponse` and the login response beside
  the existing fields (additive fields only).
- **D6 - V21 seed follows the V15 content-guard discipline.** The new F node
  (`system:user:assign-ws` under 用户管理) inserts behind `NOT EXISTS (up_id, type,
  perms)` among live rows; admin auto-grant behind the NOT-EXISTS live-binding guard;
  no explicit ids (the V15 lesson), no setval needed. V21 also: `ALTER TABLE menu_info
  ADD COLUMN IF NOT EXISTS workstation varchar(16)`; mark 系统管理 (id 1)
  `workstation='admin'` (guarded UPDATE); seed the three placeholder L1s (content-
  guarded INSERT by up_id=0 + title + workstation marker; admin auto-grant each).

## Risks / Trade-offs

- [Enum drift between DB values and frontend unions] → the closed set is quoted in the
  coordination doc and typed on both sides; a drift fails the frontend type check.
- [Assignment while the target user is logged in] → their session payload solidified
  at login (the established semantics, same as role re-grants); takes effect on next
  login/me - acceptable for a console surface.
- [User delete cascade] → NOT NEEDED (apply-time correction 2026-11-06): the users
  family has no delete endpoint (api spec: create/password/enable/list only), so there
  is no cascade to write; if a delete endpoint lands later, the workstation-row
  cleanup rides it (the role-delete cascade shape).

## Migration Plan

Deploy = rebuild `lumen-system`; Flyway applies V21 on boot (guarded statements,
hand-appliable on the shared test server). Rollback = redeploy the previous artifact;
the new table is additive and harmless to an older build.

## Open Questions

(none - scope pinned by the 2026-11-06 user ruling: full stack incl. assignment UI)
