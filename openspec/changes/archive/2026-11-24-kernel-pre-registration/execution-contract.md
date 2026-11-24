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

- **Change name**: kernel-pre-registration
- **Problem to solve**:
The US workstation redesign (拍板 02-B) makes submit register in one step and redefines
预约 as **预登记** (pre-registration): a checkup — e.g. a prenatal follow-up — booked to a
specific day + time slot. The kernel has a `BOOKED` entry state but no booking timestamp
and no slot-capacity concept, so a pre-registration cannot be placed on a slot, cannot be
refused when a slot is full, and the desk cannot distinguish a pre-registered order from a
walk-in by data. Radiology and endoscopy workstations will need the same mechanism, so the
capability belongs in the department-neutral kernel, not the US service.

<!-- AI: summarize the above into 1-2 sentences -->
- **Summary**: the exam-workflow kernel gains the pre-registration substrate — a
  `booking_time` slot anchor on `exam_order`, a derived entry-status policy
  (future → BOOKED, NULL/now/past → REGISTERED), and a per-modality slot-capacity
  config + validation service — all kernel-library-only, consumed by proposal ②'s US
  create flow.
- **In scope**:
  - `exam_order.booking_time timestamp` (kernel vendored DDL source V2; `booking_no` untouched)
  - `PreRegistrationPolicy.deriveEntryStatus` (pure time math, DB-server-time boundary supplied by caller)
  - `exam_slot_config` (per-modality row: granularity_minutes default 30, slot_capacity default 4,
    open_windows jsonb default '[]') + entity + mapper + typed jsonb handler
  - `ExamSlotCapacityService`: `resolveSlotWindow(config, bookingTime)` + `validatePreregistration(modality, bookingTime, occupancyCount)`
    with the four A0400 refusals (design D5) and `FOR UPDATE` config-row serialization (D7)
  - Kernel tests on the test-only `core` schema (V1+V2 applied), aggregate round-trip guards
