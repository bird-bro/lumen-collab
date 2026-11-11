# Tasks

Builds against the REAL backend (no mock layer, user ruling 2026-11-05); unit tests
stub API client modules at the seam (`vi.mock('@/api/...')`).

## 1. API clients + types

- [x] 1.1 Extend `src/api/types.ts`: `UserItem` gains phone/email/gender/title/depId/
  depName/modalitys/repAudit/signature/roleNames; add `UserDetail` and `UpdateUserBody`.
  Verify: `pnpm build` (tsc) passes.
- [x] 1.2 Extend `src/api/users.ts`: `listUsersWithProfile()`, `getUserDetail(id)`,
  `updateUser(id, body)`, `deleteUser(id)` - paths verbatim from the api delta
  (`/list/profile`, `/{id}`, `DELETE /{id}`). Verify: `pnpm build` passes; no existing
  export renamed (never-modify).

## 2. Signature upload control

- [x] 2.1 Write `src/pages/system/users/signature-upload.tsx` composed after the
  `logo-upload.tsx` pattern: controlled form control (value/onChange injected),
  client-side jpg/png/jpeg + 2MB validation BEFORE any request, `uploadImage()` →
  `onChange(path)` on success, preview from the stored path. Verify: `pnpm build`
  passes; component renders in isolation (a unit test on the validation
  reject/accept paths).

## 3. Page rebuild

- [x] 3.1 Rebuild `src/pages/system/users/index.tsx`: the profile list columns
  (姓名+账号/角色/科室/职称/手机号/邮箱/创建时间/操作), roleNames chips (dash on empty),
  the 编辑/删除 row actions perm-gated (`system:user:update`/`system:user:delete`),
  Popconfirm delete, the data source on `listUsersWithProfile`. Verify: RED→GREEN
  unit tests (payload shape, dash rendering, perm gating); `pnpm build` passes.
- [x] 3.2 The shared drawer (create + edit): the profile form fields
  (姓名/手机号/邮箱/性别/职称/所属科室/角色/检查类型/审核权限/签名), the edit prefill
  from `getUserDetail` (登录账号 disabled), the modalitys CSV split/join, dict-driven
  selects via `useDictItems` (性别/职称/检查类型/审核权限) with raw-code fallback, the
  deps select via `listDeps`, create submits the SM2-encrypted password flow
  unchanged. Verify: RED→GREEN tests (edit prefill, dict fallback, CSV round-trip,
  duplicate-username stays on-form); `pnpm build` passes.

## 4. Full verification

- [x] 4.1 Full unit suite + build: `pnpm test` (all suites green, prior suites
  untouched) and `pnpm build`. Verify: zero failures.
- [x] 4.2 Real-backend smoke against a locally running `lumen-system` (Direct-service
  preview override per lumen-web/CLAUDE.md): list renders the profile payload, edit
  round-trip persists, delete removes and the rejection paths toast. Verify: the smoke
  checklist recorded in the report; user browser confirmation is the verify gate.

## Workflow follow-up

- `/opsx:verify user-edit-delete-profile-web`, then the coordination gate: both changes
  verify PASS → batch-archive → `openspec sync`.
