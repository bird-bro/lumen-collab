# Tasks

Backend scope of the coordination feature `exam-setting` (shared contracts and
rulings in `openspec/coordination/exam-setting.md`). TDD throughout: each task's
Verify names the spec-delta scenarios it turns RED→GREEN. The `exam-setting-web`
change carries the frontend tasks and starts only after this change's API is
user-verified.

## 1. Module skeleton + schema

- [x] 1.1 Add `lumen-sev/lumen-setting`: pom (bom-inherited versions), `SettingApplication`
  (`org.birdbro.lumen.setting`, scans `org.birdbro`), `application.yml` (datasource via
  env vars, `spring.flyway.schemas/default-schema: setting`, no Nacos), and register the
  module in the aggregator `pom.xml`. Extend `docs/deployment/pg-init.sql` with the
  `setting` schema + `lumen_setting_role` grants (own schema + public shared tables).
  Verify: `./mvnw compile -q` green from the aggregator; `mvn -pl lumen-setting -am`
  resolves through the reactor.
- [x] 1.2 Write `lumen-setting/src/main/resources/db/migration/V1__setting_schema.sql`:
  six `CREATE TABLE IF NOT EXISTS` (`exam_body_group`, `exam_body`, `exam_method`,
  `exam_item`, `exam_item_method`, `rep_temp`) per the exam-setting column deltas
  (serial Integer PK, EntityBase audit tail, zero physical FKs) + partial unique
  indexes (`exam_body_group(modality,name)`, `exam_item(modality,code)`,
  `exam_method(modality,code)`, all `WHERE deleted = 0`, plus
  `exam_item_method(exam_id,method_id)`). Header comment cites the rulings, no
  semicolons in comments. Verify: migration test — fresh run creates everything,
  re-run changes zero rows.
- [ ] 1.3 Entities (6, `@Data @Builder @AllArgsConstructor @NoArgsConstructor`,
  EntityBase fields) + BaseMapper mappers. Verify: `./mvnw -pl lumen-setting -am
  compile -q` green.

## 2. Body groups + bodies

- [x] 2.1 DTOs (`dto/`: `BodyGroupCreateRequest`, `BodyGroupUpdateRequest`,
  `BodyCreateRequest`, `BodyUpdateRequest`, bean validation per the deltas) +
  `ExamBodyGroupService` (concrete): list-by-modality sorted, create/update with
  per-modality duplicate-name check (A0400), delete guard — non-deleted bodies count
  in the message. TDD: RED→GREEN for the group scenarios (list order, duplicate name,
  blocked delete).
- [x] 2.2 `ExamBodyService`: `GET /list` returns up_id-nested tree scoped by
  modality(+groupId), `getById`, create/update (body's modality must match its
  group's; parent must belong to the same group), delete guard — child bodies or item
  references in the message. Controller `ExamBodyController`
  (`/api/v1/setting/exam-bodies`) + `ExamBodyGroupController`
  (`/api/v1/setting/exam-body-groups`), OrgDeviceController verb style. TDD: tree
  nesting, parent-group mismatch rejection, update clears optional code, guards.
- [x] 2.3 Perm pins: `@PreAuthorize` `setting:body-group:*` / `setting:body:*` strings
  asserted by annotation tests (sign-log-query precedent).

## 3. Methods

- [x] 3.1 DTOs + `ExamMethodService`: page (modality + name/code keyword), list
  (modality picker feed, sort order), create/update (duplicate code per modality,
  A0400), delete guard — item bindings count in the message. Controller
  `ExamMethodController` (`/api/v1/setting/exam-methods`). TDD: page filters, list
  ordering, duplicate code, blocked delete.
- [x] 3.2 Perm pins `setting:method:*` (same annotation-test pattern).

## 4. Items

- [x] 4.1 DTOs (`ItemCreateRequest`, `ItemUpdateRequest`, `ItemPageQuery`) +
  `ExamItemService`: page (modality/groupId/keyword/isCommon filters; body + method
  display names resolved server-side via joins — D2), `getById`, create/update with
  modality-dict-1021 validation (unknown value → A0400), per-modality duplicate-code
  check, item+binding rewrite in ONE transaction (failure injection test — D2/D9),
  delete (logical). TDD: every exam-setting item scenario RED→GREEN.
- [x] 4.2 Controller `ExamItemController` (`/api/v1/setting/exam-items`) + perm pins
  `setting:item:*` (annotation tests).

## 5. Report templates

- [x] 5.1 DTOs + `RepTempService`: tree (modality+type, private scoped to requester),
  page (flag=1 under upId + name filter, paged), create (type=2 binds owner_uid
  server-side; type=1 requires perms), update/delete (type=2 owner-only; non-owner
  write refused without content leak), logical delete. Controller `RepTempController`
  (`/api/v1/setting/rep-temps`). TDD: every report-template endpoint + scope
  enforcement scenario RED→GREEN.
- [x] 5.2 Perm pins `setting:temp:*` (annotation tests).

## 6. Seeds + fixture ledger

- [x] 6.1 `lumen-system/.../db/migration/V2x__exam_setting_menu.sql`: L2 「检查设置」
  group (icon medicine-box) + four C pages (perms = the contract, CONTENT-guarded
  serial inserts + setval tail), F button nodes 新建/编辑/删除 ×4 (`setting:<res>:
  {create,update,delete}`, NOT EXISTS guards) + admin auto-grant (the modality
  vocabulary needs no seed: dict 1027 is already complete per V27). Verify: seed re-execution test —
  second run is a row-count no-op (AuthDeviceRoomSeedTests pattern).
- [x] 6.2 Register the new test fixture prefix family in the smoke-sweep hard-delete
  ledger (2026-11-07 ruling) and run the configured suite:
  `./mvnw test -q` exit 0 (log path + counts reported, no full log dump).
