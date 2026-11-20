# Batch 2 Report — number-rule-modality-scope (tasks 2.1–2.3)

Status: DONE_WITH_CONCERNS (one in-batch root fix beyond the brief's file list — see Finding F1)

## Implemented

- **2.1 Feign contract** (`lumen-feign/.../number/NumberIssueRequest.java`): additive
  optional `modality` (javadoc mirrors the owner-side manage DTO batch 1 wrote).
  `NumberApi` signature, `NumberApiFallback`, `@FeignClient` UNTOUCHED (diff = 1 file,
  field-only). Verified by reactor compile (in every `-am` run below).
- **2.2 Kernel** (lumen-core):
  - `ExamItemRef` gains `modality` (jsonb widening, null = global).
  - `NumberIssuerPort` gains abstract `issue(String ruleType, String modality)`; the
    single-arg method keeps signature + javadoc'd null-modality contract.
  - `FakeNumberIssuerPort` implements the overload and records `LAST_ISSUED_MODALITY`;
    single-arg delegates with null.
  - `ExamOrderRegistrationServiceImpl` issues two-arg, modality =
    `firstExamItemModality(...)` = `CollUtil.getFirst` + `StrUtil.trimToNull`
    (first item only — design D5; never scans later items).
- **2.3 US** (lumen-us): `NumberIssuerPortAdapter` implements the overload — builds
  `NumberIssueRequest` with ruleType + modality verbatim (null stays null), single-arg
  delegates; B0001 fail-fast guard unchanged. Create passthrough needed NO code change:
  `RegistrationOrderServiceImpl:110` passes `request.getExamItems()` by reference and
  `RegistrationCreateRequest.examItems` is the kernel `List<ExamItemRef>` — the proof
  test rides it green.

## TDD evidence

- RED-1 (compile, core): `/tmp/b2-core-red.log` exit 1 — `找不到符号: 变量
  LAST_ISSUED_MODALITY` (Fake) + `modality(...)` builder missing (11 errors).
- RED-2 (behavioral, core): `/tmp/b2-core-red2.log` exit 1 — 11 run, 2 failures:
  `expected: <US> but was: <null>` on `registrationIssuesTheAccessionUnderTheFirstItemsModality`
  and `multiItemOrderIssuesWithTheFirstItemsModalityOnly` (service still single-arg).
- RED-3 (compile, us): `/tmp/b2-us-red.log` exit 1 — `NumberIssuerPortAdapter不是抽象的,
  并且未覆盖...抽象方法issue(java.lang.String,java.lang.String)`.
- RED-4 (regression, core): `/tmp/b2-final.log` exit 1 — HisTwoPhaseIntakeTests
  `manualConfirmCreatesTheOrderAtRegistered:80` / `cachedApplyCorrectedBeforeCommit:114`:
  `ClassCastException: LinkedHashMap cannot be cast to ExamItemRef` (see F1).
- GREEN: final sweep `/tmp/b2-final2.log` exit 0 — core NumberIssuerPortTests 6/6,
  ExamOrderAggregateTests 5/5, HisTwoPhaseIntakeTests 4/4, RegistrationHookTests 2/2,
  PatientRegistryPortTests 2/2, ExamNumberIssuerTests 3/3; then `/tmp/b2-us-final.log`
  exit 0 — UsAdapterTests 16/16, UsRegistrationApiTests 13/13 (real HTTP, shared test
  server). Total 51 tests, 0 failures, 0 errors. No reruns consumed
  (`rerunFailingTestsCount=1` was armed, never needed).

## Files changed (12 + 1 new)

- `lumen-sev/lumen-feign/.../feign/number/NumberIssueRequest.java`
- `lumen-sev/lumen-core/.../entity/ExamItemRef.java`, `entity/ExamOrder.java`,
  `entity/ExamApplyCache.java`, `number/NumberIssuerPort.java`,
  `service/impl/ExamOrderRegistrationServiceImpl.java`
- `lumen-sev/lumen-core/.../mapper/ExamItemRefListJsonbTypeHandler.java` (NEW — F1)
- `lumen-sev/lumen-core/src/test/...`: `NumberIssuerPortTests.java` (+4 tests, +1
  characterization line), `ExamOrderAggregateTests.java` (+2 round-trip tests),
  `FakeNumberIssuerPort.java`
- `lumen-sev/lumen-us/.../adapter/NumberIssuerPortAdapter.java`
- `lumen-sev/lumen-us/src/test/...`: `UsAdapterTests.java` (+4 wire tests + captor
  helper), `UsRegistrationApiTests.java` (+1 e2e passthrough test)
- `openspec/changes/number-rule-modality-scope/loop-state.yaml` (retry_count: 1)
- NOT touched: lumen-system, lumen-web, `NumberApi`/fallback, foreign dirty files
  (`progress.md`, prototype html, batch3 brief).

## Finding F1 (the concern): mapper-read jsonb lists are untyped — fixed in-batch

The brief's STOP trigger fired exactly: after the derivation became the kernel's FIRST
typed element access, `HisTwoPhaseIntakeServiceImpl.confirmApply` CCE'd 100% of the time
— it feeds the mapper-read `exam_apply_cache.target` straight into `registerOrder`. Root
cause is PRE-EXISTING and latent: MP 3.5.3.1 `JacksonTypeHandler` constructs field
handlers through the raw `(Class)` constructor, so collection fields parse as
`List<LinkedHashMap>` (bean fields like `patient_snapshot` were always fine; nothing
ever accessed collection elements before). Fix: new kernel-owned
`ExamItemRefListJsonbTypeHandler` (pins the `ExamItemRef[]` element type on parse; write
path untouched — same Jackson serialization) registered on the TWO collection fields
(`ExamOrder.examItems`, `ExamApplyCache.target`). The HIS tests are the regression RED;
the aggregate legacy-row test was upgraded to assert TYPED reads (`first.getModality()`).

## Self-review notes

- Exactly two port implementers repo-wide (grep): Fake + US adapter. Patient's
  `PatientArchiveServiceImpl:144` calls its OWN `patientNumberService.issue` — not this
  port; stays single-arg global, untouched.
- Cosmetic wire delta after the typed handler: HIS-created orders now store
  `"modality":null` in `exam_items` (was: key absent). Reads identical (null). No
  consumer asserts key-absence.
- No new US fixture family: the e2e test reuses the existing sweep keys (stub uid band +
  `us3` name marker + `US3-` accession + `UsRegistrationFixtures.registerOrder`).
  Kernel tests are `@Transactional` (rollback). No prefix-list backfill owed.
- `StrUtil.blankToNull` does not exist in Hutool 5.8.20 — used `trimToNull` (batch 1's
  own idiom). One wasted compile cycle logged in RED/GREEN trail.

## Concerns for the coordinator

- F1 fix rides 2 `@TableField` annotation changes in kernel entities — outside the
  brief's named file list but kernel-scoped, root-cause, and regression-proven; review
  the handler class javadoc for the reasoning.
- Known MP limitation remains for ANY FUTURE collection jsonb field: it must get a
  dedicated typed handler (recorded here; no action now).
