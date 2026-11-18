<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-item-common-filter-drop
  date: 2026-11-18
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: exam-item-common-filter-drop

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit=0) | 2/2 | PASS (`./mvnw test -q -Dsurefire.rerunFailingTestsCount=1`, 49 classes / 249 tests / 0 failures / 0 errors, exit=0) | logs/build-backend.log · logs/test-backend.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Page filter by group | IMPLEMENTED | ExamItemServiceImpl.java:79-88 (groupId → bodyIds IN branch, empty → empty page) |
| Perm-gated write | IMPLEMENTED | ExamItemController.java:49/58/68 (`@PreAuthorize` per verb, :35 list) |
| Requirement text: NO 常用项目 filter on the page query | IMPLEMENTED | ItemPageQuery.java 0 `isCommon` hits; ExamItemServiceImpl 0 hits (write side retains isCommon: entity/DTOs/impl.create/update) |

## Issues

- None blocking. Verification ran 6 full-suite attempts on the SHARED test server:
  runs 1-3 were contaminated by a concurrent foreign suite from `171.34.202.192`
  (fixture sweeps destroying each other's rows — remote stopped after run 3), runs
  4-5 hit transient WAN transport resets (`Connection reset` / EOF; logic always
  0 assertion failures), run 6 fully green. `-Dsurefire.rerunFailingTestsCount=1`
  was carried as transport-flake tolerance; run 6 needed it not (clean pass).
- Adjacent latent finding FIXED during this verification (direct change, one
  payload field): AuthExamItemTransactionTests' deliberate-failure PUT now carries
  `"isCommon":false`, so the injected binding-rewrite failure — not the
  `is_common` NOT NULL constraint — triggers the rollback the scenario asserts.

## Next

- PASS → archive (batch order: common-filter-drop → external-code-sev →
  external-code-web → polish-web → table-polish-web).
