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

- **Change name**: registration-order-status-dict-sev
- **Problem to solve**:
The kernel document state machine's vocabulary no longer matches the desk's reality
(user ruling 2026-11-26): 处理中 (`IN_PROGRESS`) cannot tell 检查中 (exam being
performed) from 诊断中 (report being written), 已打印 has no state at all, and
`BOOKED`/`BOOKING_CANCELLED` are dead vocabulary since the 预约=预登记 ruling
(2026-11-24) — no flow produces them, and the cancel service still carries their
branch as dead code. The status labels are hard-coded frontend constants, so the
vocabulary cannot be operator-maintained like every other desk vocabulary
(患者来源 1023, 妊娠状态 1002).

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
5:### Requirement: Dict Items Lookup By Catalog Name
23:#### Scenario: Items returned for a known catalog
29:#### Scenario: Unknown catalog resolves to an empty list
34:#### Scenario: Disabled details are excluded
39:#### Scenario: Lookup requires authentication but no permission string
45:#### Scenario: Tagged items carry the optional display metadata
- `specs/exam-workflow-kernel/spec.md`
5:### Requirement: Document State Machine
19:#### Scenario: Booking enters at 已预约
30:#### Scenario: Walk-in and HIS confirm enter at 已登记
36:#### Scenario: Happy path advances through each stage
44:#### Scenario: Cancellation branches split by stage
57:#### Scenario: Illegal transition rejected
64:### Requirement: Pre-Registration Support
74:#### Scenario: Entry status derives from booking time in one transaction
86:#### Scenario: Pre-registration refused when the slot is at capacity
94:#### Scenario: Pre-registration below capacity occupies the slot
101:#### Scenario: Slot counting uses live pre-registered orders only
111:#### Scenario: Pre-registration refused outside open windows or in the past
119:#### Scenario: Unconfigured modality refuses pre-registration
127:#### Scenario: Vendored DDL keeps the department-owned config storage
- `specs/us-station/spec.md`
5:### Requirement: Registration Surface
31:#### Scenario: Create a booked order
47:#### Scenario: Invalid pre-registration is refused verbatim
56:#### Scenario: Below-capacity pre-registration occupies the slot
63:#### Scenario: The create result carries the derived state
74:#### Scenario: Illegal transition is refused verbatim
80:#### Scenario: Cancel carries a reason
91:#### Scenario: Modality reaches the issuer port
97:### Requirement: Workbench Query Surface
111:#### Scenario: Status multi-select composes with the wide conditions
116:#### Scenario: Snapshot conditions hit the jsonb document
122:#### Scenario: Exam-item name matches fuzzily across the jsonb array
128:#### Scenario: Apply-time range bounds the registration moment
133:#### Scenario: Status counts respect the filters


## Design Constraints

- **Architecture/Decisions**:
<!-- AI: no design.md found - create it first -->
- **Technical constraints**:
<!-- AI: fill from proposal Constraints -->

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [x] 1.1 Rewrite the state-machine tests to the six-state contract FIRST
12:- [x] 1.2 Collapse `ExamOrderStatus` to the six states (javadoc carries the trigger
16:- [x] 1.3 Fix the fallout consumers: the cancel fork in `RegistrationOrderServiceImpl`
24:- [x] 2.1 Remove the 登记 transition endpoint (controller route + its service method +
27:- [x] 2.2 Add `V55__registration_status_dict.sql` to the lumen-system ledger (design
32:- [x] 2.3 Add `V56__exam_order_status_six_states.sql` to the lumen-us ledger (design
39:- [x] 2.4 Extend the dict lookup test FIRST: the 1031 items serialize the third
43:- [x] 2.5 Add `tag` to `DictItemDto` (`@JsonInclude(NON_NULL)`, javadoc amendment
52:- [x] 3.1 Run the full backend suite once green — verify: `./mvnw test -q` exit 0

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
