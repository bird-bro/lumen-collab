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

- **Change name**: number-rule-unification-sev
- **Problem to solve**:
Number generation is ONE department-neutral mechanism duplicated across domains:
`patient_number_rule` + the local 行锁发号器 in lumen-patient (pat_id), and — until the
2026-11-18 rewind — the same shape again in lumen-core for the exam side (removed; the
kernel keeps only `NumberIssuerPort`). User ruling: unify ALL families into ONE
`number_rule` table in the SYSTEM schema behind ONE system issuance API — the
config-catalog home (dicts, exam-setting), one admin surface, one mechanism. The
burned-serial semantic survives remotely (the platform commits the increment; a caller
rollback burns the number — the 2026-11-18 ruling's core correction).

- **Problem summary**: Number-rule machinery is duplicated per domain (patient local 行锁 + former kernel copy); the 2026-11-18 ruling unifies ALL families into ONE system-schema table behind ONE system issuance API, with the first Feign contract and the patient rewire.
- **In scope**: system number_rule table + CRUD/issue API + row-lock issuer + internal-key guard; lumen-api module + NumberApi fail-fast contract; patient delegation + old table/routes/perms retired; mechanism tests relocated; menu seed move.
- **Out of scope**: the kernel (port landed); the console page (web change); service accounts/mTLS; cross-schema data migration (seed defaults instead); V38 (parallel session).
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/number-rule/spec.md`
13:### Requirement: Number Rule Directory
21:#### Scenario: Rule CRUD enforces system perms
28:#### Scenario: Family defaults are seeded
34:### Requirement: Platform Number Issuance
41:#### Scenario: Concurrent issuance does not duplicate
46:#### Scenario: Stamp rule resets by day
51:#### Scenario: Burned serial survives a caller rollback
56:### Requirement: Internal Service Issuance Contract
64:#### Scenario: Issue endpoint rejects non-internal callers
69:#### Scenario: Feign failure fails fast
- `specs/patient-domain/spec.md`
5:### Requirement: Image Number Issuance
12:#### Scenario: Concurrent issuance does not duplicate
17:#### Scenario: Stamp rule resets by day
22:#### Scenario: Rolled-back archive creation burns the number
30:### Requirement: Number Rule Management API


## Design Constraints

- **Architecture/Decisions**:
- **D1 — Domain placement**: `org.birdbro.lumen.manage` (the 系统管理域 — number rules
  are system-console administration, same home as dicts/users). Controller
  `NumberRuleController`, business services `NumberRuleService`/`NumberIssuerService`
  (interface + impl per the 2026-11-13 ruling), mapper `NumberRuleMapper`, entity
  `NumberRule` (the `PatientNumberRule` shape 1:1). Perms family `system:number:*`
  (list/create/update/delete).
- **D2 — Migration**: one system-ledger migration (next free V) creating
  `system.number_rule` + seeding the three family defaults (pat_id / accession /
  booking) + the menu seed move (号码规则 leaves 设置 L1 患者配置 group; a 系统管理 C
  node with `system:number:list` + the four button perms join the M=1 subtree — the
  web change consumes this seed). Comment lines carry no semicolons (the V36 house
  rule).
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 GREEN (structure): `lumen-api` module (plain jar, aggregator after `common`,
14:- [ ] 2.1 TDD: `NumberIssuerServiceTests` (lumen-system suite, fixture-family
20:- [ ] 2.2 TDD: `NumberRuleApiTests` — RED `ruleCrudEnforcesSystemPerms` (each write
24:- [ ] 2.3 Migration (next free system-ledger V — NOT V38): `number_rule` table +
31:- [ ] 3.1 TDD: patient `PatientNumberIssuanceTests` rewritten — RED
36:- [ ] 3.2 Retirement: patient-ledger migration DROPS `patient_number_rule`; the old
44:- [ ] 4.1 Full reactor green: `./mvnw compile -q` + `./mvnw test -q` from the
47:- [ ] 4.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md; coordination

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: issuer mechanism proofs (concurrency/day-reset/burned-rollback/no-rule) RED before the relocated implementation; CRUD perms gating + duplicate-active rejection RED before the controller; internal-key rejection RED before the interceptor; patient burned-semantic RED against the mocked contract before delegation.
- **Required edge cases**: concurrent issuance distinct serials; day rollover restarts at 1; REQUIRES_NEW commit survives outer rollback; wrong/missing internal key issues nothing; duplicate active rule rejected.
- **Regression-sensitive areas**: patient archive-creation flows keep issuing pat_id (via the contract); lumen-system suite grows without disturbing existing proofs; shared test DB conventions; migration takes the NEXT free V (never V38).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: one stack, four sequential task groups across three modules (lumen-api/system/patient) — sequential TDD batches in-context; the cross-stack coordination lives in the parent doc, SDD dispatch overhead not warranted.

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
