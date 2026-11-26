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

- **Change name**: us-rail-quick-search
- **Problem to solve**:
The rail quick-search landed as a user-directed follow-up slice (commits `9ca86f4` /
`e737153`, user ruling 2026-11-26) AFTER `registration-order-status-dict` archived —
the `keyword` query condition and the rail-search box are implemented and tested but
the synced specs do not carry them yet. This change RECORDS the contract: it exists to
sync the delta, not to drive new implementation.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/us-station/spec.md`
5:### Requirement: Workbench Query Surface
22:#### Scenario: Status multi-select composes with the wide conditions
27:#### Scenario: Snapshot conditions hit the jsonb document
33:#### Scenario: Exam-item name matches fuzzily across the jsonb array
39:#### Scenario: Apply-time range bounds the registration moment
44:#### Scenario: Status counts respect the filters
56:#### Scenario: The rail quick-search OR-matches the identity handles
- `specs/web-us-station/spec.md`
5:### Requirement: Order List
42:#### Scenario: List renders with state and identity
49:#### Scenario: Status chips carry the meter and multi-select
62:#### Scenario: The rail quick-search narrows the list and the meter
69:#### Scenario: Wide conditions compose through the query row
76:#### Scenario: 我的查询 persists and auto-applies the default


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
8:- [x] 1.1 Backend: the keyword API test passes in isolation (name/accession hits +
13:- [x] 1.2 Frontend: the page suite passes with the quick-search + chip assertions —

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
