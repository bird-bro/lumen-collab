# Coordination: button-perm-enforcement

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "button-perm-enforcement"`.

## Why

The menu-management feature (archived 2026-11-06) shipped the button-level AUTHORING
surface - F nodes with `perms` strings can be created, granted, and delivered through
login/me - but the runway is empty: the seeds contain ZERO F nodes (5 M + 8 C only, all
perms strings page-level `system:*:list`), and every console page still gates ALL its
action buttons behind the single page perm. Button-level permissions exist as a model,
not as a lived experience. This feature lands them end to end: seeds, server-side
enforcement, and per-button frontend gating.

## Shared Design

Single source of truth for both stacks. The perms inventory below is the F-node seed
spec AND the endpoint-pin map AND the frontend gate map - one table, three consumers.

### Perms inventory (module:resource:action; F nodes under their C page)

| Page (C node) | Action (页面实际 affordance) | perms string | Endpoint pinned |
|:--|:--|:--|:--|
| 用户管理 (2) | 新建用户 | `system:user:create` | `POST /api/v1/system/users` |
| 用户管理 | 重置密码 | `system:user:reset-pwd` | `PUT /api/v1/system/users/{id}/password` |
| 用户管理 | 启停 Switch | `system:user:enable` | `PUT /api/v1/system/users/{id}/enable` |
| 角色管理 (9) | 新建角色 | `system:role:create` | `POST /api/v1/system/roles` |
| 角色管理 | 编辑角色 | `system:role:update` | `PUT /api/v1/system/roles/{id}` |
| 角色管理 | 删除角色 | `system:role:delete` | `DELETE /api/v1/system/roles/{id}` |
| 角色管理 | 菜单授权 | `system:role:assign` | `PUT /api/v1/system/roles/{id}/menus` |
| 菜单管理 (10) | 新建根节点 / 新增下级 | `system:menu:create` | `POST /api/v1/system/menus` |
| 菜单管理 | 编辑节点 | `system:menu:update` | `PUT /api/v1/system/menus/{id}` |
| 菜单管理 | 删除节点 | `system:menu:delete` | `DELETE /api/v1/system/menus/{id}` |
| 机构管理 (7) | 新建机构 / 新建根机构 | `system:org:create` | `POST /api/v1/system/orgs` |
| 机构管理 | 编辑机构 | `system:org:update` | `PUT /api/v1/system/orgs/{id}` |
| 机构管理 | 删除机构 | `system:org:delete` | `DELETE /api/v1/system/orgs/{id}` |
| 科室管理 (8) | 新建科室 | `system:dep:create` | `POST /api/v1/system/org-deps` |
| 科室管理 | 编辑科室 | `system:dep:update` | `PUT /api/v1/system/org-deps/{id}` |
| 科室管理 | 删除科室 | `system:dep:delete` | `DELETE /api/v1/system/org-deps/{id}` |
| 字典管理 (11) | 新建目录 / 编辑目录 / 删除目录 | `system:dict-catal:create/update/delete` | `/api/v1/system/dicts/catal/**` |
| 字典管理 | 新建明细 / 编辑明细 / 删除明细 | `system:dict-detail:create/update/delete` | `/api/v1/system/dicts/detail/**` |

22 F nodes total. Log pages (日志查询/登录日志) are read-only - no buttons, no nodes.

### Enforcement model

- **Read endpoints keep the class-level page gate** (`system:*:list`) unchanged.
- **Write endpoints gain method-level `@PreAuthorize("hasAuthority('<button perms>')")`**
  - the method annotation REPLACES the class gate for that method (Spring semantics),
    so the button perm IS the action authority: a grant of only `system:user:create`
    can create but not list; page visibility stays governed by the C-node grant.
    Precedent: the sign-log-query b2 method-level pins.
- **Frontend**: per-affordance `hasPerm('<button perms>')` checks replace the blanket
  `canManage` for action buttons (the action COLUMN may stay page-gated); `新建根机构`/
  `新增下级`/`新建根节点` share their page's `create` perm with `新建机构`/`新增下级`.
- Existing class-level `@PreAuthorize` lines stay untouched (annotations are additive
  method-level; the sign-log precedent covers annotating existing handlers).

### Seed (V15, lumen-system)

- 22 F nodes under their C parents with explicit ids (a fresh block above the current
  sequence), `type='F'`, title = the action label, perms per the table, `sort` numbering
  the actions, `visible=1`.
- Admin role auto-grant: `role_menu` rows for role 1 with the NOT-EXISTS live-binding
  guard (V5 discipline) + `setval` resync. Guarded/idempotent style so the shared test
  DB can be hand-applied before the next boot (V5/V15 pattern).
- Existing non-admin roles keep their page grants but see no buttons until the admin
  grants the F nodes - the intended tightening.

## Decisions

- Full landing (user ruling 2026-11-06): seeds + server-side method pins + frontend
  per-button gating, one cross-stack feature - not the seed-only middle option.
- Method-level pin REPLACES the page gate on write endpoints (button perm = action
  authority; page perm remains the visibility authority) - 2026-11-06.
- `dict-catal`/`dict-detail` are two resources (matching the two endpoint families);
  `reset-pwd` abbreviates the action segment - 2026-11-06.
- Log pages stay button-free (read-only surfaces) - 2026-11-06.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | button-perm-enforcement-api | proposed (artifacts complete, valid) | - |
| frontend | button-perm-enforcement-web | **archived 2026-11-06** | PASS (3/3, L1/L3 PASS, 204/204) |

## Gate

Feature done = ALL registered changes verify PASS -> archive each.

**Feature closed 2026-11-06** — both registered changes verify PASS and archived;
`auth-rbac` carries § Button-Level Permission Enforcement (seed + pins), `web-system-console`
carries § Button-Level Affordance Gating. D7a gap RESOLVED same day: 设为默认 turned out
to ride `PUT /orgs/{id}` (already pinned `system:org:update`, covered by the 编辑院区 F
node) — the frontend button now gates on `system:org:update`; no new F node was needed.
