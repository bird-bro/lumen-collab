# Proposal: role-list-api

## Why

The `/system/roles` console page 405s on load: `GET /api/v1/system/roles/list` is the api
spec's declared gap endpoint (surfaced 2026-11-03) and was never implemented in
`RoleController`. Since the mock layer was removed (user ruling 2026-11-05,
`frontend-backend-first`), the page hits the real backend, where Spring matches
`/roles/list` against the `/{id}` PUT/DELETE mappings and rejects the GET with 405. The
synced api spec anticipates exactly this backend follow-up change. User direction
2026-11-09: take the standalone 随访 (fu) system's role page/endpoint family
(`/system/account/role` → `fu_sev` `RoleController` + `fu_web` `views/system/role`) as the
design reference.

## What Changes

- Implement `GET /api/v1/system/roles/list`: IPage-wrapped page query with
  `pageNum`/`pageSize` (PageSizeConfig defaults 1/20), optional `name` (LIKE) and `type`
  (EQ) filters, `id`-ascending order - the 随访 `POST /api/manage/role/page` pattern
  ported to lumen's GET convention (随访 parity, 2026-11-09 reference direction).
- Each list row carries the `menuIds` grant echo (the role's live `role_menu` ids) -
  user decision 2026-11-09: echo on list rows, deliberately diverging from 随访's
  separate `GET /menu/{rid}` prefill endpoint, because the already-typed frontend
  contract (`RoleItem.menuIds`) and the page's 授权数 column consume row-level data.
- Dissolve the `MenuManageService` 寄住 form (user ruling 2026-11-06, binds to role
  feature development): the `role_menu` binding logic (auto-grant admin on node create,
  binding cleanup on node delete) moves into the RoleService family; menu-node lifecycle
  and tree guards stay menu-domain as `MenuService`; `MenuController` re-points.
  `.claude/rules/naming.md` precedent example swapped accordingly.
- No data migration, no new error codes, no frontend change (client `roles.ts` + page
  are already built to this contract; tests mock at the api-client seam).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: Role Management Endpoints - the declared list gap becomes the implemented
  contract (IPage shape, `menuIds` grant echo, name/type filters, id-asc order); the
  "NOT yet implemented" gap paragraph is replaced.
- `web-system-console`: Role Management and Menu Assignment - the "(mocked until the
  backend implements it)" qualifier is dropped; the page is backed by the real endpoint.

## Impact

- `lumen-sev/lumen-system`: `RoleController` (+1 GET endpoint), `RoleService`/
  `RoleServiceImpl` (page query with echo assembly; gains the binding operations), new
  `RoleItemDto`, `MenuManageService` dissolved → `MenuService` (menu-domain lifecycle +
  guards) with binding ops extracted to the role family, `MenuController` re-point.
- Tests: new role-list contract tests (TDD); `AuthMenuManageTests` must stay green
  through the service reorganization (URL-level behavior unchanged).
- `.claude/rules/naming.md`: service-package-layout precedent swap
  (MenuManageService → MenuService as the concrete-class example; RoleService family
  owns role_menu binding writes).
- `openspec/specs/api/spec.md` + `openspec/specs/web-system-console/spec.md`: synced
  from this change's deltas at archive.
- Frontend (`lumen-web`): none in this change. Wiring the page's existing name query box
  to the new server-side filters (today it filters client-side on the current page only)
  is a candidate follow-up web change, out of scope here.
