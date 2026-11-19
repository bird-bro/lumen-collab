# Tasks — lumen-core-registration-kernel

## 1. Module skeleton + canonical DDL

- [x] 1.1 RED (structure): `KernelModuleArchitectureTests` — asserts the packaged jar
      has no `Main-Class`, no `@SpringBootApplication` in main sources, no
      `org.springframework.web`-annotated controller types in
      `org.birdbro.lumen.core` (spec: Library is not runnable / HTTP surface
      ownership). Run: fails (module absent).
- [x] 1.2 GREEN: `lumen-core` module — pom (parent aggregator, deps via `lumen-bom`:
      framework, common, mybatis-plus, hutool; no spring-boot plugin), aggregator
      `<module>lumen-core</module>` after `common`; package root
      `org.birdbro.lumen.core`. Verify: `./mvnw compile -q` exit 0, structure test
      green.
- [x] 1.3 Canonical DDL: `lumen-core/src/main/resources/db/kernel/
      V1__exam_kernel_registration.sql` — `exam_order` (编号段/状态段/申请段/取消段 +
      `patient_uid` NOT NULL + jsonb snapshot + `channel` default 本地申请 +
      EntityBase audit set), `exam_execution`, `exam_report` (status +
      completion-source), `exam_apply_cache` (source/target jsonb, status,
      his_id/apply_no). Header names the vendored-adoption rule. Verify: DDL parses
      (test applies it — see 2.x). (REWOUND 2026-11-18: the `exam_number_rule` table
      and its seeds LEFT the kernel for the platform's system schema — user ruling,
      `number-rule-unification`; the canonical file now carries four tables.)

## 2. Test host + aggregate persistence

- [x] 2.1 Test host: test-sources `TestKernelApplication` (`@SpringBootApplication`
      + `@MapperScan`, main disabled from jar) + `KernelIntegrationTestBase`
      (shared test DB, `spring.flyway.schemas=core`, locations
      `classpath:db/kernel`, `.env-test.local` credentials, Hikari keepalive —
      lumen-system conventions). Verify: context boots, DDL applies to `core`.
- [x] 2.2 TDD aggregate: `ExamOrderAggregateTests` — RED `aggregatePersistsAsFourLinkedMembers`
      (four rows, `patient_uid` NOT NULL) + `channelReservesTheRemoteValue`; GREEN
      entities (`ExamOrder`/`ExamExecution`/`ExamReport`, OPS Lombok combo) +
      mappers + `insertWithMembers` single-tx service + jsonb
      `PatientSnapshot` `Types.OTHER` handler + `BeanUtil.copyProperties` idiom.
- [x] 2.3 TDD snapshot guard: `SnapshotRefreshGuardTests` — RED
      `snapshotRefreshGuardedByReportState` (refresh updates while report not 已出;
      refuses once 已出, snapshot byte-identical); GREEN refresh service on the
      aggregate.

## 3. Document state machine

- [x] 3.1 TDD: `ExamOrderStateMachineTests` — RED `bookingEntersAtBooked` /
      `walkInAndHisConfirmEnterAtRegistered` / `happyPathAdvancesThroughEachStage` /
      `cancellationBranchesSplitByStage` (含 已出-report cancel refusal) /
      `illegalTransitionRejected` (A0400, stored status unchanged); GREEN fresh
      lumen status enum + enum-level transition table + single-writer transition
      service (`BusinessException` rejections).

## 4. Number-issuer port (rewound 2026-11-18 — machinery moved to the platform)

- [x] 4.1 TDD: `NumberIssuerPortTests` — RED `registrationIssuesAccessionThroughThePort`
      (the stored accession equals the fake port's result verbatim) +
      `issuerFailureRefusesTheRegistration` (a failing port leaves NO order row); GREEN
      `org.birdbro.lumen.core.number.NumberIssuerPort` seam + registration service
      rewiring; canonical DDL drops `exam_number_rule` + seeds (four tables); kernel
      test host gains `FakeNumberIssuerPort` (deterministic sequence). The old issuer
      entity/mapper/service/tests are REMOVED from the kernel (they relocate to
      lumen-system with `number-rule-unification`).

## 5. Patient port + order creation

- [x] 5.1 TDD: `PatientRegistryPortTests` — RED `orderCreationConsumesThePort`
      (fake adapter identity drives `insertWithMembers`; creation without identity
      refuses) + `incompletePortResultRefused` (no `pat_uid` → refusal); GREEN
      `org.birdbro.lumen.core.patient.PatientRegistryPort` + `PatientIdentity` +
      order-creation service wiring.

## 6. HIS two-phase intake

- [x] 6.1 TDD: `HisTwoPhaseIntakeTests` — RED `cacheStoresMatchedApplyAndDropsUnmatchedItems`
      + `manualConfirmCreatesTheOrderAtRegistered` (port identity, cache marked
      committed, single tx) + `noAutoCommitFromTheCacheStage` +
      `cachedApplyCorrectedBeforeCommit` (committed row rejects); GREEN
      `exam_apply_cache` mapper + cache/confirm services (items reference
      exam-setting catalog by id + name snapshot).

## 7. Extension hooks

- [x] 7.1 TDD: `RegistrationHookTests` — RED `hooksFireInRegisteredOrder` +
      `hookFailureRollsBackTheRegistration` (no order row survives); GREEN
      `org.birdbro.lumen.core.hooks.RegistrationHook` (`order()` + `execute`)
      fired inside the registration tx by the creation service.

## 8. Close-out

- [x] 8.1 Full reactor green: `./mvnw compile -q` + `./mvnw test -q` from the
      aggregator (JDK 11) — counts + log paths recorded; existing suites untouched.
- [x] 8.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.