- **Out of scope**:
  - Slot entities / reservation / cross-day schedules (v2 号池, EIS BookingNumber)
  - Pre-registration number issuance (`booking_no` stays unused in v1)
  - Controller/API surface (kernel carries none — proposal ② extends the contract)
  - Config CRUD UI (proposal ③ web delta)
  - Changes to `registerOrder`'s signature or existing state-machine edges (D1: policy helper, caller composes)
  - Department vendoring of V2 (proposal ②'s task) and radiology/endo adoption

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-workflow-kernel/spec.md`
6:### Requirement: Exam-Order Aggregate
16:#### Scenario: Aggregate persists as four linked members
23:#### Scenario: Snapshot refresh guarded by report state
30:#### Scenario: Channel reserves the remote value
36:#### Scenario: Booking time anchors the pre-registration slot
46:### Requirement: Pre-Registration Support
54:#### Scenario: Entry status derives from booking time in one transaction
62:#### Scenario: Pre-registration refused when the slot is at capacity
70:#### Scenario: Pre-registration below capacity occupies the slot
77:#### Scenario: Slot counting uses live pre-registered orders only
85:#### Scenario: Pre-registration refused outside open windows or in the past
93:#### Scenario: Unconfigured modality refuses pre-registration
101:#### Scenario: Vendored DDL keeps the department-owned config storage

All 11 scenarios confirmed — the 7 new/changed Pre-Registration + booking-time scenarios
map to tasks 1.x/2.x/3.2; the 3 preserved Aggregate scenarios are regression-guarded by
the existing suites (task 3.1). The vendored-DDL scenario is demonstrated kernel-side by
the V2 source + `core`-schema application (task 1.2); the department-side vendoring step
belongs to proposal ②'s tasks.

## Design Constraints

- **Architecture/Decisions**:
- **D1 — Policy helper, not a `registerOrder` rewrite.** The entry-status derivation ships
  as a kernel policy component (e.g. `PreRegistrationPolicy.deriveEntryStatus(bookingTime)`):
  NULL/now/past → REGISTERED, future → BOOKED. `registerOrder` keeps its "caller chooses"
  contract; proposal ②'s create flow calls the helper then passes the derived status.
  *Alternative rejected:* changing `registerOrder` to derive internally — it would break the
  documented caller-choice contract (spec Patient Registry Port) and force HIS two-phase
  intake through the same path.
- **D2 — Capacity check belongs to the kernel service, the count query to the caller's
  mapper.** The kernel `ExamSlotCapacityService` exposes `validatePreregistration(modality,
  bookingTime, occupancyCount)` + `resolveSlotWindow(config, bookingTime)`; the occupancy
  count itself is a `SELECT count(*)` on the department's own `exam_order` (status=BOOKED,
  booking_time in window) — the department service runs it against its own schema and hands
- **Technical constraints**:
  - House refusal pattern: `BusinessException(ExceptionEnum.ERROR_A0400, <中文消息>)` — no new
    error-code enum; D5 message set: 该时段已满 / 不在开台时间窗内 / 预登记时间不能早于当前时间 /
    该检查类型未开通预登记 (+ fail-closed variant for unparseable windows)
  - Kernel = non-executable library: no controllers, no boot entry in main sources
    (KernelModuleArchitectureTests keeps its teeth)
  - Entity Lombok combo `@Data @Builder @AllArgsConstructor @NoArgsConstructor`; timestamps
    are `java.util.Date` (EntityBase/ExamExecution precedent)
  - V2 comment lines carry NO semicolons (V36 lesson); V1 source stays untouched; modality
    column is the dict-1024 value token, varchar(16) (org_device.type / number_rule precedent)
  - Slot window semantics (task 2.3 pins tests): grid slot = `[floor(timeOfDay, granularity),
    floor + granularity)`; open window containment = `start <= slotStart < end` (end exclusive).
    NOTE: design D4's worked example ("09:00–09:47 window [09:00,10:00)") is internally
    inconsistent — the grid reading above is the self-consistent one and is what the tests pin.
  - DB-server-time boundary: the now/past comparison uses `SELECT now()::timestamp`
    through the mapper, never app clocks (design Risks row 2)
  - TDD on the shared test server `core` schema; kernel test fixtures ride `@Transactional`
    rollback (SnapshotRefreshGuardTests precedent); JDK 11 reactor builds from `lumen-sev/`
  - Never modify existing public methods — additive fields/overloads only

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
- Batch 1 — DDL source + entities (tasks 1.1–1.4): V2 copy-from source; RED schema test
  (booking_time + exam_slot_config exist after V1+V2 on the `core` schema); ExamOrder
  .bookingTime; ExamSlotConfig entity + mapper + SlotWindow/SlotWindowBounds + typed handler
- Batch 2 — policy + capacity service (tasks 2.1–2.6): RED policy unit tests → policy
  component; RED window/refusal tests → capacity service impl; integration flow test
  (derive → validate → registerOrder; A0400 past refusal; FOR UPDATE lock-order probe)
- Batch 3 — regression guards (tasks 3.1–3.2): existing kernel suites green with V2 in the
  ledger; booking_time aggregate round-trip (NULL → NULL; future ts → equal)
- Batch 4 — verification (tasks 4.1–4.3): full reactor test run; /opsx:verify credential;
  coordination registry → verified

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**:
  - V2 schema artifacts exist after V1+V2 (task 1.2 RED)
  - deriveEntryStatus NULL/now/past → REGISTERED, future → BOOKED (task 2.1 RED)
  - resolveSlotWindow grid flooring inside a window; outside → 不在开台时间窗内;
    empty/unparseable → fail-closed (task 2.3 RED)
  - validatePreregistration: missing config → 该检查类型未开通预登记; count ≥ capacity →
    该时段已满; below → pass with resolved bounds (task 2.4 RED)
  - composed create flow: future booking persists BOOKED through registerOrder; past →
    A0400 预登记时间不能早于当前时间; FOR UPDATE lock-order probe (task 2.6 RED)
- **Required edge cases**:
  - boundary: booking exactly at DB now → REGISTERED (derivation), validate refuses ≤ now
  - window boundary: slot start == window start allowed; == window end refused (end exclusive)
  - granularity flooring across the 12:00/14:00 window gap
  - capacity exactly equal → refuse; capacity-1 → pass
- **Regression-sensitive areas**:
  - existing aggregate/state-machine/registration/HIS suites (V1 untouched, additive V2)
  - ExamOrder autoResultMap interplay with the new plain timestamp column
  - ExamItemRefListJsonbTypeHandler untouched (typed handler for open_windows is a new class)

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: single-stack kernel-only change, 13 tasks with TDD per task and
  a precise D1–D7 design; batches 1–3 implemented sequentially in-context with a per-batch
  WHEN/THEN review checkpoint, batch 4 is the verification gate. No subagent dispatch —
  the SDD machinery earns its cost on cross-stack features, not a one-module kernel delta.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after each batch — diff vs spec WHEN/THEN; before the
  reactor run — full contract constraint sweep (A0400 messages, no-semicolons, Date types,
  library boundary)
- **Blocking categories**: Critical / Important findings on spec scenarios or the four D5
  refusal messages

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
