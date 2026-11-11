# user-edit-delete-profile — Coordination (Cross-Stack Feature)

> Feature: the console user page grows the RCP-shaped admin surface (reference
> `/manage/account/user`): edit (profile + role rebind), protected logical delete, and
> the profile/business fields (手机号/邮箱/性别/职称/科室/检查类型/审核权限/签名). Origin:
> user request 2026-11-10 ("用户管理页面做的太简单了...你参考rcp项目的
> /manage/account/user页面"), field semantics ruled same day (检查类型=exam-type data
> visibility CT/MR/US/...; 审核权限=report-workflow tier 报告医生/初审医生/终审医生;
> 签名=handwritten signature image affixed to reports; 设备权限=technician room
> filter). Field range + protection matrix ruled in the propose session (双保护).

## Why

The console user page is a minimal scaffold (batch 6a): create carries only
用户名/姓名/角色/初始密码, row actions only 重置密码 + the enable switch, no edit or
delete exists. A RIS cannot be administered without them - a mistyped 姓名 or a moved
employee forces a recreate-and-rebind dance. The list rows also cannot self-describe
(role names render as the 未提供 chip for every non-session user).

## Shared Design

- **Storage**: eight nullable columns on `system.user_info` (V26, table REBUILD - the
  V17/V24 audit-block-tail discipline): `phone varchar(32)`, `email varchar(128)`,
  `gender integer` (dict 性别 code), `title integer` (dict 职称 code), `dep_id integer`
  (org_dep reference, SERVICE-enforced - user_info stays at ZERO physical FKs), 
  `modalitys varchar(128)` (CSV of dict 检查类型 codes, the RCP lineage shape),
  `rep_audit integer` (dict 审核权限 code), `signature varchar(255)` (the common
  upload's stored path, verbatim).
- **Dict catalogs**: 「检查类型」 id 1021 (CT/MR/US/ENDO, +200000 detail band) and
  「审核权限」 id 1022 (不参与 0 / 报告医生 1 / 初审医生 2 / 终审医生 3) - deterministic
  ids, ON CONFLICT-guarded, the V23/V25 house style; consumed by-name via the existing
  ungated lookup. The frontend renders labels through `useDictItems` with raw-code
  fallback (role-type-dict D4 precedent).
- **API contract** (`/api/v1/system/users`): `GET /list/profile` (the richer read -
  roleNames/depName batch-resolved, no N+1), `GET /{id}` detail (roleIds for the edit
  echo, never the hash), `PUT /{id}` partial update (username immutable, roleIds
  present = FULL-REPLACE, dead dep/role id = A0400 no-change), `DELETE /{id}` logical
  delete + role unbind (self + seed-admin id 1 reject A-class - the 双保护 ruling).
  The plain `GET /list` and the four existing endpoints stay byte-identical
  (never-modify).
- **Permissions**: two new F nodes under 用户管理 (id 2) - 编辑用户
  `system:user:update` sort 5, 删除用户 `system:user:delete` sort 6, admin
  auto-granted (V15 CONTENT-guard discipline). The frontend gates the row affordances
  by them; no invented strings.
- **OUT of scope** (deliberate, design Non-Goals): 设备权限 (awaits the org-domain
  room/device registry - device/room SEPARATE tables, user ruling 2026-11-10), avatar,
  the extended archive set, consumer wiring in radiology flows (store/display only, the
  role-type-dict D7 boundary), 审核权限→role-page placement (rejected: a person's tier
  is a grade, not an authorization bundle; re-evaluation triggers recorded in design).

## Decisions

- D1-D12 in the api change's `design.md` (the authoritative how) - the web change
  references them, do not restate.
- 2026-11-10 - field semantics (检查类型/审核权限/设备权限/签名) ruled by the user in the
  propose session, mid-turn message.
- 2026-11-10 - 审核权限 stays on the USER page (person's grade, not a role grant),
  检查类型 stays on the USER page (per-person visibility), 设备权限 deferred to the
  room/device registry feature, 签名 reuses the common upload transport.
- 2026-11-11 - V26 rebuilds user_info instead of ADD COLUMN (audit-block-tail pin) and
  drops the physical dep_id FK (binding design: user_info at 0 FKs, service-level
  integrity); the data spec delta records the clarification.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend (lumen-sev) | `user-edit-delete-profile` (this change, -api scope) | archived 2026-11-11 | PASS 32/32 (173/173 tests; verify.md) |
| frontend (lumen-web) | `user-edit-delete-profile-web` | archived 2026-11-11 | PASS 16/16 (286/286 tests; API smoke + user browser confirmation 2026-11-11) |

## Gate

**Gate**: SATISFIED 2026-11-11 — both changes verify PASS and are archived as
`2026-11-11-user-edit-delete-profile-{api,web}`; specs synced into api / auth-rbac /
data / system-data / web-system-console (sync markers added). The web apply surfaced a
backend spec gap (create needed the profile fields) — closed inside the api change
(create-carries-profile scenario, 13/13). Git commit is a separate track. Backend first - the frontend builds against the real API (no mock
layer, user ruling 2026-11-05). The frontend change is created at apply time (tasks
5.2) and carries the UI work: list columns 姓名+账号/角色/科室/职称/手机号/邮箱/创建时间/
操作, edit/delete row actions, the expanded create dialog, dict-driven selects, the
signature upload control (the `logo-upload.tsx` pattern), perm-gated affordances.
