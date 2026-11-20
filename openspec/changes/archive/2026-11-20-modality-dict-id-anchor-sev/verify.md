<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: modality-dict-id-anchor-sev
  date: 2026-11-20
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "6/6"
      L3: PASS
---

# Verification: modality-dict-id-anchor-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit=0) | 6/6 | PASS (`./mvnw test -q`, exit=0; 全 reactor 75 类 / 361 tests / 0 failures / 0 errors / 0 skipped) | logs/build-backend-verify.log, logs/test-backend-full-verify.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Unknown modality rejected (exam-setting) | IMPLEMENTED | ExamBodyGroupServiceImpl.java:105-109（countLiveModality==0 → A0400 抛出）；SQL id 锚 DictModalityMapper.java:31-35；四处 api 拒绝用例全过 AuthExamItemApiTests:106 / AuthExamMethodApiTests:110 / AuthExamBodyApiTests:87 / AuthRepTempApiTests:149 |
| Catalog rename does not affect validation (exam-setting) | IMPLEMENTED | SQL 无目录名依赖（DictModalityMapper.java:31-35）——结构保证；DictModalityMapperTests 锁锚（RED→GREEN，logs/test-mapper-red.log → test-mapper-green.log） |
| Fresh database carries the starter dictionary (system-data) | IMPLEMENTED | V27__org_device_room.sql:127-134（1024 目录 + 1024001+ 确定性 detail 带，ON CONFLICT DO NOTHING）；V26__user_profile_columns.sql:103（1022 审核权限）；共享库实查 2026-11-20：17 目录全 enable=1/deleted=0 |
| Re-run is a no-op (system-data) | IMPLEMENTED | 同上 ON CONFLICT 守卫语句；SettingSchemaSeedTests 5/5 + AuthDeviceRoomSeedTests 6/6（受影响套件跑 logs/test-affected-suites.log，44/44） |
| Exam-type vocabulary renders abbreviations (system-data, id-1024 锚) | IMPLEMENTED | DictModalityMapperTests.liveTokenResolvesThroughCatalogId（CT/US→1）；测试基座 1024 带直愈 SettingIntegrationTestBase.java:118+；库实查词表 DR/CT/MR/DSA/US/ENDO 六项启用 |
| Audit-authority vocabulary carries the three tiers (system-data) | IMPLEMENTED | V26__user_profile_columns.sql:103,111（1022 目录 + 分层 detail）；本变更未触碰该场景，全 reactor 套件绿（AuthUserProfileSeedTests 在内） |

## Issues

- ~~非阻断：拒绝文案仍引用作废目录「字典 1027」~~ **已修（用户裁决 2026-11-20）**：ExamBodyGroupServiceImpl.java:108 文案改为「不在模态词表（字典 1024）中」；修正后 5 套件重跑 24/24 全绿（logs/test-message-fix.log，exit=0）。

## Next

- PASS -> 与 `modality-dict-id-anchor-web`（已 PASS）双栈批量归档（跨栈 gate）。
