# Proposal: user-edit-delete-profile

> Cross-stack feature (lumen-sev + lumen-web). Per the project's cross-stack workflow a
> coordination doc `openspec/coordination/user-edit-delete-profile.md` tracks both
> per-stack changes; the registry names `user-edit-delete-profile-api` and
> `user-edit-delete-profile-web`.

## Why

The console user page (`/system/users`) is a minimal scaffold (batch 6a): the create
dialog carries only 用户名/姓名/角色/初始密码, the row actions carry only 重置密码 +
the enable switch, and no edit or delete exists. The RCP reference page
(`/manage/account/user`) shows the expected admin surface: rich per-user profile fields
(phone/email/gender/title/department), the business fields that make a RIS account a
working radiologist account (检查类型 visibility, 审核权限 tier, 设备 visibility, the
report-贴附 signature image), and edit/delete row actions. A RIS cannot be administered
without them — a mistyped 姓名 or a moved employee currently forces a
recreate-and-rebind dance.

Field semantics per user direction 2026-11-10: 检查类型 controls which exam-type data
(CT/MR/US/...) a user can see; 审核权限 is the report-workflow authority tier
(报告医生/初审医生/终审医生); 设备权限 controls which devices' images the user can
see; 签名 is the doctor's handwritten-signature image affixed to reports.

## What Changes

Backend (lumen-system):

- `user_info` gains the profile columns via Flyway V26 (system schema): `phone`,
  `email`, `gender` (dict 性别 code), `title` (dict 职称 code), `dep_id` (FK
  `org_dep.id`), `modalitys` (CSV of dict 检查类型 codes), `rep_audit` (dict 审核权限
  code, single value), `signature` (stored image path from the common upload). All
  nullable — existing rows and scripts keep working.
- Two new dict catalogs seeded (public schema, V6 house style, deterministic ids):
  「检查类型」 (code/modality abbreviations CT/MR/US/...) and 「审核权限」 (报告医生/
  初审医生/终审医生). 性别 (1004) / 职称 (1005) already seeded — consumed.
- `GET /api/v1/system/users/list` rows extend to carry the new fields plus the user's
  bound role names (the current response cannot; the page shows 未提供 today).
- `GET /api/v1/system/users/{id}` returns the full profile detail (edit-dialog echo —
  the RCP edit flow fetches before opening).
- `PUT /api/v1/system/users/{id}` partial-update (name/phone/email/gender/title/depId/
  roleIds/modalitys/repAudit/signature); `username` immutable, `password` untouched
  (reset stays its own endpoint).
- `DELETE /api/v1/system/users/{id}` logical delete with cascade unbind of `user_role`.
  Protection matrix (user ruling 2026-11-10): self-delete and seed-admin (`id = 1`)
  both rejected A-class server-side.
- New button perm nodes under 用户管理 (V26, V15 discipline): 编辑用户
  `system:user:update`, 删除用户 `system:user:delete`; admin auto-granted.

Frontend (lumen-web):

- `/system/users` rebuilt to the RCP-shaped layout: list columns
  姓名+账号 / 角色 / 科室 / 职称 / 手机号 / 邮箱 / 创建时间 / 操作.
- Row actions: 编辑 (profile drawer, username disabled), 删除 (Popconfirm → delete
  endpoint). 重置密码 and the 启用 switch stay as-is.
- The create dialog grows the same field set (username/password/name/roles + the
  profile fields + 检查类型 multi-select + 审核权限 select + 签名 upload); role names
  in the list now come from the payload (the 未提供 chip dies).
- Gender/title/exam-type/rep-audit selects are dict-driven via the existing
  `dictItemsByName` + `useDictItems` hook (role-type-dict precedent); the department
  select consumes the existing `org-deps` clients; the signature upload reuses the
  common image upload endpoint through a dedicated controlled-form-control component
  (the `logo-upload.tsx` pattern).

NOT in this change (deliberate, see design Non-Goals): 设备权限 (RCP binds physical
exam-room devices — lumen has no device/room registry yet; the field arrives with the
lumen-integration device model), avatar, and the extended archive set (学历/岗位/
生日/身份证/院校/专业/地址 — no consuming feature yet).

## Capabilities

### New Capabilities

(none — the user-management behaviors this change extends already live in the
`api` / `auth-rbac` / `data` / `system-data` / `web-system-console` capabilities;
introducing a parallel `user-profile` capability would fork them.)

### Modified Capabilities

- `api`: User Management Endpoints — list rows extend (profile + business fields +
  role names); new GET detail, PUT update, DELETE endpoints under the same
  `/system/users` prefix.
- `auth-rbac`: User Management — user CRUD now includes modify/delete with the
  protection matrix; profile fields persist; role rebind on update.
- `data`: new requirement pinning the `user_info` profile columns (V26) and the
  `dep_id` FK semantics.
- `system-data`: Dict Seed Data — two new seeded catalogs (检查类型, 审核权限).
- `web-system-console`: User Management — list columns, edit/delete row actions,
  expanded create dialog, dict-driven selects, signature upload, dept select.

## Impact

- `lumen-sev/lumen-system`: V26 migration (schema + dict seed + perm seed);
  `UserInfo` entity + `UserItemDto` + new `UserDetailDto`/`UpdateUserRequest`;
  `UserInfoService`/Impl (never-modify rule: new methods, existing signatures
  byte-identical); `UserInfoController` new endpoints; error reuses `ExceptionEnum`
  A-class constants (`A0111` duplicates, `A0400` bad payloads, `A0301` denials,
  `B0031` misses).
- `lumen-web`: `src/api/users.ts` + `types.ts` new client functions/types;
  `src/pages/system/users/index.tsx` rebuild; `users-page.test.tsx` rewrite; a new
  signature-upload control component composed after `logo-upload.tsx`; `useDictItems`
  reused (no new hook).
- Frontend permission gating keys off the two NEW seeded perm strings
  (`system:user:update` / `system:user:delete`) — V15-style server-pinned, no invented
  strings.
- No change to: login/me payloads beyond additive fields, the workstation model,
  password flows (SM2 create/reset unchanged), org/dep management, the common upload
  endpoint (consumed as-is).
