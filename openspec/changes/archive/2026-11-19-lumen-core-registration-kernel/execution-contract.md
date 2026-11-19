# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: lumen-core-registration-kernel
- **Problem to solve**:
The department workstations share one exam workflow, but no shared kernel exists yet:
building lumen-us directly would fork EIS's 78-field `order_info` god table per
department — the exact pattern the EIS deep-dive disproved (三科登记页三份复制、
`order_patient` 三处写、状态机调用点散落五个服务类). patient-domain (archived
2026-11-18) delivered the patient half (`pat_uid`/`pat_id`/发号); the exam-side half is
the next step on the dependency route patient → lumen-core → lumen-us
(`lumen-reference/NOTES-us-workstation.md` §5.3), and it is the foundation both the US
workstation and radiology's booking flow build on. User rulings 2026-11-18: 登记侧先行

<!-- AI: summarize the above into 1-2 sentences -->
- **Problem summary**: The exam workflow has no shared kernel — each department would
  fork EIS's god-table registration. This change creates the `lumen-core` shared
  library (registration side only: order aggregate, state machine, port seams, HIS
  two-phase intake, extension hooks) as the foundation for radiology and the US
  workstation on the patient → lumen-core → lumen-us dependency route (user rulings
  2026-11-18: 登记侧先行 / 可配置审核级留报告域提案 / 纯内核库 / number rules unify into
  the platform system schema — kernel carries the port only).
- **In scope**: `lumen-core` module skeleton + canonical DDL (four tables); four-member
  exam-order aggregate; document state machine; number-issuer port + patient-registry
  port; HIS apply-cache two-phase intake; ordered registration hooks with rollback;
  test-host verification against a test-only `core` schema.
- **Out of scope**: HTTP surface/controllers; department consumption (radiology/us);
  叫号域; report-domain mechanics (审核链/锁/合并/签名 — user ruling defers to the
  report-domain proposal, configurable 0/1/2 级); 打印; HL7v2/MWL/MPPS protocol layers
  (lumen-integration); the platform number-rule table + issuance API
  (`number-rule-unification`, next change); production schema creation;
  `system-data`/`errors` spec changes.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-workflow-kernel/spec.md`
13:### Requirement: Exam-Order Aggregate
22:#### Scenario: Aggregate persists as four linked members
29:#### Scenario: Snapshot refresh guarded by report state
36:#### Scenario: Channel reserves the remote value
42:### Requirement: Document State Machine
52:#### Scenario: Booking enters at 已预约
57:#### Scenario: Walk-in and HIS confirm enter at 已登记
63:#### Scenario: Happy path advances through each stage
68:#### Scenario: Cancellation branches split by stage
77:#### Scenario: Illegal transition rejected
84:### Requirement: Number Issuer Port
93:#### Scenario: Registration issues the accession through the port
98:#### Scenario: Issuer failure refuses the registration
103:### Requirement: Patient Registry Port
110:#### Scenario: Order creation consumes the port
116:#### Scenario: Incomplete port result refused
121:### Requirement: HIS Two-Phase Intake
128:#### Scenario: Cache stores matched apply and drops unmatched items
134:#### Scenario: Manual confirm creates the order at 已登记
139:#### Scenario: No auto-commit from the cache stage
144:#### Scenario: Cached apply corrected before commit
151:### Requirement: Registration Extension Hooks
158:#### Scenario: Hooks fire in registered order
163:#### Scenario: Hook failure rolls back the registration
- `specs/service-topology/spec.md`
5:### Requirement: Shared Library Boundary
17:#### Scenario: Library is not runnable
23:#### Scenario: HTTP surface ownership
31:#### Scenario: Service-specific rule proposed for a shared library


## Design Constraints

- **Architecture/Decisions**:
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
- **Technical constraints**:
- Dependency iron rule (patient decision #10): lumen-core never depends on remote;
  remote embeds lumen-core without rewriting it (#6). Kernel never imports
  department modules; departments depend on the kernel.
- Order↔patient linkage: `patient_uid` NOT NULL (占位患者行 exists from birth of the
  order); snapshot jsonb auto-refresh on `patient.changed` only while 报告未出
  (decision #4) — the refresh listener is consumer-side; the kernel exposes the
  snapshot-refresh service.
- v1 reserves the 申请渠道 `channel` field with a 远程申请 enum value present but not
  yet enabled (patient decision #30 note, remote readiness).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [x] 1.1 RED (structure): `KernelModuleArchitectureTests` — asserts the packaged jar
10:- [x] 1.2 GREEN: `lumen-core` module — pom (parent aggregator, deps via `lumen-bom`:
15:- [x] 1.3 Canonical DDL: `lumen-core/src/main/resources/db/kernel/
27:- [x] 2.1 Test host: test-sources `TestKernelApplication` (`@SpringBootApplication`
32:- [x] 2.2 TDD aggregate: `ExamOrderAggregateTests` — RED `aggregatePersistsAsFourLinkedMembers`
37:- [x] 2.3 TDD snapshot guard: `SnapshotRefreshGuardTests` — RED
44:- [x] 3.1 TDD: `ExamOrderStateMachineTests` — RED `bookingEntersAtBooked` /
53:- [ ] 4.1 TDD: `NumberIssuerPortTests` — RED `registrationIssuesAccessionThroughThePort`
64:- [x] 5.1 TDD: `PatientRegistryPortTests` — RED `orderCreationConsumesThePort`
72:- [x] 6.1 TDD: `HisTwoPhaseIntakeTests` — RED `cacheStoresMatchedApplyAndDropsUnmatchedItems`
81:- [x] 7.1 TDD: `RegistrationHookTests` — RED `hooksFireInRegisteredOrder` +
88:- [ ] 8.1 Full reactor green: `./mvnw compile -q` + `./mvnw test -q` from the
90:- [ ] 8.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md.

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: every GREEN task pairs a RED test named
  after its scenario — aggregate four-member persist + snapshot report-state guard +
  channel reserve (2.2/2.3, done); all state-machine paths incl. cancel-with-已出-report
  refusal and illegal-transition rejection (3.1, done); port-driven accession issuance
  + issuer-failure refusal (4.1, pending — the rewound port task); port-driven order
  creation + incomplete-identity refusal (5.1, done); HIS cache/confirm/no-auto-commit/
  correction-guard (6.1, done); hook order + hook-failure rollback (7.1, done); module
  structure assertions ran before the module existed (1.1, done).
- **Required edge cases**: hook exception leaves zero order rows; committed HIS cache
  rejects correction; snapshot refresh byte-refused after 已出; issuer failure refuses
  registration (no fabricated-number fallback ever).
- **Regression-sensitive areas**: aggregator build stays green with the new module
  (reactor compile + full `./mvnw test`); existing lumen-system/lumen-patient suites
  untouched; shared test DB conventions (keepalive, exclusive window) per known
  hazards.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 13 tasks across 8 groups confined to ONE new module
  (`lumen-core`) + the aggregator pom — no cross-module dependencies, no second stack,
  no HTTP surface; sequential TDD batches suit inline execution. SDD coordination
  overhead is not warranted for a single-module library change. The 2026-11-18 rewind
  (number rules → platform) kept the mode: task 4.1 re-scoped in place.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
