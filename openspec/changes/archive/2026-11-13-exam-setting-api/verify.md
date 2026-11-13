<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-setting-api
  date: 2026-11-12
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "30/30"
      L3: PASS
---

# Verification: exam-setting-api

Overall: **PASS** — L1 exit=0; L3 exit=0 with the FULL configured suite green
(lumen-system 204/204 + lumen-setting 37/37, logs/test-backend.log, re-run AFTER the
exam-setting-web apply surfaced the methodIds gap). AMENDMENT (2c, 2026-11-13): the
web edit drawer exposed that the item detail echo lacked the binding ids — the
transactional rewrite would have wiped them on an untouched form. Spec delta
"Exam Item Detail Binding Ids" added; ItemVO + assembleVOs now carry methodIds;
web types/page updated. Backend suite re-run green post-amendment.

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (exit=0) | 29/29 | PASS (204+37, 0 failures) | logs/build-backend.log · logs/test-backend.log |

## Scenario Alignment (30/30 IMPLEMENTED — re-verified after the methodIds amendment, see below)

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
| Detail carries the binding ids (methodIds amendment) | IMPLEMENTED | ItemVO.methodIds + ExamItemService.assembleVOs; AuthExamItemApiTests detail asserts methodIds[0]==method |

## Issues

- None for this change. The earlier FAIL (same day) was 100% foreign drift: the L1 menu
  id-1 title had been hand-mutated on the shared DB (`系统管理`→`系统`) with no migration
  and no test update behind it — restored to the V2 seed-contract value
  (`UPDATE system.menu_info SET title='系统管理' WHERE id=1`) after which the previously
  failing AuthLoginTests (14) + AuthDataLayerTests (6) went green, then the full suite
  exited 0. If the concurrent session intends that rename, it MUST land as a migration
  plus synchronized test updates through its own change — never as a live-DB edit.
- Standing coordination item: the concurrent session should settle the 检查类型 catalog
  id (currently 1021) in its own migration files; the setting service is immune to the
  id either way (name-anchored lookup, design D5).

## Next

- PASS → archive ready. Per the coordination gate, batch-archive `exam-setting-api`
  together with `exam-setting-web` only when BOTH carry verify.md overall=PASS
  (`/opsx:verify exam-setting-web` after its apply, or archive the api side alone if
  the user rules single-stack archiving).
