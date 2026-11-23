# Batch 2 Brief — contracts + kernel + US plumbing (tasks 2.1–2.3)

Change: number-rule-modality-scope. Batch 1 (lumen-system, UNCOMMITTED in the working
tree) is done and under review — do NOT touch lumen-system, do NOT commit anything.
You touch ONLY: `lumen-sev/lumen-feign`, `lumen-sev/lumen-core`, `lumen-sev/lumen-us`.

## Read first

1. Delta specs (acceptance): `openspec/changes/number-rule-modality-scope/specs/us-station/spec.md`
   (Registration Surface + "Modality reaches the issuer port" scenario); design D4–D6 in
   `openspec/changes/number-rule-modality-scope/design.md`.
2. Contract: `lumen-feign/src/main/java/org/birdbro/lumen/feign/number/NumberIssueRequest.java`
   (+ `NumberApi.java`, `NumberApiFallback.java` — signature must NOT change).
3. Kernel: `lumen-core/src/main/java/org/birdbro/lumen/core/number/NumberIssuerPort.java`,
   `entity/ExamItemRef.java`, `service/impl/ExamOrderRegistrationServiceImpl.java` (the
   `numberIssuerPort.issue("登记号")` call site), and tests
   `lumen-core/src/test/java/org/birdbro/lumen/core/NumberIssuerPortTests.java` +
   `FakeNumberIssuerPort.java` (a port IMPLEMENTER — must compile after the overload).
4. US: `lumen-us/src/main/java/org/birdbro/lumen/us/adapter/NumberIssuerPortAdapter.java`
   (the other port implementer), `service/impl/RegistrationOrderServiceImpl.java`
   (`.examItems(order.getExamItems())` pass-through at ~line 246 — verify modality rides),
   and `lumen-us/src/test/java/org/birdbro/lumen/us/` test base conventions.

## Task 2.1 — Feign contract field

- `lumen-feign` `NumberIssueRequest`: add optional `modality` (String, null = legacy global).
  `NumberApi` method signature + fail-fast fallback + `@FeignClient` annotation UNTOUCHED.
  Pure additive — rebuild-safe for consumers still on the old jar.
- (System-side controller DTO was already extended in batch 1 — do not touch lumen-system.)

## Task 2.2 — Kernel port overload + jsonb widening

TDD RED first in `lumen-core` tests (`NumberIssuerPortTests` / registration tests; follow
`KernelIntegrationTestBase` conventions):

- `ExamItemRef`: add `private String modality;` — jsonb widening; pre-existing rows without
  the key must read as null (round-trip test proves it).
- `NumberIssuerPort`: ADD `String issue(String ruleType, String modality);` (abstract
  overload). The EXISTING single-arg abstract method keeps its signature and contract
  (javadoc: null modality = family global rule). TWO implementers must compile:
  `FakeNumberIssuerPort` (core test) and `lumen-us` `NumberIssuerPortAdapter` — the Fake
  records/forwards the two-arg call for assertions.
- `ExamOrderRegistrationServiceImpl`: the accession issuance call becomes the two-arg
  overload with the FIRST exam item's modality if non-blank, else null (CollUtil.first +
  StrUtil blank-safe; do NOT scan later items — mixed-modality orders are out of scope,
  design D5). Blank-family behavior unchanged.
- Kernel port contract test: single-arg port call = null modality on the wire; two-arg with
  token reaches the port; first-item derivation (multi-item order uses item 1's token only).

## Task 2.3 — US adapter + create passthrough

TDD RED first (lumen-us tests, follow `UsIntegrationTestBase`):

- `NumberIssuerPortAdapter`: implement the two-arg overload — builds `NumberIssueRequest`
  with `ruleType` + `modality` (null stays null on the wire) — and the single-arg method
  delegates to it (or both delegate to a shared private build; single-arg signature/annotation
  semantics unchanged). Fail-fast contract unchanged: null envelope/data → B0001.
- Passthrough proof: a create body whose item refs carry `modality` produces
  `exam_order.exam_items` jsonb WITH modality intact AND the port called with the first
  item's token. The service already passes `order.getExamItems()` by reference
  (`RegistrationOrderServiceImpl:246`) — verify, and only add code if the ride-through
  is actually broken.
- The modality token originates from the client (the setting catalog it renders) — the US
  layer must NOT validate or default it (unknown token = issuer-side global fallback).

## House rules (non-negotiable)

- Same JDK 11 export discipline; Hutool over hand-rolled; `@author birdbro` headers;
  never modify an existing public method signature — overloads only.
- Tests hit the shared team test server (`.env-test.local`): hard-DELETE sweeps only,
  new fixture families registered in the base-class prefix list, WAN flake → rerun the
  class once before diagnosing; `-Dsurefire.rerunFailingTestsCount=1` on suite-ish runs.
- Smallest affected tests only, e.g.:
  `cd /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev && export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -pl lumen-core,lumen-us,lumen-feign -am -q -Dtest='NumberIssuer*,Registration*,*Port*' -Dsurefire.failIfNoSpecifiedTests=false`
  (adjust the -Dtest pattern to the real class names; redirect to a log; report exit + counts).
- DO NOT commit. Parallel-session hazard: another change (`system-drawer-status-switch-web`)
  may hold dirty files — if `git status` shows foreign modified files, leave them alone.

## Report

Write `openspec/sdd/reports/number-rule-modality-scope-b2.md` (<80 lines): implemented
items, RED/GREEN evidence (command + assertions + counts + exit), files changed,
self-review findings, concerns. Reply ≤15 lines: Status / summary / report path.
