# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: us-rail-card-v2
- **Problem to solve**:
The compact rail card was rebuilt through a one-day ruling chain (2026-11-26) that
landed AFTER the `us-order-exam-room-web` archive swept the earlier card skeleton into
`web-us-station`. The main spec's Order List scenarios still carry two retired facts
(the 现场/预约 category chip, the 预登记时间 reading) and none of the post-archive
anatomy rulings (the state stripe, the single 检查时间 anchor semantics, the
click-to-expand items row, the emergency red frame, the cancel ×-circle glyph, the
icon-color discipline). The approved prototype `docs/prototypes/us-rail-card-v2.html`
(variant A, user-picked and visually confirmed against the real backend) is the UI

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
3:### Requirement: Cancel Verb Glyph
13:#### Scenario: 单据取消 actions ride the cancel glyph
20:#### Scenario: 删除 verbs keep the trash glyph
- `specs/web-us-station/spec.md`
3:### Requirement: Order List
11:#### Scenario: List renders with state and identity
30:#### Scenario: The card anatomy carries the v2 layout
48:#### Scenario: The card keeps one icon and one bold discipline
57:#### Scenario: Status chips carry the meter and multi-select
67:#### Scenario: The rail quick-search narrows the list and the meter
76:#### Scenario: Wide conditions compose through the query row
85:#### Scenario: 我的查询 persists and auto-applies the default
96:### Requirement: Card Rail Anchor Semantics
105:#### Scenario: The anchor never shows the audit clock


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
10:- [x] 1.1 Walk the shipped compact card (`src/pages/us/registration/index.tsx`) against
13:- [x] 1.2 Confirm the ConsoleAction `cancel` key and the ×-circle call site pair in
18:- [x] 2.1 Run the registration page suite (`pnpm vitest run
22:- [x] 2.2 Typecheck (`pnpm exec tsc --noEmit`) exits 0
23:- [ ] 2.3 L2 check: every delta scenario maps to at least one shipped test or an

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
