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

- **Change name**: us-prereg-as-registered-sev
- **Problem to solve**:
The archived us-registration-redesign made 预约 a separate lifecycle stage: a
pre-registered order enters `BOOKED` and a nurse must run 登记 (arrival confirmation)
before the exam. User ruling 2026-11-24: **预约 IS 预登记** — the registration already
happened when the booking was made, the patient arrives and goes straight into the exam.
The manual confirmation step contradicts the business, and once both entry kinds merge
into one state, `booking_time` NULL/NOT NULL is the only discriminator left — a fragile
inference for the coming queue system (排队叫号), not a business fact.

<!-- AI: summarized — collapse the two-stage 预约/登记 model into 登记 for every create
path, move the entry category onto a server-stamped kernel column, and re-anchor slot
occupancy to booking_time + not-cancelled; state machine untouched -->
- **In scope**: kernel `ExamRegistrationType` enum + `ExamOrder.registrationType` field;
kernel `db/kernel/V3` DDL source + us `V6` vendor copy; us create-path stamps (both
branches REGISTERED + category, server-owned); occupancy predicate NOT IN the two cancel
states at both count sites; cancel branch left code-untouched (reserved);
`RegistrationOrderView`/`RegistrationCreateResult` gain `registrationType` (+view
`createBy`); spec deltas: exam-workflow-kernel (3 requirements) + us-station
(Registration Surface).

