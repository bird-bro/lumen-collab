# Tasks

Backend first (the frontend builds against the real API — no mock layer, user ruling
2026-11-05). This change is the `-api` scope of the coordination feature; the
`user-edit-delete-profile-web` change carries the frontend tasks.

## 1. Migration V26 (schema + dict seed + perm seed)

- [ ] 1.1 Write `V26__user_profile_columns.sql`: eight guarded `ADD COLUMN IF NOT EXISTS` on
  `user_info` (phone/email/gender/title/dep_id + FK/modalitys/rep_audit/signature), the two
  dict catalogs with deterministic ids (检查类型 id 1021: CT/MR/US/… details; 审核权限
  id 1022: 报告医生/初审医生/终审医生/不参与; detail ids +200000 band, ON CONFLICT-guarded,
  V23 house style), and the two F-node inserts (编辑用户 `system:user:update` sort 5,
  删除用户 `system:user:delete` sort 6, parent id 2) with the admin auto-grant NOT-EXISTS
  guard — comment lines carry no semicolons (V21 discipline). Verify:
  `AuthUserMigrationSeedTests`-style test asserts columns + catalogs + nodes exist after
  migration and re-run is a row-count no-op.
- [ ] 1.2 Extend `UserInfo` entity with the eight nullable fields (Lombok combo
  `@Data @Builder @AllArgsConstructor @NoArgsConstructor` unchanged). Verify:
  `./mvnw -pl lumen-system -am test -q` passes with existing suites green.

## 2. Read surface (list extension + detail)

- [ ] 2.1 Add `roleNames`/`depName`/`phone`/`email`/`gender`/`title`/`depId`/`modalitys`/
  `repAudit`/`signature` to `UserItemDto`; write the batch name-resolution in
  `UserInfoServiceImpl.listUsers` as a NEW private helper (never-modify: existing method
  signature untouched, body extension allowed only if byte-stable callers unaffected —
  prefer assembling in a new method the controller keeps calling). Verify: RED→GREEN test
  asserting a user with two roles and a dep renders both role names + dep name, and a user
  with none renders empty/absent — no N+1 (spy the mapper call counts).
- [ ] 2.2 Add `GET /{id}` detail: `UserDetailDto` (profile + roleIds + modalitys CSV +
  repAudit + signature), new `UserInfoService.getUserDetail(Integer)` + impl, controller
  endpoint with the `system:user:list` gate. Verify: RED→GREEN test — detail of a live user
  returns profile + roleIds and NO password; detail of a dead id answers B0031.

## 3. Update + delete endpoints

- [ ] 3.1 Write `UpdateUserRequest` DTO (name/phone/email/gender/title/depId/roleIds/
  modalitys/repAudit/signature; no username, no password) with bean validation. Verify:
  compiles; validation messages match the house style (`field must not be blank`
  precedent).
- [ ] 3.2 Implement `UserInfoService.updateUser(Integer, UpdateUserRequest)` (NEW interface
  method): partial update + roleIds full-replace in one transaction, `depId`/role id
  existence checked A0400, username ignored. Controller `PUT /{id}` pinned
  `system:user:update`. Verify: RED→GREEN tests — update changes profile fields; roleIds
  replace bindings (old binding logically dead); absent roleIds leaves bindings; bad role id
  rejects A0400 and changes NOTHING; username in body does not overwrite.
- [ ] 3.3 Implement `UserInfoService.deleteUser(Integer)` (NEW interface method): self-delete
  + seed-admin (id 1) A-class rejections (descriptive msg), else logical delete + live
  user_role unbind, one transaction. Controller `DELETE /{id}` pinned `system:user:delete`;
  `@Log` annotation (删除). Verify: RED→GREEN tests — self-delete rejected; id-1 rejected;
  regular delete hides from list/detail, unbinds roles, username re-creatable.

## 4. Controller wiring + full-suite verification

- [ ] 4.1 Wire the three new controller methods (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`)
  with `@Log` annotations (修改/删除) and the doc annotations matching the existing four.
  Verify: `./mvnw -pl lumen-system -am test -q` full module green.
- [ ] 4.2 Run the aggregator build and the auth test family:
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q`
  from `lumen-sev/`. Verify: zero failures, all prior suites untouched-green.

## 5. Coordination bookkeeping

- [ ] 5.1 Create `openspec/coordination/user-edit-delete-profile.md` from `_template.md`:
  registry (api/web), shared design summary (D1–D9 pointers), gate. Verify: file exists and
  names both changes.
- [ ] 5.2 Create the sibling change `user-edit-delete-profile-web` (proposal/spec deltas
  against `web-system-console`) per the coordination doc. Verify:
  `openspec status --change user-edit-delete-profile-web` shows the artifacts.

## Workflow follow-up

- Frontend implementation happens in `user-edit-delete-profile-web` (after the backend API
  is user-verified, per the cross-stack workflow: backend first, then the web change).
- `/opsx:verify` on this change, then archive both changes together once the gate is
  SATISFIED (all registered changes verify PASS), then `openspec sync` the specs.
