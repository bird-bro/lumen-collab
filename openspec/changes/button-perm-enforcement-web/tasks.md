# Tasks: button-perm-enforcement-web

## 1. Users page gating (red → green)

- [ ] 1.1 Failing cases: session with page perm only → 新建用户/重置密码/启停 hidden; +`system:user:create` → 新建用户 shows; +`reset-pwd`/`enable` similarly (design D5) - verify: red, then green once the three `hasPerm` checks land in `pages/system/users/index.tsx`.

## 2. Roles, menus, orgs, deps, dicts gating (red → green)

- [ ] 2.1 Roles page: 新建角色/编辑角色/删除角色/菜单授权 per-string cases (`system:role:create/update/delete/assign`) - verify: red → green; existing role tests' perm sets grown first.
- [ ] 2.2 Menus page: 新建根节点+新增下级 share `system:menu:create`; 编辑节点/删除节点 per-string - verify: red → green; existing menu tests' perm sets grown.
- [ ] 2.3 Orgs page: 新建机构+新建根机构 share `system:org:create`; 编辑/删除 per-string (tree-row affordances) - verify: red → green; existing org tests' perm sets grown.
- [ ] 2.4 Deps page: 新建/编辑/删除科室 per-string (`system:dep:*`) - verify: red → green; existing dep tests' perm sets grown.
- [ ] 2.5 Dicts page: 目录面板 three strings (`system:dict-catal:*`) + 明细面板 three strings (`system:dict-detail:*`); 新建明细 compounds `disabled = !perm || !selected` (design D4) - verify: red → green; existing dict tests' perm sets grown.

## 3. Full verification

- [ ] 3.1 Unit suite + type check - verify: `pnpm test` (all files, 0 failures) and `pnpm build` pass in `lumen-web/`.
- [ ] 3.2 Real-backend walkthrough with `button-perm-enforcement-api` deployed: as admin every affordance shows; craft a role with a subset (e.g. page + create only), log in as its user, verify ungranted buttons absent and the granted write passes server-side while a sibling write returns A0301 - verify: observable behavior matches every WHEN/THEN of the delta.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/web-system-console/spec.md` at archive time; gate = both registered changes verify PASS (see `openspec/coordination/button-perm-enforcement.md`).
- Backend-first sequencing: start this change's real-backend steps only after `button-perm-enforcement-api` lands.
- Operator note for the release: non-admin roles lose button affordances until the F nodes are granted (admin auto-granted by the seed).
