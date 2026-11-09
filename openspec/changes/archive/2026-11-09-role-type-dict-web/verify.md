---
verify:
  change: role-type-dict-web
  date: 2026-11-09
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# verify.md — role-type-dict-web

> Three-layer verification (2026-11-09). Overall **PASS**: L1 build green
> (1.81s), L3 suite 279/279 across 24 files, all 10 delta scenarios IMPLEMENTED
> with code+test evidence.

## Stacks

| Stack | L1 build | L2 spec alignment | L3 tests |
|:--|:--|:--|:--|
| lumen-web | ✓ PASS (`pnpm build` exit 0, 1.81s) | 10/10 IMPLEMENTED | ✓ PASS (`pnpm test` exit 0, 279/279) |

## Scenario alignment detail

### web-system-console delta — Role Management and Menu Assignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Role CRUD round-trip (regression) | ✓ IMPLEMENTED | `roles-page.test.tsx` CRUD suite (create/duplicate-A0714/delete) green in the 279; submit paths post through `createRole`/`updateRole` unchanged |
| Menu assignment is full-replace (regression) | ✓ IMPLEMENTED | menu-assignment suite (echo reopen / union ancestors / A0400) green in the 279; `withAncestors` untouched |
| Type label resolved from the dict | ✓ IMPLEMENTED | dict load effect (roles/index.tsx:178 `dictItemsByName(ROLE_TYPE_DICT_NAME)`) + column render `resolveDictLabel` (:343); blue-10/others-cyan chrome (:341-346); test `renders 类型 labels from the dict` (roles-page.test.tsx:228) |
| Unknown code falls back to the raw number | ✓ IMPLEMENTED | `resolveDictLabel` fallback `String(code)` (utils/dict-items.ts:39-41); test asserts code 42 renders raw (roles-page.test.tsx:228-246) |
| Dict fetch failure degrades without blocking | ✓ IMPLEMENTED | mount-effect catch → one `messageApi.error` (roles/index.tsx:178-184); test `dict fetch failure degrades` (roles-page.test.tsx:247) asserts raw codes + message + create round-trip |
| Drawer offers dict categories | ✓ IMPLEMENTED | dict-driven Select on both drawers (roles/index.tsx:527 create with `initialValue={10}`, :560 edit reflects the row code via `setFieldsValue`); options from `roleTypeOptions` (:139-142); tests :318/:340/:361 |
| One wire call per catalog per session | ✓ IMPLEMENTED | utils session cache (utils/dict-items.ts:13,22-31 - promise dedupe, failure not cached :27); page loads once per mount (roles/index.tsx:175-184); test `one resolver call per mount` (roles-page.test.tsx:265) + wire-level cache tests (dict-items.test.ts:46-66) |
| Status switch reflects and toggles the enable flag | ✓ IMPLEMENTED | 状态 column (roles/index.tsx:350-365, orgs chrome `aria-label 启用 {name}`, disabled without `system:role:update`) → `toggleEnable` (:274-284) → `toggleRoleEnable` client (api/roles.ts:38); tests :384/:399 |
| Toggle failure reverts the switch | ✓ IMPLEMENTED | failure branch re-lists (roles/index.tsx:281-283) - no optimistic flip; test `a rejected toggle...re-list restores` (roles-page.test.tsx:409) |

## Issues

(none — no PARTIAL, no NOT_IMPLEMENTED)

## Notes (process record)

- Out-of-band user styling during the session: the drawer remark textareas pin
  `height: 130px` (roles/index.tsx, both drawers; test `pins the remark textarea`
  roles-page.test.tsx) — recorded here because it is a user ruling outside the
  delta scenarios.
- The real-backend API smoke (nginx:80 `/items/角色类型` → auth-entry envelope,
  route live) was exercised in the apply phase; the authenticated path is proven
  end-to-end by the api change's integration tests (see its verify.md). Browser
  UI smoke remains the user-side step per the house pattern.
- The assign-icon ruling evolved during the session (key → ShieldCheck via the
  parallel session's user contact) — recorded in console-shell ACTION_ICONS and
  the roles-page tests; orthogonal to this change.
