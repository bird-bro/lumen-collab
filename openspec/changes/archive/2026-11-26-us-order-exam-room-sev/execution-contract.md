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

- **Change name**: us-order-exam-room-sev
- **Problem to solve**:
The US registration order cannot record WHERE the exam happens: neither the kernel
order nor the US create path carries an exam-room field, so the desk cannot route a
patient to a room and the booking dialog cannot stamp the room onto a pre-registered
order. The room registry (`org_room`, org-device-room split model) already exists and
lists rooms; the order side is the missing half.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
5:### Requirement: Internal Exam Room Reads
17:#### Scenario: Internal list returns the department's enabled rooms
24:#### Scenario: Internal detail carries the validation fields
30:#### Scenario: Missing internal key is refused
- `specs/exam-workflow-kernel/spec.md`
5:### Requirement: Exam-Order Aggregate
24:#### Scenario: Aggregate persists as four linked members
31:#### Scenario: Snapshot refresh guarded by report state
38:#### Scenario: Channel reserves the remote value
44:#### Scenario: Booking time anchors the pre-registration slot
52:#### Scenario: Registration type records the entry category
62:#### Scenario: Exam room anchors the exam destination
- `specs/us-station/spec.md`
5:### Requirement: Registration Surface
37:#### Scenario: Create a booked order
55:#### Scenario: Invalid pre-registration is refused verbatim
64:#### Scenario: Create refuses a missing or invalid room
75:#### Scenario: Below-capacity pre-registration occupies the slot
82:#### Scenario: The create result carries the derived state
93:#### Scenario: Illegal transition is refused verbatim
99:#### Scenario: Cancel carries a reason
110:#### Scenario: Modality reaches the issuer port
118:### Requirement: Station Room Directory
130:#### Scenario: Station rooms list the department's enabled rooms
136:#### Scenario: Room service unavailability degrades to an honest empty list


## Design Constraints

- **Architecture/Decisions**: `design.md` D1–D7 — kernel V4 source + us-ledger V57
  vendor copy (stacks AFTER registration-order-status-dict, archived 2ac1a99 with the
  co-riding applied slice); NULL column + surface-enforced requirement (no backfill);
  the create gate runs FIRST (before the patient port resolve — nothing persists
  anywhere) and outside any transaction; validation read fails CLOSED (fallback
  throws) vs the directory's honest-empty degradation; one stamp point
  (`newOrder`, both branches); roomName joined at read time via ONE directory read
  per page (dangling id → null); provider internal family under the existing
  `InternalKeyInterceptor` + permit-all (console endpoints untouched); Feign client
  reuses `lumen.feign.system.url`; `@MockBean` seam in the suite base.
- **Technical constraints**: JDK 11 reactor builds from `lumen-sev/` (`-pl <m> -am`);
  never-modify public methods (additions only); OPS Lombok combo on the entity;
  Flyway-only DDL, comment lines carry NO semicolons; per-service schema ledger.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
- **Batch 1 (kernel column, TDD)**: tasks 1.1–1.3 — DONE (RED compile → GREEN 6/6
  aggregate; V4+V57; column-ledger 6/6).
- **Batch 2 (provider + Feign, TDD)**: tasks 2.1–2.5 — DONE (provider 3/3; fallback
  contract 3/3).
- **Batch 3 (directory endpoint, TDD)**: tasks 3.1–3.2 — DONE (4/4).
- **Batch 4 (create gate + view, TDD)**: tasks 4.1–4.3 — DONE (6/6; 25 legacy create
  bodies retrofitted).
- **Batch 5 (regression)**: task 5.1 — DONE (settled-tree reactor: core/system 325 +
  patient 58 green; us 95 = 94 green + 1 foreign failure [rail-quick-search annotation
  misplacement, fixed by user-authorized hotfix, class re-run 34/34 + module re-run
  95/95 green]).

## Test Obligations

- **Behaviors requiring failing-test-first**: provider key-gated reads; both-branch
  room stamping; create refusals (roomless/unknown/disabled/foreign-dept); directory
  listing + degraded band; view roomId/roomName with dangling null; fallback split
  semantics.
- **Required edge cases**: legacy row NULL room loads; validation-read failure refuses;
  absent station-dept-id → directory empty + creates refused; dangling roomId → null
  name; shared-bean mutation restores (review Important fix).
- **Regression-sensitive areas**: pre-registration capacity/occupancy flows; the five
  carried kernel aggregate scenarios; the console room registry (JWT gates untouched).

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
