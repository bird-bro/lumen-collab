# Batch 2 Review: rep-template-structured-content (tasks 2.1–2.2)

Commit 8e8b222 · 2026-11-18 · Verdict: **APPROVED** · 0 Critical / 0 Important / 0 Minor
(报告由 orchestrator 代 reviewer 落盘 — reviewer 在回复中给出全文,未写文件)

Scope note (not a finding): delta "Invalid content rejected" names "an empty sections
array" for create/update; update-path exercise of that one kind was skipped per the
batch brief (briefs/batch2-backend-api-tests.md:20 lists exactly the 5 implemented
kinds). It rides the shared @Valid cascade (TempUpdateRequest.java:49-50), whose
removal would already fail the PUT blank-name/code/label cases — nothing changes
next; task 4.2 awareness only.

## Special gates (6/6 PASS)

1. **纯测试提交: PASS.** diff = 1 file, +213, test-only (RepTempContentApiTests.java:
   4 tests + updateBody helper :462 + javadoc note). Post-commit git status intact:
   exam-method 6-file series + pom.xml, untracked V38 — nothing swallowed.
2. **M1 落实: PASS.** structuredUpdateRoundTripsContentThroughWrapperMapping (:233)
   PUTs NEW content, re-asserts DB jsonb_typeof(content)='object' + finding/impression
   NULL (:256-262) — the wrapper-route probe (RepTempServiceImpl.java:140,
   CONTENT_HANDLER_MAPPING) — then page-verifies NEW content verbatim incl. null normal.
3. **M3 落实: PASS.** updatePathRejectsInvalidContentNamingTheField (:295-362) = all
   5 brief kinds via PUT, envelope A0400 + msg naming the field (live log :89/:99);
   explicitNullImpressionRoundTripsAsJsonNull (:365) asserts has("impression")+
   isNull() on create (:380-382) and update (:397-399).
4. **边缘用例: PASS.** emptyMeasuresSectionRoundTripsAsArray (:409) — ENDO-style
   "measures":[] round-trip, isArray() + size()==0 (:425-427).
5. **测试真实性: PASS.** Real HTTP + shared-PG; page reads guard total==1; run-1 RED
   (/tmp/batch2-test.log:303-305) was the implementer's own fixture JSON (A0427),
   committed body well-formed, run 2 green.
6. **2.2 结论: PASS.** All rejections reuse A0400; errors spec already covers A-class
   + descriptive msg (errors/spec.md:19-21); no errors delta — matches code facts.

## Spec compliance / Quality / TDD form

- "Template payloads carry the content object" fully covered (create batch 1 :76-105 +
  update :233-290 + prose content:null :222-226); exclusivity re-proven on update;
  invalid-content create 6 kinds + update 5 kinds; full-replace null + tree
  content-free were batch 1. Missing/Extra/Misunderstood: none in scope.
- Assertions exact-value (verbatim strings, jsonb_typeof, has()+isNull(),
  isArray()+size()); hard-delete sweep registered ut模板_ family, rep_temp=0 confirmed.
- Coverage-completion batch: no assertion weakened; surefire RepTempContentApiTests
  7/0/0, AuthRepTempApiTests 3/0/0, AuthRepTempPermPinTests 3/0/0.
