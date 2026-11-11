# Design: user-edit-delete-profile

## Context

`user_info` (V1) holds six business columns (username/password/must_change_password/
enable/name + audit). The console page paginates the plain `list` client-side and its
only row mutations are reset-password/enable. The RCP reference carries the full
surface in a two-table model (`user_info` + `user_join` for org-binding) — lumen does
NOT copy that split: `user_join` exists because RCP is multi-org (a user joins many
orgs with a default); lumen v1 is the single controlled hospital model where one
department anchor column suffices. Constraints: never-modify on existing public
methods (overload), Flyway-only DDL, additive DI, dict codes stored not labels,
button perms server-pinned (V15/V21 discipline), SM2 password transport untouched.

## Goals / Non-Goals

Goals: editable/deletable users with the core clinical profile; role names visible
per row; dict-driven selects; permission-gated new affordances.

Non-Goals (explicit, several recorded from the field-range + 2026-11-10 field-semantics
messages):
- NO 设备权限 (RCP `user_join.job_value` binding the room's `dev_iot`): a
  TECHNICIAN-scoped second-level filter below the department (a department holds MANY
  rooms/devices — DR-1, DR-2, CT-1, CT-2 of mixed modality). The registry model is
  user-ruled 2026-11-10 after the OPS reference read: DEVICE and ROOM are SEPARATE
  tables in the org domain (the OPS `/manage/org/device` + `/manage/org/room` pages —
  NOT RCP's one-room-one-device inline shape): `org_device` carries the asset fields
  PLUS the device's DICOM port config (AE / IP / Port — the device connects by them);
  `org_room` carries the room's own business fields PLUS the queue-call config
  (call_ip/port/mac — the queue screen sits in the ROOM and calls patients into it)
  and a swappable `dev_id` FK to the device. `lumen-integration` consumes the device
  AE for MWL/MPPS/storage routing; exam rows anchor to the ROOM; so the user-side
  permission binds ROOMS (the data row's key), 「设备权限」 being the historical label.
  Whether its consumption copies RCP's frontend-dropdown filter or hardens server-side
  is that future feature's decision; recorded here so the console field arrives
  designed against this registry, not guessed.
- NO avatar (`icon`) — presentation polish, no consuming surface.
- NO 学历/岗位/毕业院校/专业/参加工作时间/身份证/生日/地址 — the extended archive set
  (would need new dict catalogs; deferred until a consumer asks).
- NO consumer wiring of 检查类型/审核权限/签名 in the radiology worklist/report flows —
  this change STORES and displays the values (console surface only). Their consumers:
  the worklist's 检查类型/检查室 filters and the report state machine's tier gates are
  their own future changes, the same boundary role-type-dict D7 drew for role enable.
- NO 审核权限 → role-page placement (explicitly weighed and rejected 2026-11-10): the
  tier is a PERSON's grade (岗位属性 — set at hire, changed at promotion), not an
  authorization bundle; the workstation ruling (permissions flow only through roles)
  governs MENU visibility, not person attributes of this family (signature/exam-type
  visibility/tier). The report state machine reads the single user-row value directly;
  a role-based tier would force a highest-bound-tier aggregation rule plus a
  cross-department decomposition that buys nothing for the ultrasound/endo
  self-sign-self-report reality. Re-evaluation triggers are recorded: per-department
  tier for one person, or rotation/roster-based reviewing — both migrate the column.
- NO report-flow configuration (放射三阶 vs 超声/内镜自签自发 or the 大医院审核步骤):
  the per-department WORKFLOW SHAPE belongs to the exam-core report state machine, not
  to the user row. The user row answers WHO (tier), the state machine answers HOW MANY
  gates.
- NO `user_join`-style table, no multi-org membership.
- NO server-side paging change (list stays plain; client-side pagination stays).
- NO behavioral consequence of disable beyond today's login rejection (unchanged).

## Decisions

- **D1 — Columns on `user_info`, not a join table.** `phone varchar(32)`, `email
  varchar(128)`, `gender integer`, `title integer`, `dep_id integer` (FK `org_dep`),
  all nullable. Rationale: one live department per user in the controlled model; the
  RCP `user_join` split solves a multi-org problem lumen does not have. Alternative
  (copy `user_join`) rejected: joins N tables for fields the console renders once, and
  RCP's own docs call the layout 乱 (REFERENCE.md: "表结构也比较乱需要重新整理").
- **D2 — Role names joined once per list, in SQL.** `listUsers` assembles
  `UserItemDto.roleNames` by one query over live `user_role` rows of all listed users
  + one `role_info` name fetch (no N+1 per-row mapper calls); `depName` resolves the
  same way (one batch fetch of the referenced `org_dep` rows). Alternative (frontend
  resolves role names from `roles/list`) rejected: the frontend would need EVERY role
  (page 1 defaults to 20) to label a user bound to role 500 — the payload must be
  self-describing (the 未提供 chip was exactly this gap).
- **D3 — Update is a dedicated partial-update DTO, not the create request.**
  `UpdateUserRequest` (name/phone/email/gender/title/depId/roleIds, no username, no
  password). `roleIds` present ⇒ full-replace of bindings (the role-menu assign
  precedent: "the posted set becomes the complete grant"); absent ⇒ bindings
  untouched. Service implementation is a NEW interface method (never-modify:
  existing `createUser`/`listUsers`/`resetPassword`/`setEnable` signatures stay
  byte-identical).
- **D4 — Delete protection matrix in the service layer.** Self-delete: compare the
  target id against the bridged audit identity (the `MetaObjectConfig` USER-HEADER
  source, read the same way). Seed admin: `id == 1` constant (V2 seed; the id is a
  migration-stable contract). Both reject A-class; the body of the delete is a
  logical delete (`@TableLogic` deleteById) + logical unbind of live `user_role`
  rows, one transaction. RCP has no protection — lumen's bootstrap reality (id 1 is
  the only guaranteed login) forces the guard (user ruling 2026-11-10: 双保护).
- **D5 — Perms: two new F nodes, V15 discipline.** `system:user:update` (编辑用户,
  sort 5) + `system:user:delete` (删除用户, sort 6) under 用户管理 (id 2), admin
  auto-grant via the NOT-EXISTS live-binding guard. Controller pins them
  method-level exactly like create/reset-pwd/enable. No invented strings anywhere.
- **D6 — Gender/title via the existing dict-by-name lookup.** The frontend reuses
  `dictItemsByName` + `useDictItems` (role-type-dict-web D4 precedent, session
  cache): catalog 性别 / 职称 (seeded V6, ids 1004/1005). Stored values are the dict
  numeric `code`s; display falls back to the raw code. The backend stores ints
  without validating membership (D5 of role-type-dict: soft constraint, keeps user
  create working when a catalog is missing).
- **D7 — Edit dialog echo via GET detail.** One `UserDetailDto` (profile + `roleIds`)
  fetched on dialog open — same as RCP's `queryUserInfo` flow; avoids reconstructing
  state from the list row (whose roleNames are labels, not ids).
- **D8 — V26 does schema + dict seed + perms in one migration** (the V21/V23 precedent
  of a single idempotent migration carrying table + seed changes): guarded
  `ADD COLUMN IF NOT EXISTS` ×8 + the two dict catalogs (检查类型 catalog id 1021 with
  CT/MR/US/... details; 审核权限 catalog id 1022 with 报告医生/初审医生/终审医生/不参与;
  detail ids +200000 offset — clear of V6's +100000 band and V23's +1000000 band, all
  ON CONFLICT-guarded) + the two F-node inserts + admin auto-grant. Re-run is a no-op.
- **D9 — Error codes reuse existing constants**: `A0111` (duplicate username, create
  path unchanged), `A0400` (bad payload: unknown role/dep id), `A0301` (permission),
  `B0031` (missing live user). Self/seed-admin delete reuses `A0400` with a
  descriptive msg (no new ExceptionEnum constant — the enum is shared and additive
  edits there are a separate concern).
- **D10 — 检查类型 as CSV on the user row, dict codes as content** (user ruling
  2026-11-10 field semantics: the field controls which exam-type data a user sees):
  RCP stores `user_join.modalitys` as a comma-joined code string and the frontend
  splits/joins around the drawer — lumen keeps the exact shape (`varchar(128)` CSV)
  for lineage fidelity; the frontend Select submits an array the client joins. No
  FK/index: a visibility tag, not a join key.
- **D11 — 审核权限 single int** (user ruling: report-workflow authority tier):
  `rep_audit integer`, one dict code; vocabulary seeded per D8. RCP's single-select
  semantics (one tier per user) kept; a user without a tier stores NULL and renders
  the empty dash.
- **D12 — Signature is a stored PATH, not bytes** (user ruling: the handwritten
  signature image affixed to reports): `signature varchar(255)` holding the common
  upload endpoint's returned path. The report feature copies the path into its
  signature slots at sign time (RCP's `report_doc_signature`/`audit_doc_signature`
  precedent). Frontend composes a controlled upload control after the existing
  `logo-upload.tsx` pattern (client-side extension/2MB validation, upload → path →
  form value), no new transport.

## Risks / Trade-offs

- [List response shape changes (additive fields)] → All fields are additions; existing
  consumers (the current page) ignore unknown JSON keys. Test suite pins the new
  shape.
- [Client-side pagination over a growing user table] → Same trade-off as today,
  unchanged by this design; a server-paged `/page` is the recorded future direction
  (api spec scenario note), not this change.
- [dep_id left dangling when a department is deleted] → The data spec pins the read
  path (empty label, not an error); the list assembler resolves names batch-wise and
  skips dead rows naturally.
- [检查类型/审核权限 stored with no consumer yet] → Console-only surface is the
  deliberate boundary (role-type-dict D7 precedent); the radiology worklist filter and
  report signature attach arrive with their own changes. Risk is schema debt, which
  the nullable CSV/int columns bound cheaply.
- [Report signing needs TWO signature slots (报告医生 + 审核医生), the user row
  carries one per person] → Not a conflict: the report entity holds its own
  signature columns (RCP's `report_doc_signature`/`audit_doc_signature` precedent)
  and COPIES the signer's user-row path at sign time. The user-row `signature` is the
  person's master image; report slots are snapshots of who signed.
- [CSV modalitys invites inconsistent separators] → The value is written ONLY by the
  update path (client joins a Select array with `,`); RCP lineage reads tolerate the
  shape. No other writer exists in this change.
- [Seed-admin id constant] → V2's explicit-id seed makes id 1 a migration contract;
  a `WHERE deleted = 0` re-check in the guard keeps the rejection live even if a
  future migration renumbers (the constant is asserted in tests).

## Migration Plan

V26 runs on next `lumen-system` boot (Flyway, service schema `system`; V25 is taken by
the in-flight audit-log-unify-api). Rollback:
the migration is additive; a downgrade is a manual `DROP COLUMN`/delete-of-menu-rows
never exercised in lumen history — the forward path is the only supported path.
Frontend deploys independently (additive API client functions; the page rebuild
tolerates a backend not yet carrying the new fields by rendering the empty dash).

## Open Questions

(none — the field range and the protection matrix were ruled by the user
2026-11-10; everything else follows recorded precedents.)
