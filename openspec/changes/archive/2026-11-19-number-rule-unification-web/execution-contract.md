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

- **Change name**: number-rule-unification-web
- **Problem to solve**:
The 号码规则 console page (patient-domain change built it under 设置 L1's 患者配置
group, `patient:rule:*` perms, patient-service data source) administrates a table that
now lives in the SYSTEM schema behind the system service (`number-rule-unification-sev`).
The page migrates to the 系统管理 IA with `system:number:*` perms and the system
data source — same table, same drawer patterns; the patient-side page entry retires
with the patient routes.

- **Problem summary**: The 号码规则 console page administrates a table that moved to the system schema — the page migrates to the 系统管理 IA with system:number:* perms and the system data source (existing-page edit, scenario set preserved).
- **In scope**: API client repoint (new number-rules.ts, patient.ts rule section retired); page move per the migrated seed; perms wholesale switch; multi-family 规则类型 select; test repoint.
- **Out of scope**: backend (sev change); menu seed edits (backend owns menu_info); visual redesign; new pages.
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
5:### Requirement: Number Rule Page Shell
13:#### Scenario: Page loads against the real backend
19:#### Scenario: Toolbar follows button perms
24:### Requirement: Number Rule Form and Delete
33:#### Scenario: Create a stamped rule
40:#### Scenario: Invalid serial length surfaces on the field
45:#### Scenario: Delete requires confirmation
51:#### Scenario: Rule type selects the platform family


## Design Constraints

- **Architecture/Decisions**:
- **D1 — API client**: a dedicated `src/api/number-rules.ts` typed against the system
  endpoints (the spec's Direct-service surface); the rule section inside
  `src/api/patient.ts` is deleted (no dead code); page imports repoint.
- **D2 — Page location**: the component moves to the 系统管理 page directory
  (route registered against the migrated seed's path — exact path read from the seed
  migration at apply time, the two changes coordinate via the registry); the old
  location's files are removed.
- **D3 — Perms**: one wholesale switch `patient:rule:*` → `system:number:*`
  (list/create/update/delete) in the page gate + toolbar + drawer buttons.
- **D4 — 规则类型**: a select over the platform families with Chinese labels
  (影像号/检查号/预约号); the drawer keeps every other field and validation as-is.
- **D5 — Tests**: the page tests repoint their mocked API module + perms strings; the
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 TDD red: page tests repoint — mocked module becomes
9:- [ ] 1.2 GREEN: `src/api/number-rules.ts` (typed against the system endpoints); page
16:- [ ] 2.1 `pnpm build` exit 0 + `pnpm test` counts; user visual confirm on the real
18:- [ ] 2.2 `/opsx:verify` → verify.md; coordination registry updated.

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: the repointed page tests + the multi-family select scenario run RED against the current page before the migration edits.
- **Required edge cases**: drawer validation surfaces on the offending field; delete confirmation; missing system:number:create hides the create affordance.
- **Regression-sensitive areas**: full pnpm suite stays green; no dead patient-side rule client code remains.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: four tasks confined to lumen-web (client + one page + tests) — sequential inline batches.

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
