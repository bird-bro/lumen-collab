# Proposal: button-perm-enforcement-api

## Why

The menu-management feature shipped the button-level authoring surface, but the seeds
contain zero F nodes and every console write endpoint is gated only by its page-level
`system:*:list` authority - button permissions are a model, not an enforcement. This
change materializes them server-side: the 22-button F-node seed and method-level
`@PreAuthorize` pins so each console action has its own authority.

## What Changes

- **Add migration `V15__button_perm_nodes.sql`** (lumen-system): 22 button (`F`) nodes
  under their console C pages - 用户管理 3 (create / reset-pwd / enable), 角色管理 4
  (create / update / delete / assign), 菜单管理 3 (create / update / delete), 机构管理 3
  (create / update / delete), 科室管理 3 (create / update / delete), 字典管理 6
  (dict-catal create/update/delete + dict-detail create/update/delete) - each with its
  `perms` string per the coordination inventory, explicit ids + `setval` resync, and
  admin-role (role 1) auto-grant with the NOT-EXISTS live-binding guard (V5 discipline;
  guarded/idempotent so the shared test DB can be hand-applied).
- **Pin every console write endpoint with a method-level
  `@PreAuthorize("hasAuthority('<button perms>')")`** on UserInfoController (3),
  RoleController (4), MenuController (3), OrgController (3), OrgDepController (3),
  DictCatalController (3), DictDetailController (3). The method annotation replaces the
  class gate for that method (Spring semantics): the button perm is the action
  authority; read endpoints keep the class-level page gate. Existing annotations and
  signatures stay untouched (method-level addition - sign-log-query b2 precedent).
- Log families stay untouched (read-only surfaces, no buttons).
- Cross-stack: the frontend change `button-perm-enforcement-web` gates each affordance
  by the same strings (see `openspec/coordination/button-perm-enforcement.md`).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `auth-rbac`: button-level permission enforcement becomes a requirement - the seeded
  F-node inventory with its admin auto-grant, and the write-endpoint method-level
  enforcement semantics (button perm = action authority; page perm = visibility).

## Impact

- `lumen-system`: 7 controllers (+1 method-level annotation per write endpoint),
  one new Flyway migration, new/extended permission-matrix tests.
- Spec sync on archive: `openspec/specs/auth-rbac/spec.md`.
- Shared design source: `openspec/coordination/button-perm-enforcement.md`.
- Deployment note: existing non-admin roles keep page access but lose button
  affordances until an administrator grants the new F nodes (intended tightening);
  the admin role is auto-granted by the seed.
