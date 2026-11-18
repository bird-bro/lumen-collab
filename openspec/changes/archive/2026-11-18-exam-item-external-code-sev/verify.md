<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-item-external-code-sev
  date: 2026-11-18
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "7/7"
      L3: PASS
---

# Verification: exam-item-external-code-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit=0) | 7/7 | PASS (`./mvnw test -q -Dsurefire.rerunFailingTestsCount=1`, 49 classes / 249 tests / 0 failures / 0 errors, exit=0) | logs/build-backend.log · logs/test-backend.log |

## Scenario Alignment

Exam Item Columns:

| Scenario | Status | Evidence |
|:--|:--|:--|
| Item stores the catalog facts and the score weights | IMPLEMENTED | ExamItemServiceImpl.java:119-122 (create BeanUtil copy), 138-153 (update .set chain); group derivable via body only (VO assembly :260-265) |
| Item code uniqueness | IMPLEMENTED | rejectDuplicateCode ExamItemServiceImpl.java:206-217 + `exam_item_live_code_uidx` partial unique (V29, referenced in V37 header) |
| Item stores the external mapping code | IMPLEMENTED | V37__exam_item_external_code.sql (`external_code varchar(32)` nullable); ExamItem.java:56; echo rides `ItemVO extends ExamItem` (dto/ItemVO.java:21) — asserted AuthExamItemApiTests:230-236 |
| Duplicate external mapping codes are accepted | IMPLEMENTED | V37 carries NO index/uniqueness (deliberate, 打包计费); the only uniqueness guard is on 院内 code (impl:206-217) |

Exam Item Endpoints:

| Scenario | Status | Evidence |
|:--|:--|:--|
| Page filter by group | IMPLEMENTED | ExamItemServiceImpl.java:79-88 |
| Keyword matches the external mapping code | IMPLEMENTED | ExamItemServiceImpl.java:92 (`.or().like(ExamItem::getExternalCode, …)`) |
| Perm-gated write | IMPLEMENTED | ExamItemController.java:49/58/68 |

## Issues

- None blocking. Verification ran 6 full-suite attempts on the SHARED test server
  (see exam-item-common-filter-drop/verify.md Issues for the full run history:
  foreign-suite contamination runs 1-3, WAN transport resets runs 4-5, run 6 fully
  green with 0 failures / 0 errors). This change's domain (setting package incl.
  AuthExamItemApiTests' externalCode round-trip/echo/uniqueness cases) was GREEN in
  every one of the six runs.
- Adjacent latent finding FIXED during this verification (direct change): the
  AuthExamItemTransactionTests deliberate-failure payload now carries
  `"isCommon":false` — the injected binding-rewrite rollback is genuinely covered
  (injected-failure marker verified in the response).

## Next

- PASS → archive (batch order: common-filter-drop → external-code-sev →
  external-code-web → polish-web → table-polish-web).
