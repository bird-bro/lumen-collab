<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: user-edit-delete-profile-web
  date: 2026-11-11
  overall: PASS   # 2026-11-11 refresh: user browser confirmation given (UI eyeball over :1574 passed; API smoke already green)
  stacks:
    web:
      L1: PASS
      L2: "16/16"             # all scenarios confirmed: the API round-trip evidence + the user browser confirmation (2026-11-11)
      L3: PASS
---

# Verification: user-edit-delete-profile-web

Overall: **PASS** — 2026-11-11 refresh: the user confirmed the rebuilt page in the
browser (http://localhost:1574/system/users over the vite proxy to the real backend),
closing task 4.2's gate. The API round-trip evidence below stands unchanged.

## web (lumen-web)

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `pnpm build`; exit=0; logs/build-web.log |
| L2 spec | 16/16 | 0 PARTIAL (browser confirmation closed 2026-11-11) |
| L3 tests | PASS | `pnpm test`; exit=0; **286/286 (24 files)**; logs/test-web.log. One recorded FLAKE: menus-page's tree-filter test failed once in full-suite order (jsdom message-portal timing), passed solo AND on the full re-run — not related to this change's files. |

## Real-backend smoke evidence (task 4.2, 2026-11-11)

Over `http://localhost:1574` → proxy → `lumen-system` :8081 (Direct-service preview
override): SM2 login → `GET /list/profile` (roleNames 系统管理员/depName render) →
`GET /users/{id}` (no password field) → `PUT /users/{id}` (phone persists) →
`DELETE /users/1` self-delete A0400 → dict lookups 检查类型 (CT/MR/US/ENDO) +
审核权限 (不参与/报告/初审/终审) → full create (profile fields land) → roleIds []
full-replace → logical delete → out of the live list. SPA route `/system/users` 200.

## Scenario alignment (web-system-console § User Management, 16)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Paged user list | IMPLEMENTED | index.tsx:151-168 (view state); users-page.test.tsx:189-201 |
| List columns render the profile payload | IMPLEMENTED | index.tsx:384-470 (姓名+账号/角色/科室/职称/手机号/邮箱); test 197-211 |
| Role chips come from the row | IMPLEMENTED | index.tsx:415-428 (roleNames.map; the 未提供 chip is gone from the codebase); test 203-205 |
| Duplicate username feedback | IMPLEMENTED | index.tsx:334 (setFields on username); test 267-281 |
| Create dialog carries the profile form | IMPLEMENTED | index.tsx:596-701 (the 12-field form); test 249-271 |
| Exam-type and audit-tier selects are dict-driven | IMPLEMENTED | index.tsx:181-192 (dictItemsByName load); 648-669 (Selects); CSV split/join test 348-376 |
| Signature upload reuses the common transport | IMPLEMENTED | signature-upload.tsx:47-95; signature-upload.test.tsx 5/5 |
| Edit opens prefilled with detail | IMPLEMENTED | index.tsx:236-283 (getUserDetail prefill, 登录账号 disabled); test 289-313 |
| Edit failure keeps the dialog | IMPLEMENTED | index.tsx:339-341 (update branch msg path); test 335-345 |
| Delete confirms then removes | IMPLEMENTED | index.tsx:347-360 + Popconfirm 554-563; test 386-399 |
| Protected-account rejection surfaces | IMPLEMENTED | index.tsx:351-354; test 402-412 |
| Dict-driven gender and title with raw-code fallback | IMPLEMENTED | index.tsx:444-447 (resolveDictLabel); test 214-224 (dict-failure degrade) |
| Department select lists departments | IMPLEMENTED | index.tsx:164-171 (listDeps) + 626-633 (Select); test 253 |
| Password and enable actions | IMPLEMENTED | index.tsx:363-404 (reset) + 406-419 (enable); tests 418-446 |
| Encrypted password fields on create and reset | IMPLEMENTED | users.ts:44,65 (encryptPassword INSIDE the client boundary — untouched); create test asserts the body contract |
| Edit and delete follow the new button permissions | IMPLEMENTED | index.tsx:103-104 (system:user:update/delete gates) + 546-563; test 471-484 |

All 16 scenarios IMPLEMENTED — closed with the user's browser confirmation 2026-11-11.

## Issues

(none — the browser-confirmation PARTIAL closed 2026-11-11 with the user's eyeball
pass over http://localhost:1574/system/users; 16/16.)
