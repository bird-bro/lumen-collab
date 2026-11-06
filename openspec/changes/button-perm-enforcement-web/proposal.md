# Proposal: button-perm-enforcement-web

## Why

Every console page still gates ALL its action buttons behind the single page perm
(`canManage = hasPerm('system:*:list')`), so the button-level permissions seeded and
pinned by `button-perm-enforcement-api` have no consumer on the frontend - a role
granted 新建用户 but not 删除角色 would still see and click both. This change wires each
affordance to its own button perm string.

## What Changes

- **Per-affordance `usePerm` gating on the six console management pages**, mapped from
  the coordination inventory (`openspec/coordination/button-perm-enforcement.md`):
  - 用户管理: 新建用户 → `system:user:create`, 重置密码 → `system:user:reset-pwd`,
    启停 Switch → `system:user:enable`
  - 角色管理: 新建角色/编辑角色/删除角色/菜单授权 → `system:role:create/update/delete/assign`
  - 菜单管理: 新建根节点 + 新增下级 → `system:menu:create`, 编辑节点 →
    `system:menu:update`, 删除节点 → `system:menu:delete`
  - 机构管理: 新建机构 + 新建根机构 → `system:org:create`, 编辑 → `system:org:update`,
    删除 → `system:org:delete`
  - 科室管理: 新建/编辑/删除科室 → `system:dep:create/update/delete`
  - 字典管理: 目录面板 → `system:dict-catal:create/update/delete`, 明细面板 →
    `system:dict-detail:create/update/delete`
- The action COLUMN stays page-gated (a user reaching the page holds the page perm);
  individual affordances render only when their own button perm is in the session set.
- Disabled affordances that stay visible (e.g. 字典 新建明细 before a row selection)
  keep their existing disabled logic AND gain the perm condition.
- Test sweep: existing page tests assert affordances visible under page-only perm sets -
  their session perm sets gain the button perms, plus new hidden-when-ungranted cases.
- Log pages: no buttons, untouched. No API client changes (endpoints unchanged).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-system-console`: a cross-cutting button-level affordance-gating requirement is
  added (each action button renders only when its own button perm is granted; the
  perm strings are the coordination inventory).

## Impact

- `lumen-web`: 6 page files (perm checks only - no layout/flow changes),
  6+ test files (perm-set updates + new gating cases).
- Depends on the backend change's V15 seed + pins being deployed (backend-first).
- Spec sync on archive: `openspec/specs/web-system-console/spec.md`.
