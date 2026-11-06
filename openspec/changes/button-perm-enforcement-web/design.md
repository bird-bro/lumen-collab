# Design: button-perm-enforcement-web

## Context

The `usePerm()` hook (reactive `hasPerm(code)` over the auth store's session set) is the
established gate; every management page already computes a blanket `canManage =
hasPerm('system:*:list')` and hangs ALL its action affordances on it. The backend change
seeds the 22 F nodes and pins the write endpoints with the same strings (coordination
inventory). See proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- Replace the blanket affordance gate with per-string checks on the six pages, keeping
  the existing layout/flow byte-identical apart from the visibility conditions.

**Non-Goals:**
- No new components, no router/layout changes, no API client changes, no menu-model
  work, no server-side changes (that is `button-perm-enforcement-api`), no log-page
  work (read-only, no buttons).

## Decisions

- **D1 - Per-string checks inline, no gate abstraction.** Each page swaps
  `canManage &&` / unconditional affordances for `hasPerm('<string>')` on the exact
  inventory strings. A `ButtonPerm` helper/table per page would hide the string from
  the diff and invite drift from the inventory; the string literal inline next to the
  affordance IS the audit trail (same style as the existing `hasPerm('system:menu:list')`
  usages).
- **D2 - The action column stays page-gated.** The column render remains under the
  existing page-perm check; individual buttons additionally require their own string.
  This keeps the un-perm'd edge case (button perm without page perm) at "no column",
  matching the delta scenario and avoiding three-state column logic.
- **D3 - Shared-perm affordances gate together.** 新建机构/新建根机构,
  新建根节点/新增下级, and the dicts panels' three affances each reference one string -
  no per-button distinctions that the backend pins do not make.
- **D4 - Disabled affordances compound the perm.** 字典's 新建明细 (disabled until a
  catalog row is selected) becomes `disabled = !hasPerm(...) || !selected` - visible
  placement, disabled function, matching its existing pattern.
- **D5 - Test sweep: grant sets grow, then gating cases land.** Existing page tests
  set session perms to the page perm only; after this change their affordances would
  vanish. Each page test's `beforeEach` perm set gains the page's button strings, then
  new cases assert the hidden-when-ungranted / shown-when-granted pairs (the delta
  scenarios, per page).

- **D7a - orgs 设为默认 stays page-gated** (apply-time note, 2026-11-06): the
  org-redesign session's campus column added a 设为默认 action with no dedicated F
  node in the V15 inventory - it keeps the page gate until the org-redesign owners
  define its button perm (server-side is unpinned too).
- **D7 - Menus page visual alignment with the 随访 reference** (user instruction in
  the apply command, 2026-11-06; reference corrected by the user - EIS was the WRONG
  system, the real one is `/birdbro/work/01-JF/00-gitlab/随访/fu_web/src/views/system/
  menu/index.vue`): the management table adopts the reference column set - 菜单名 (tree) /
  类型 tag (M 目录 / C 页面 / F 按钮) / 路径 / 组件 / 权限标识 / 排序 / 可见 (tag, F shows
  "-") / 操作 (fixed right, icon actions with tooltips, per-action perms) - and its
  tool-row pattern (menu-name filter + 查询 on the left, add button on the right). The
  filter is client-side over the loaded tree: hit nodes keep their whole subtree,
  children hits keep the ancestor chain (the reference's filterTree semantics), applied
  on 查询/Enter. Drawer follows the reference field order and wording (上级菜单/
  菜单标题/类型/路径/组件/图标/权限标识/排序/是否显示) with lumen deltas kept: no
  重定向/是否缓存 (columns trimmed from the lumen model, design D5 of menu-management),
  and perms stays editable for EVERY type (lumen C nodes carry page perms - the
  reference hides perms on C, lumen cannot). Display only - the gating scenarios keep
  their recorded behavior.

## Risks / Trade-offs

- [A missed swap leaves an affordance on the blanket gate] → the per-page gating tests
  enumerate every affordance of the page; a missed swap fails its page's new case.
- [String drift from the backend inventory] → strings are quoted from the coordination
  table in both stacks' reviews; a drift surfaces as an A0301 in the real-backend
  walkthrough (server pins are the backstop).
- [Users mid-session lose affordances after a role re-grant] → the perm set refreshes
  on login/me only (existing behavior, unchanged); acceptable for a console surface.

## Migration Plan

Pure frontend swap behind existing routes; deploy after the backend seed/pins are live
(backend-first). Rollback = revert the page commits; no data or contract change.

## Open Questions

(none - scope pinned by the 2026-11-06 user ruling: full landing)
