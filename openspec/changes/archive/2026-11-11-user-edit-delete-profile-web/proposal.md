# Proposal: user-edit-delete-profile-web

> The frontend half of the `user-edit-delete-profile` cross-stack feature (coordination:
> `openspec/coordination/user-edit-delete-profile.md`). Backend first - this change
> builds against the REAL backend API (no mock layer, user ruling 2026-11-05).

## Why

The console user page (`/system/users`) cannot administer a RIS: the create dialog
carries four fields, the row actions carry only 重置密码 + the enable switch, and the
角色 column can only name the current session user's roles (the 未提供 chip). The
backend change (`user-edit-delete-profile`) now ships the real surface: profile
columns, role/department names in the payload, detail/update/delete endpoints, and the
two new button perms (`system:user:update` / `system:user:delete`).

## What Changes

- List: columns rebuilt to 姓名+账号 / 角色 / 科室 / 职称 / 手机号 / 邮箱 / 创建时间 /
  操作; role chips from the row payload's `roleNames` (the 未提供 chip dies); the data
  source switches to `GET /system/users/list/profile`; client-side pagination stays.
- Row actions: 编辑 (drawer prefilled from `GET /system/users/{id}`, 登录账号 disabled)
  and 删除 (Popconfirm → `DELETE /system/users/{id}`, backend rejection surfaces as an
  error toast); gated by the two new button perms.
- Create dialog: grows the same field set (姓名/手机号/邮箱/性别/职称/所属科室/角色/
  检查类型/审核权限/签名) - username/password/name/roles unchanged in behavior.
- Dict-driven selects: 性别 (catalog 性别), 职称 (catalog 职称), 检查类型 (multi,
  catalog 检查类型), 审核权限 (single, catalog 审核权限) via the existing
  `dictItemsByName` + `useDictItems` hook; raw-code fallback on a missing catalog or
  failed fetch.
- 所属科室 select: the existing `listDeps` client (label = name, value = id).
- 签名 upload: a controlled upload control composed after the `logo-upload.tsx`
  pattern (client-side extension/2MB validation, the common image upload endpoint, the
  returned path as the form value).
- API clients: `src/api/users.ts` gains `listUsersWithProfile`, `getUserDetail`,
  `updateUser`, `deleteUser`; `types.ts` extends `UserItem` and adds `UserDetail` /
  `UpdateUserBody`. Password transport untouched (create/reset SM2 inside the client
  boundary stays).

## Capabilities

### New Capabilities

(none - the console behaviors extend `web-system-console`'s existing User Management
requirement.)

### Modified Capabilities

- `web-system-console`: User Management - list columns, edit/delete row actions, the
  expanded create/edit dialogs, dict-driven selects, the signature upload control,
  perm-gated affordances.

## Impact

- `src/api/users.ts` + `src/api/types.ts`: new client functions/types (additive).
- `src/pages/system/users/index.tsx`: page rebuild; `src/test/users-page.test.tsx`:
  rewrite (the tests stub API client modules at the seam - vi.mock - never the page
  data).
- No change to: auth stores, menu/router, other console pages, the password-cipher
  client, the logo upload control (composed, not modified - never-modify rule).
