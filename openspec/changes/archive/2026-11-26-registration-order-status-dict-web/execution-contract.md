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

- **Change name**: registration-order-status-dict-web
- **Problem to solve**:
The US registration desk's status chips carry hard-coded `STATUS_LABELS` over the
retired six-state enum (`BOOKED`…`BOOKING_CANCELLED`), which no longer matches the
kernel's six-state machine (user ruling 2026-11-26: 已登记/检查中/诊断中/已完成/已打印/
已取消), and the labels are not operator-maintainable — every other desk vocabulary
moved into the dict system (患者来源 1023, 妊娠状态 1002).

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
5:### Requirement: Order List
34:#### Scenario: List renders with state and identity
41:#### Scenario: Status chips carry the meter and multi-select
53:#### Scenario: Wide conditions compose through the query row
60:#### Scenario: 我的查询 persists and auto-applies the default
67:### Requirement: Registration Workbench Dict-Resolved Vocabularies
83:#### Scenario: Form selects render dict-driven options
91:#### Scenario: Pregnancy cell conditions on the dict female code
97:#### Scenario: Status chips resolve labels from catalog 1031
106:#### Scenario: Dict failure degrades without blocking


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
9:- [x] 1.1 Narrow `UsOrderStatus` in `src/api/us-orders.ts` (the self-contained module the type actually lives in - the task text guessed types.ts from the proposal Impact) to the six enum names and
12:- [x] 1.2 Add `US_ORDER_STATUS_DICT_ID = 1031` and the `US_ORDER_STATUS_BRIDGE` map
21:- [x] 2.1 Update `us-registration-page.test.tsx` FIRST: seed the dict mock with the
27:- [x] 2.2 Rework the page: `RAIL_HEAD_STATUSES` → six states in dict order, chips +
37:- [x] 3.1 Run the full frontend suite + build once green — verify: `pnpm vitest run`

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
