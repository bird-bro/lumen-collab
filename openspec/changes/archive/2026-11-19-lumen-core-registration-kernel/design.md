# Design

## Context

The aggregator (`lumen-sev/pom.xml`) currently declares 5 modules (`lumen-bom`,
`framework`, `common`, `lumen-system`, `lumen-patient`). `lumen-core` does not exist
yet; `lumen-api` (inter-service Feign contracts) has not materialized either, so any
patient-service interaction inside the kernel must be a port interface, not a Feign
call. The patient domain (archived 2026-11-18) set the precedents this kernel reuses:
行锁 + 日重置 stamp numbering (`lumen-patient` rule table), jsonb via `Types.OTHER`
handler, append-only outbox, `@Data @Builder @AllArgsConstructor @NoArgsConstructor`
entity combo. The EIS reference is digested in `lumen-reference/NOTES-us-workstation.md`
§1.2–1.4 (god table, three-number rule mechanism, state machine, HIS two-phase intake)
and §5.1–5.2 (kernel domain list, department seams). Naming ruling 2026-11-17 fixes the
module/package as `lumen-core` / `org.birdbro.lumen.core`; kernel tables are created by
consuming services' Flyway ledgers (hosted-domain precedent), not a kernel schema.

## Goals / Non-Goals

**Goals:**
- `lumen-core` module skeleton that builds in the reactor and joins the Shared Library
  Boundary (non-executable, controller-free).
- The registration-side behavior set: aggregate, state machine, exam-side numbering,
  patient port, HIS two-phase intake, extension hooks — each with TDD coverage.
- Kernel DDL as canonical copy-from source + a test-schema verification path.
- Consumer-facing seams documented well enough that the first department change
  (radiology or us) can adopt without re-design.

**Non-Goals:**
- Any HTTP surface, controller, or department consumption (pure library).
- 叫号域, report-domain mechanics (审核链/锁/合并/签名 — later proposal per user ruling
  2026-11-18), 打印, integration protocols (HL7v2/MWL/MPPS belong to lumen-integration).
- Production schema creation anywhere (no consumer exists yet).

## Decisions

- **D1 — Module shape**: `lumen-core` is a plain jar wired into the aggregator
  (`<module>lumen-core</module>` after `common`, before the services). Dependencies
  ride `lumen-bom` (framework, common, mybatis-plus, hutool) — no new versions, no
  spring-boot-maven-plugin repackage, no `@SpringBootApplication` in main sources.
- **D2 — DDL ownership: canonical source + vendored adoption**. Flyway per-ledger
  version uniqueness forbids a shared `classpath:db/kernel` location with fixed
  version numbers (a consumer ledger already holds V1–V37). The kernel therefore ships
  its DDL as the canonical copy-from source at
  `lumen-core/src/main/resources/db/kernel/V1__exam_kernel_registration.sql` (four
  tables after the 2026-11-18 rewind — exam_order / exam_execution / exam_report /
  exam_apply_cache; the number-rule table left the kernel for the platform's system
  schema, see D5); each consuming service VENDOR-COPIES the statements into its own
  ledger at adoption (merge-setting-into-system precedent) and records the copy. This
  change applies the canonical file to a test-only `core` schema on the shared test
  DB. Alternatives rejected: shared Flyway location (version collision), kernel-owned
  schema (violates the tables-follow-consumer-schema ruling).
- **D3 — Aggregate persistence**: entities `ExamOrder` / `ExamExecution` /
  `ExamReport` + `PatientSnapshot` value object (jsonb column on `exam_order`,
  `Types.OTHER` handler per patient precedent); the OPS Lombok combo on every entity;
  `BeanUtil.copyProperties(src, dest, true)` for 1:1 request→entity copies; `Integer`
  surrogate ids; every table carries the `EntityBase` audit column set. The aggregate
  saves through one kernel service (`insertWithMembers`) in a single transaction so
  the four rows live or die together.
- **D4 — State machine**: a fresh lumen enum (EIS numeric codes 104201/101301… are
  EIS dict semantics and are NOT copied); transitions live in one enum-level transition
  table; the transition service is the only writer of the status column. Rejections
  throw the framework's `BusinessException` with `A0400` + descriptive msg for now;
  dedicated machine-actionable codes are deferred to the first consumer change (no UI
  exists to key off them yet).
- **D5 — Number issuance behind a port** (rewound 2026-11-18, user ruling: number rules
  unify into the system schema via `number-rule-unification`): the kernel defines
  `org.birdbro.lumen.core.number.NumberIssuerPort` (`issue(ruleType) → number`) and its
  registration entry consumes it for the accession family; the row-lock rule table and
  issuer implementation are PLATFORM-owned (system schema + system API, the next
  change) — the kernel ships no rule table, no issuer, no seeds. Fail-fast contract:
  a port failure refuses the registration; no fallback ever fabricates numbers
  (burned-serial semantics preserved remotely: the platform commits the serial
  increment, a caller rollback burns the number). The test host stands in a
  deterministic fake port (the FakePatientRegistryPort pattern).
- **D6 — Patient port**: `org.birdbro.lumen.core.patient.PatientRegistryPort`
  (`resolveOrRegister(...) → PatientIdentity(patUid, patId, resolved, demographics…)`)
  defined in the kernel; consuming services provide the adapter (HTTP/Feign to
  lumen-patient belongs to them). Kernel tests inject a fake port. The snapshot
  refresh service takes the delivered payload and refuses when the aggregate's report
  is 已出 (spec guard).
- **D7 — HIS intake model**: `exam_apply_cache` (source jsonb = raw payload, target
  jsonb = dictionary-mapped items referencing the exam-setting catalog by id + name
  snapshot, status, his_id/apply_no, patient fields filled at confirm). Cache-time
  matching drops items without a catalog reference (EIS 1.4 semantics). Confirm
  resolves identity through the port, creates the order at 已登记, marks the cache row
  committed — all inside one transaction. Corrections rewrite `target` only while
  status is cached.
- **D8 — Extension hooks**: `org.birdbro.lumen.core.hooks.RegistrationHook`
  (`order()` + `execute(context)`), injected as the ordered Spring list; the
  registration transaction fires them in `order()` sequence after the aggregate
  persists; any exception propagates and rolls back (EIS five-step linkage semantics).
  Outbound integrations (HIS push, PACS) should ride the consumer's own async/outbox
  path — the hook contract documents this but does not enforce it.
- **D9 — Test host**: a test-sources-only `TestKernelApplication`
  (`@SpringBootApplication` + `@MapperScan`) boots against the shared test DB with
  `spring.flyway.schemas=core`, locations `classpath:db/kernel`, mirroring the
  lumen-system integration-test conventions (`.env-test.local` credentials,
  Hikari keepalive settings). The `core` schema is kernel-tests-only and is never a
  production schema.

## Risks / Trade-offs

- **Vendored DDL drift**: each consumer copies the kernel DDL into its ledger, so
  copies can drift from the canonical file. Mitigation: the canonical file carries a
  header naming the adoption rule, and the first consumer change's tasks include a
  copy-and-record step.
- **Shared test DB coupling**: kernel integration tests inherit the shared-server
  hazards (parallel-suite collisions, transient disconnects — known-issues memory);
  acceptable, same as every other service suite.
- **In-tx hook failure aborts registration**: matches EIS, but a slow remote hook
  would hold the transaction open. Consumers pushing to external systems must not do
  it synchronously in a hook (documented in the hook contract).
- **Semantics without a surface**: state-machine and intake rejections carry no HTTP
  contract until the first consumer lands; verify L2 checks code-level behavior, and
  error-code hardening defers to that change.