- **Out of scope**: operator NAME resolution (uid only); the queue system (取号/签到
arrival signals); removing kernel `BOOKED` state or any edge;
`PreRegistrationPolicy.requireFutureBooking` changes; the ext hook seam (us-apply-info
owns those files right now).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-workflow-kernel/spec.md`
3:### Requirement: Exam-Order Aggregate
18:#### Scenario: Aggregate persists as four linked members
25:#### Scenario: Snapshot refresh guarded by report state
32:#### Scenario: Channel reserves the remote value
38:#### Scenario: Booking time anchors the pre-registration slot
46:#### Scenario: Registration type records the entry category
56:### Requirement: Document State Machine
66:#### Scenario: Booking enters at 已预约
75:#### Scenario: Walk-in and HIS confirm enter at 已登记
81:#### Scenario: Happy path advances through each stage
86:#### Scenario: Cancellation branches split by stage
95:#### Scenario: Illegal transition rejected
102:### Requirement: Pre-Registration Support
112:#### Scenario: Entry status derives from booking time in one transaction
124:#### Scenario: Pre-registration refused when the slot is at capacity
132:#### Scenario: Pre-registration below capacity occupies the slot
139:#### Scenario: Slot counting uses live pre-registered orders only
149:#### Scenario: Pre-registration refused outside open windows or in the past
157:#### Scenario: Unconfigured modality refuses pre-registration
165:#### Scenario: Vendored DDL keeps the department-owned config storage
- `specs/us-station/spec.md`
3:### Requirement: Registration Surface
29:#### Scenario: Create a booked order
45:#### Scenario: Invalid pre-registration is refused verbatim
54:#### Scenario: Below-capacity pre-registration occupies the slot
61:#### Scenario: The create result carries the derived state
72:#### Scenario: Illegal transition is refused verbatim
78:#### Scenario: Cancel carries a reason
85:#### Scenario: Modality reaches the issuer port


## Design Constraints

- **Architecture/Decisions**:
- **D1 — Column home is kernel `exam_order`, not the ext table.** The category is
  department-neutral (radiology needs the same), so the kernel aggregate rule "no
  department-specific field" also FORBIDS the ext: the ext exists for US clinical fields
  (孕检) and is written only through the hook seam at create. `registration_type` rides
  the same create transaction but on the document. Alternative rejected: us-side ext
  column (breaks the kernel aggregate rule; forks the category per department).
- **D2 — Enum, the `channel` precedent.** `ExamRegistrationType` (`WALK_IN`,
  `PRE_REGISTERED`) with the house enum-name handler; column varchar(16) NOT NULL
  DEFAULT 'WALK_IN'. Entity gains the field additively; the create DTO gains NOTHING
  (the stamp is server-owned — `BeanUtil.copyProperties` can never pick it up because
  the request has no such property).
- **D3 — Us create path sets `REGISTERED` directly; `deriveEntryStatus` is retired from
- **Technical constraints**: JDK 11 reactor builds from `lumen-sev/` (JAVA_HOME export;
mvnw pins Maven 3.9.9); never-modify — additive entity/view fields only,
`deriveEntryStatus` is RETIRED from the us flow, not modified; Flyway never rewrites an
applied migration — new kernel V3 / us V6 files only; DDL comment lines carry no
semicolons; enum storage rides the house enum-name handler (`ExamChannel` precedent);
the create DTO gains NO registrationType property (server-owned stamp cannot leak
through `BeanUtil.copyProperties`); MP logic-delete keeps handling non-deleted exactly
as today.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
7:- [ ] 1.1 Add `ExamRegistrationType` enum (`WALK_IN`, `PRE_REGISTERED`) + the `registrationType` field to kernel `ExamOrder` (the `channel`/`ExamChannel` precedent); verify `./mvnw -pl lumen-core -am compile -q` exits 0
8:- [ ] 1.2 Add kernel `db/kernel/V3__exam_order_registration_type.sql` (ADD COLUMN IF NOT EXISTS `registration_type` varchar(16) NOT NULL DEFAULT 'WALK_IN', no-semicolon comments, V4 house style) and verify the kernel test ledger applies it: run the kernel flow tests, exit 0
9:- [ ] 1.3 Kernel state-machine test guard: a test pinning that the EDGES map and `transition()` are byte-unchanged by this change (existing tests suffice — run them, exit 0)
13:- [ ] 2.1 RED: extend `UsRegistrationApiTests` — a walk-in create persists `registrationType=WALK_IN` + status REGISTERED; a future-slot create persists `registrationType=PRE_REGISTERED` + status REGISTERED (not BOOKED); the create response echoes `registrationType`; run, watch them fail
14:- [ ] 2.2 Vendor the kernel V3 as us `V6__exam_order_registration_type.sql` (same statements, us-ledger header) and verify the us migration tests apply it, exit 0
15:- [ ] 2.3 GREEN: stamp the two branches in `RegistrationOrderServiceImpl` (walk-in → WALK_IN, future-slot → PRE_REGISTERED, both `setStatus(REGISTERED)` — `PreRegistrationPolicy.deriveEntryStatus` drops out of the flow, `requireFutureBooking` stays); re-run 2.1, green
16:- [ ] 2.4 Guard: a test posting `registrationType` in the create body asserts the stamp is ignored (server-owned), green
20:- [ ] 3.1 RED: extend the slot tests — a REGISTERED pre-registered order (and a legacy BOOKED one) occupies its window; a CANCELLED one frees it; the `slotOptions` picker booked counts mirror the same predicate; run, watch them fail
21:- [ ] 3.2 GREEN: swap both count predicates (`registerPreRegistration` occupancy + `slotOptions`) from `.eq(status, BOOKED)` to `.notIn(status, BOOKING_CANCELLED, CANCELLED)`; re-run 3.1 + the capacity-refusal tests, green
25:- [ ] 4.1 RED: a test cancelling a pre-registered order (REGISTERED) lands `CANCELLED`; run, watch it fail if the current fixture creates BOOKED, then update the fixture to the new entry semantics and assert the green path
26:- [ ] 4.2 RED→GREEN: `RegistrationOrderView` gains `registrationType` + `createBy`; `RegistrationCreateResult` gains `registrationType`; `toView` maps them; a page-query test asserts the fields on rows, green
27:- [ ] 4.3 Run the FULL us suite (`./mvnw -pl lumen-us -am test -q`), exit 0 — no regression in the ext/apply-info or slot-picker tests building on the same files
31:- [ ] 5.1 `openspec validate us-prereg-as-registered-sev` passes (deltas parse, superset rule holds)

## Test Obligations

<!-- AI: identified from the scenarios + task plan -->
- **Behaviors requiring failing-test-first**: category stamp + REGISTERED entry per
branch (walk-in WALK_IN / future PRE_REGISTERED, response echo) — task 2.1; occupancy
predicate swap (REGISTERED pre-reg + legacy BOOKED occupy, CANCELLED frees, picker
counts mirror) — task 3.1; cancel of a pre-registered order lands CANCELLED — task 4.1;
view rows carry registrationType + createBy — task 4.2.
- **Required edge cases**: client-posted `registrationType` is ignored (server-owned,
task 2.4); capacity-refusal / outside-window / past / unconfigured refusals stay
verbatim; walk-ins (NULL booking_time) never occupy; COMPLETED orders keep occupying
their window.
- **Regression-sensitive areas**: kernel state-machine edges byte-unchanged (task 1.3);
the full us suite over files shared with the in-flight us-apply-info-sev (task 4.3);
`slotOptions` picker booked counts; the shared test DB holds legacy BOOKED rows whose
meaning shifts (sweep may hard-delete per the shared-DB ruling).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 13 tasks, one stack (lumen-sev, kernel+us in one reactor),
sequential kernel→us dependency, no external-interface risk beyond the already-ruled
semantics — above the Inline threshold, below SDD; shared-DB verify runs in an exclusive
window per the shared-test-server hazards memory.

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
