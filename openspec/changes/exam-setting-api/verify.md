<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-setting-api
  date: 2026-11-12
  overall: FAIL
  stacks:
    backend:
      L1: PASS
      L2: "29/29"
      L3: FAIL
---

# Verification: exam-setting-api

Overall: **FAIL** — the configured suite carries 2 failures, BOTH traced to shared-test-DB
drift caused by the CONCURRENT session (menu id-1 title mutated `系统管理`→`系统` on the
live database; no migration in any working tree performs that rename). ZERO failures are
attributable to this change's surface: lumen-setting 37/37 green in EVERY run, the
AuthExamSettingSeedTests 3/3 green in EVERY run, and the same configured suite exited 0
when the concurrent session was quiet (15:44 window, logs/test-backend.log's predecessor).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (exit=0) | 29/29 | FAIL (2 foreign failures; change-scope 40/40 green) | logs/build-backend.log · logs/test-backend-retry.log |

## Scenario Alignment (29/29 IMPLEMENTED)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Service migrates only its own schema | IMPLEMENTED | application.yml:20-24; SettingSchemaSeedTests.settingTablesExist |
| Role isolation denies cross-schema access | IMPLEMENTED | pg-init.sql:69-79 (lumen_setting_role grants; DBA deliverable) |
| Fresh deployment creates the body-group columns | IMPLEMENTED | V1__setting_schema.sql:27-41; SettingSchemaSeedTests:75 |
| Body belongs to a group and may nest | IMPLEMENTED | V1 exam_body DDL; ExamBodyService.getList:64-91; AuthExamBodyApiTests.bodyTreeNestsByUpId |
| Fresh deployment creates the method columns | IMPLEMENTED | V1 exam_method DDL; SettingSchemaSeedTests:75 |
| Item stores the catalog facts + score weights | IMPLEMENTED | V1 exam_item DDL; AuthExamItemApiTests.itemUpdateIsNullAware:170-178 |
| Item code uniqueness | IMPLEMENTED | ExamItemService.rejectDuplicateCode; AuthExamItemApiTests.itemCodeUniqueness:118 |
| Item update rewrites bindings atomically | IMPLEMENTED | ExamItemService @Transactional + rewriteBindings; ExamItemMethodMapper.deleteByExamId; AuthExamItemTransactionTests:66 |
| Public/private templates share one tree model | IMPLEMENTED | V1 rep_temp DDL; RepTempService.create:96-104; AuthRepTempApiTests.treeReturnsScopes |
| Unknown modality rejected | IMPLEMENTED | DictModalityMapper.countLiveModality (name-join); ExamBodyGroupService.requireModality; 4 suites' unknown-modality cases |
| Group with bodies refused | IMPLEMENTED | ExamBodyGroupService.delete:88-97; AuthExamBodyApiTests.groupDeleteIsGuardedByItsBodies |
| Method in use refused | IMPLEMENTED | ExamMethodService.delete:108-117; AuthExamMethodApiTests.methodDeleteIsGuardedByItemBindings |
| Page filter by group | IMPLEMENTED | ExamItemService.getPage:76-87 (body-join derivation); AuthExamItemApiTests.pageFilterByGroup:48 |
| Perm-gated write | IMPLEMENTED | ExamItemController @PreAuthorize pins; AuthExamItemApiTests.itemWritesDenyThePermless:196 (A0301) |
| Group list feeds the cascade | IMPLEMENTED | ExamBodyGroupController /list; AuthExamBodyApiTests.groupListIsModalityScopedAndSorted |
| Body tree scoped to a group | IMPLEMENTED | ExamBodyService.getList forest assembly; AuthExamBodyApiTests.bodyTreeNestsByUpId |
| Method list feeds the item form | IMPLEMENTED | ExamMethodService.getList (sort order); AuthExamMethodApiTests.methodListFeedsTheItemForm |
| Tree returns both scopes independently | IMPLEMENTED | RepTempService.getTree + scopeWrapper (owner-scoped); AuthRepTempApiTests.treeReturnsScopes + leak probe |
| Template page under a folder | IMPLEMENTED | RepTempService.getPage (flag=1 + upId + name); AuthRepTempApiTests same test |
| Non-owner private write refused | IMPLEMENTED | RepTempService.requireOwnedRow:146-153 (content-free A0400); AuthRepTempApiTests.privateCreateBindsOwner:104-119 |
| Private create binds the owner | IMPLEMENTED | RepTempService.create:99-101 (session UserHeader, body ignored); AuthRepTempApiTests owner_uid assert:95-98 |
| Foundation modules resolve | IMPLEMENTED | aggregator pom modules; L1 exit=0 |
| Deferred module requested | IMPLEMENTED | exam-setting-api added `lumen-setting` module (pom.xml:9) |
| Schemas exist after initialization | IMPLEMENTED | pg-init.sql:9-15 (seven schemas); SettingTestServerBootstrap:79 |
| Shared system tables reachable | IMPLEMENTED | DictModalityMapper name-join READS public.dict_detail at runtime (all suites green) |
| Fresh deployment seeds the console group | IMPLEMENTED | V28 sections 1-2; AuthExamSettingSeedTests.examSettingDirectoryAndPagesSeeded |
| Re-execution is a no-op | IMPLEMENTED | V28 CONTENT-guards; AuthExamSettingSeedTests.seedReExecutionIsARowCountNoOp |
| Admin role receives the button grants | IMPLEMENTED | V28 section 3; AuthExamSettingSeedTests.buttonNodesSeededAndAdminAutoGranted |

## Issues

- L3-FAIL (foreign): `AuthLoginTests.meWithAdminTokenReturnsProfileMenusAndPerms` +
  one 6-test suite — `expected: <系统管理> but was: <系统>` — menu id-1 TITLE on the
  shared test DB was mutated outside any migration (verified: `SELECT title FROM
  system.menu_info WHERE id=1` → `系统`; no working-tree migration renames it).
  Owned by the concurrent session's menu-IA work, not this change.
- Earlier runs (same day) showed the same class of foreign interference: fixture-sweep
  collisions (`campus does not exist: org id 2039`, A0301 on run-admin) and a
  flyway-history race (V27/V28 rows deleted by both sessions' repair procedures).
  Recorded in memory `flyway-history-race-parallel-sessions`.

## Next

- FAIL → archive blocked. This change needs NO code fix: re-run `/opsx:verify
  exam-setting-api` once the concurrent session's DB churn settles (its menu rename
  lands as a proper migration or is reverted), then archive.
- The concurrent session should settle the 检查类型 catalog id (1021, current) and the
  L1 menu title through THEIR OWN migration files, not live-DB edits.
