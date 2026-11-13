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

- **Change name**: merge-setting-into-system
- **Problem to solve**:
The standalone `lumen-setting` service (exam-setting-api, archived 2026-11-13) is a
pseudo-microservice: it shares the database, Redis, and the JWT secret with
lumen-system, exchanges no MQ events, has no Feign consumers, and its menu seeds already
live in system migrations — the split costs a second image, build, Flyway ledger, nginx
upstream, and DB role while buying no deployability for a pure-CRUD config domain. User
ruling 2026-11-13 dissolves it (rationale ledger: `openspec/coordination/exam-setting.md`
§ "2026-11-13 Merge Ruling"). One service = one image = one schema = one Flyway ledger;
future department services (radiology/us/endo) keep their planned independent services.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/data/spec.md`
5:### Requirement: Schema-per-Service Isolation
18:#### Scenario: Role isolation denies cross-schema access
24:#### Scenario: Schemas exist after initialization
29:#### Scenario: Shared system tables are reachable from every role
- `specs/exam-setting/spec.md`
5:### Requirement: Setting Schema Ownership
15:#### Scenario: Hosting service migrates the domain tables
22:#### Scenario: No dedicated role remains
29:### Requirement: Exam Body Group Columns
36:#### Scenario: Fresh deployment creates the body-group columns
42:### Requirement: Exam Body Columns
50:#### Scenario: Body belongs to a group and may nest
57:### Requirement: Exam Method Columns
63:#### Scenario: Fresh deployment creates the method columns
68:### Requirement: Exam Item Columns
78:#### Scenario: Item stores the catalog facts and the score weights
84:#### Scenario: Item code uniqueness
90:### Requirement: Exam Item Method Binding
96:#### Scenario: Item update rewrites method bindings atomically
103:### Requirement: Report Template Columns
112:#### Scenario: Public and private templates share one tree model
- `specs/service-topology/spec.md`
5:### Requirement: Foundation Module Set
16:#### Scenario: Foundation modules resolve
24:#### Scenario: Deferred module requested
32:### Requirement: Hosted Domain Packages
47:#### Scenario: Hosted domain discovered by the hosting service
55:#### Scenario: Hosted domain migrates through the one ledger
62:#### Scenario: Hosted domain keeps its external contract


## Design Constraints

- **Architecture/Decisions**:
### D1: Move the whole package tree, rename nothing
`org.birdbro.lumen.setting.*` (main + test) re-homes into `lumen-system`'s source roots
via git mv — package FQNs, class names, imports, and the
`dto/examsetting/` sub-package stay byte-identical, so git tracks renames and no import
rewrites exist. Alternative considered: flattening into `org.birdbro.lumen.system.*`
(deviceroom precedent) — rejected by user ruling: the separate package root IS the
future re-extraction boundary (naming.md "Hosted domain packages" precedent).
`SettingApplication` and `SettingUserDetailsService` are deleted, not moved: the first
is a second boot shell (two `UserDetailsService` beans would break `SecurityConfig`'s
constructor injection with `NoUniqueBeanDefinitionException` — observed in the first
merged-suite run), the second is the standalone-service verify stub the real
`UserDetailsServiceImpl` replaces.
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
12:- [x] 1.1 git mv `org.birdbro.lumen.setting.{controller,dto,entity,mapper,service,service.support}` +
18:- [x] 1.2 Aggregator `lumen-sev/pom.xml` module list drops `lumen-setting`; the retired
24:- [x] 2.1 `V1__setting_schema.sql` → `lumen-system/.../db/migration/V29__exam_setting_tables.sql`
28:- [x] 2.2 Test family schema retarget (D3): `SettingIntegrationTestBase` datasource
36:- [x] 3.1 `docs/deployment/pg-init.sql`: `setting` schema + `lumen_setting_role` block
39:- [x] 3.2 `.claude/rules/naming.md` "Hosted domain packages" precedent recorded;
46:- [ ] 4.1 Full suite green: `./mvnw test` from the aggregator exits 0 (log path +
49:- [ ] 4.2 Team test DB cleanup: confirm the retired `setting` schema holds fixture
53:- [ ] 4.3 Run `/opsx:verify merge-setting-into-system` (L1/L2/L3) and refresh the

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: 
- **Required edge cases**: 
- **Regression-sensitive areas**: 

## Execution Mode

- **Mode**: `Inline` | `Batch Inline` | `SDD`
- **Selection rationale**: <!-- AI: choose based on task count and complexity -->

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
