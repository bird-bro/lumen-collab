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

- **Change name**: us-nav-calendar-day-counts
- **Problem to solve**:
The date-nav popover's mini month calendar (the 2026-11-26 in-session addition) shows
dates but no workload signal — the desk flips days blind. A per-day registration count
turns the calendar into a workload preview (which days carry orders), mirroring the
booking dialog's day-level badge pattern.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
5:### Requirement: Registration Page Shell
25:#### Scenario: Page renders under the activated US workstation
31:#### Scenario: Actions hide without perms
36:#### Scenario: The edge toggle switches the two states
42:#### Scenario: The datenav popover calendar carries per-day counts


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [x] 1.1 Page test FIRST: opening the date popover shows the count badge on a day
8:- [x] 1.2 Implement the loader (Promise.allSettled over the displayed month's days,
14:- [x] 2.1 `pnpm test` exit 0 (the full page suite stays green)

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
