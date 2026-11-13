<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-setting-web
  date: 2026-11-13
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "7/7"
      L3: PASS
---

# Verification: exam-setting-web

Overall: **PASS** — L1 `pnpm build` exit=0; L3 `pnpm test` exit=0 (31 files / 358 tests,
zero failures — includes the 4 new page suites 8+6+7+7 = 28 cases); every scenario in
the web-system-console delta carries component-test evidence (api seam mocked per the
no-mock-at-runtime ruling; pages wire the REAL `/api/v1/setting/**` clients from
exam-setting-web task 1.1).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (exit=0) | 10/10 | PASS (358/358) | logs/build-lumen-web.log · logs/test-lumen-web.log |

## Scenario Alignment (7/7 spec scenarios IMPLEMENTED)

| Scenario (verbatim from the delta) | Status | Evidence |
|:--|:--|:--|
| Group selection cascades the body options | IMPLEMENTED | pages/setting/exam-item/index.tsx onDrawerGroupChange (reload + stale clear); exam-item-page.test.tsx cascade case |
| Create posts to the real backend | IMPLEMENTED | api/exam-item.ts createExamItem; exam-item-page.test.tsx create case (methodIds array + refresh) |
| Left column manages groups without leaving the page | IMPLEMENTED | pages/setting/exam-body/index.tsx aside; exam-body-page.test.tsx group-create case (listBodyGroups re-called) |
| Blocked group delete shows the reason | IMPLEMENTED | exam-body-page.test.tsx blocked-delete case (message verbatim, group kept) |
| Method CRUD round-trips the real API | IMPLEMENTED | pages/setting/exam-method/index.tsx; exam-method-page.test.tsx create/edit/delete + refusal verbatim |
| Tab toggle switches scope without cross-leak | IMPLEMENTED | pages/setting/rep-template/index.tsx (scope in every call); rep-temp-page.test.tsx tab case (private tree+page, public row gone) |
| Directory node creates a child template | IMPLEMENTED | rep-template/index.tsx openNodeAdd(node); rep-temp-page.test.tsx (upId asserted + tree reload) |

Supporting evidence beyond the 7 scenarios: the display-resolved table assertions
(bodyName/methodNames/score columns), the same-group parent echo, and the strip-role
gating cases ride the same four suites (28 page cases total).

## Issues

- None. During the apply, a REAL data-loss gap surfaced through the edit-drawer RED
  test and was fixed via a coordinated 2c amendment: the item detail echo lacked
  `methodIds` (the transactional rewrite would have wiped bindings on an untouched
  form) — spec delta "Exam Item Detail Binding Ids" + backend ItemVO/assembleVOs +
  web types landed together; backend suite re-run green (204+37) afterwards.

## Next

- PASS → both stacks now carry verify.md overall=PASS (api 30/30 + web 10/10).
  Batch-archive per the coordination gate: `/opsx:archive exam-setting-api` +
  `/opsx:archive exam-setting-web`.
