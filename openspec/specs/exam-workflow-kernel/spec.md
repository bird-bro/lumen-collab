# exam-workflow-kernel Specification

## Purpose

The exam-workflow kernel is the department-neutral core of the exam pipeline
(booking → registration → exam → report): one order aggregate, one document state
machine, exam-side numbering, and the seams departments extend. The kernel exists so
radiology and ultrasound workstations consume one kernel instead of forking a god
table per department.

<!-- synced from: lumen-core-registration-kernel 2026-11-19 -->

## Requirements

<!-- synced from: kernel-pre-registration 2026-11-24 -->

<!-- synced from: us-order-exam-room-sev 2026-11-26 -->
### Requirement: Exam-Order Aggregate

The kernel SHALL model a registration order as an aggregate of four members persisted
together — the order document (`exam_order`), the exam execution (`exam_execution`),
the report placeholder (`exam_report`), and the patient reference (`patient_uid` NOT
NULL plus a jsonb 患者快照). No member SHALL carry a department-specific field; the
snapshot refresh SHALL be guarded by report state and the channel SHALL reserve the
remote value. The order document additionally carries the pre-registration slot anchor
`booking_time` (nullable timestamp — walk-in orders carry NULL), the entry-category
marker `registration_type` (varchar, `WALK_IN` 现场登记 / `PRE_REGISTERED` 预约预登记,
NOT NULL with the `WALK_IN` default) — the category is a business fact stamped
SERVER-SIDE from the create branch, never accepted from a client body, and it is the
discriminator the queue system (排队叫号) consumes instead of a `booking_time`
NULL/NOT NULL inference — and the exam-room anchor `room_id` (BIGINT NULL): WHERE the
exam happens, chosen at creation and stamped on both entry branches. `room_id` is
nullable at the DB level (no natural default, no legacy backfill — legacy rows read
NULL); the CREATE-TIME requirement (the room must be supplied and valid) is owned by
the registering service's surface, not by the column.

#### Scenario: Aggregate persists as four linked members

- **WHEN** the kernel saves a new registration order carrying execution, report, and
  patient data
- **THEN** all four members persist linked to the order's identifier
- **AND** the patient reference is NOT NULL

#### Scenario: Snapshot refresh guarded by report state

- **WHEN** the snapshot refresh runs for an order whose report is not 已出
- **THEN** the jsonb snapshot updates from the delivered patient data
- **WHEN** the same refresh runs for an order whose report is 已出
- **THEN** the refresh is refused and the stored snapshot is unchanged

#### Scenario: Channel reserves the remote value

- **WHEN** an order is created without an explicit channel
- **THEN** `channel` records 本地申请
- **AND** the channel value set contains 远程申请 as a defined but unenabled value

#### Scenario: Booking time anchors the pre-registration slot

- **WHEN** an order is created with a future `booking_time`
- **THEN** the document persists with that timestamp as its pre-registration slot anchor
- **WHEN** an order is created without `booking_time`
- **THEN** the document persists with `booking_time` NULL and the order is not counted
  against any slot

#### Scenario: Registration type records the entry category

- **WHEN** an order is created through the walk-in branch (no future `booking_time`)
- **THEN** the document persists with `registration_type` `WALK_IN`
- **WHEN** an order is created through the pre-registration branch (a future
  `booking_time`)
- **THEN** the document persists with `registration_type` `PRE_REGISTERED`
- **AND** a client body posting a `registration_type` value has no effect — the stamp
  is server-owned

#### Scenario: Exam room anchors the exam destination

- **WHEN** an order is created through either entry branch (walk-in or
  pre-registration) with a validated room reference
- **THEN** the document persists with that `room_id` stamped — the room is chosen at
  creation (预约时选房 for pre-registered orders, 现场选房 for walk-ins), never
  derived
- **WHEN** a legacy order predates the column
- **THEN** `room_id` reads NULL and the order still loads (the column is nullable; the
  display layer renders the missing room as '—')


### Requirement: Document State Machine

The kernel SHALL drive every order through a single state machine —
已登记 →(检查开始)→ 检查中 →(诊断抢占)→ 诊断中 →(完成报告)→ 已完成 →(打印)→ 已打印 —
with the branch 已取消 (from 已登记, 检查中, or 诊断中, refused once the report is 已出).
预登记 (booking), walk-in registration, and HIS two-phase confirm enter directly as
已登记 (预约=预登记, user ruling 2026-11-24 — the arrival-free model). The machine
defines NO 已预约 and NO 取消预约 state: the former arrival vocabulary and its edges are
removed (user ruling 2026-11-26, six-state model — the 登记 order-status machine).
Forward edges stay permissive (every later stage is reachable from each earlier live
state) so an order cannot strand while a stage caller is absent; backward moves and
terminal-state exits do not exist. The kernel SHALL reject any other transition with a
client-error signal and leave the stored status unchanged:

#### Scenario: Booking enters at 已预约

<!-- scenario superseded in place: the retired shape (entering 已预约) survives only
in this comment for history; the 2026-11-24 ruling 预约=预登记 landed the 已登记 entry
and the 2026-11-26 six-state ruling removed the state itself -->

- **WHEN** a 预登记 (booking) creates an order
- **THEN** the order is stored in the 已登记 state
- **AND** the machine defines no 已预约 state — the former arrival step and its
  签到 edge are gone

#### Scenario: Walk-in and HIS confirm enter at 已登记

- **WHEN** a walk-in registration commits without a booking
- **THEN** the order is stored in the 已登记 state
- **AND** the same holds for an order confirmed from the HIS intake cache

#### Scenario: Happy path advances through each stage

- **WHEN** 检查开始, 诊断抢占, 完成报告, and 打印 fire in sequence on a live order
- **THEN** the status advances 已登记 → 检查中 → 诊断中 → 已完成 → 已打印, each step
  persisted
- **AND** the forward-permissive edges let every later stage fire from each earlier
  live state (e.g. 诊断抢占 from 检查中 or directly from 已登记)

#### Scenario: Cancellation branches split by stage

<!-- scenario superseded in place by the 2026-11-26 six-state ruling: the retired
shape (the 取消预约 branch from 已预约 and the two-way split) survives only in this
comment for history -->

- **WHEN** cancellation fires on a 已登记, 检查中, or 诊断中 order whose report is
  not 已出
- **THEN** the order lands in 已取消
- **WHEN** cancellation fires on an order whose report is 已出 (or on an order already
  in 已完成 or 已打印)
- **THEN** it is refused and the status is unchanged

#### Scenario: Illegal transition rejected

- **WHEN** a transition not defined by the state machine fires (e.g. 诊断中 back to
  已登记, any move out of 已打印, or the removed 已预约 vocabulary)
- **THEN** the kernel rejects it with a client-error signal
- **AND** the stored status is unchanged

### Requirement: Number Issuer Port

The kernel SHALL define a number-issuer port seam — issuing one number for a rule
family (the 检查号 accession and 预约号 booking families among them) — that consuming
services adapt to the platform's number-rule service (the row-lock rule machinery is
platform-owned, not kernel-owned). Kernel registration SHALL take accession numbers
ONLY from a port result and SHALL fail fast when the issuer fails — a fallback that
fabricates numbers never exists (numbers are never reused):

#### Scenario: Registration issues the accession through the port

- **WHEN** a registration creates an order without a caller-brought accession
- **THEN** the stored accession number is the number-issuer port's result verbatim

#### Scenario: Issuer failure refuses the registration

- **WHEN** the number-issuer port fails during registration
- **THEN** the registration is refused and no order row survives

### Requirement: Patient Registry Port

The kernel SHALL define a patient-registry port seam — resolve-or-register semantics
returning the patient identity (`pat_uid`, `pat_id`, resolved state) — that consuming
services adapt to the patient service. Kernel order creation SHALL take identity only
from a port result and SHALL refuse creation without one:

#### Scenario: Order creation consumes the port

- **WHEN** kernel services create an order
- **THEN** the patient reference comes from a patient-registry port result
- **AND** order creation without a resolved identity is refused

#### Scenario: Incomplete port result refused

- **WHEN** a port result carries no `pat_uid`
- **THEN** the kernel refuses to link the order and reports the failure

### Requirement: HIS Two-Phase Intake

The kernel SHALL provide two-phase HIS intake: an apply-cache stage (raw source +
dictionary-mapped target; unmatchable exam items dropped at cache time) followed by a
manual confirm creating the formal order — no server-side auto-commit, corrections
allowed only before commit, and the HL7v2 protocol layer itself out of scope:

#### Scenario: Cache stores matched apply and drops unmatched items

- **WHEN** an inbound apply caches with one matchable and one unmatchable exam item
- **THEN** the cache row stores source and target with the matchable item
- **AND** the unmatchable item is dropped at cache time

#### Scenario: Manual confirm creates the order at 已登记

- **WHEN** confirm fires for a cached apply with a resolved patient identity
- **THEN** a formal order is created entering 已登记 and the cache row is marked committed

#### Scenario: No auto-commit from the cache stage

- **WHEN** an apply is cached
- **THEN** no order exists until the confirm fires

#### Scenario: Cached apply corrected before commit

- **WHEN** the upstream corrects a not-yet-committed cached apply
- **THEN** the cache row updates its target
- **WHEN** the same correction targets a committed cache row
- **THEN** it is rejected

### Requirement: Registration Extension Hooks

The kernel SHALL fire ordered registration extension hooks inside the registration
transaction; consuming services register their hooks (PACS worklist push, 叫号, 分单,
HIS outbound all live outside the kernel). A hook failure SHALL roll back the
registration so no partial order survives:

#### Scenario: Hooks fire in registered order

- **WHEN** a registration completes
- **THEN** every registered hook executes in registration order

#### Scenario: Hook failure rolls back the registration

- **WHEN** a hook raises during registration
- **THEN** the transaction rolls back and no order row survives

<!-- synced from: kernel-pre-registration 2026-11-24 -->

### Requirement: Pre-Registration Support

The create entry status is `REGISTERED` for every path (预登记=已登记, user ruling
2026-11-24): a NULL/now/past `booking_time` is a 现场单 and a future slot is a 预登记 —
both enter `REGISTERED` in the same transaction as the aggregate save. The kernel SHALL
validate every future-slot create against the modality's slot capacity configuration.
Refusals SHALL ride the house `A0400` business-refusal pattern with a descriptive
Chinese message. The state-machine edges are the six-state machine's (the former
`BOOKED → REGISTERED` arrival edge is removed, user ruling 2026-11-26).

#### Scenario: Entry status derives from booking time in one transaction

<!-- scenario superseded in place: the retired shape (future slot → BOOKED) survives
only in this comment for history -->

- **WHEN** the create flow runs with `booking_time` NULL or now/past
- **THEN** the order enters `REGISTERED` (现场单 submit registers directly)
- **WHEN** the create flow runs with a future `booking_time`
- **THEN** the order enters `REGISTERED` as well (预登记=已登记 — the slot anchor records
  the appointment, not a pending state)
- **AND** both derivations persist in the same transaction as the aggregate save

#### Scenario: Pre-registration refused when the slot is at capacity

- **WHEN** a pre-registration create targets a slot whose live not-cancelled order count
  already equals the modality's configured capacity
- **THEN** the create is refused with an `A0400` business refusal naming the full slot
  (e.g. 该时段已满)
- **AND** nothing persists

#### Scenario: Pre-registration below capacity occupies the slot

- **WHEN** a pre-registration create targets an open slot whose live not-cancelled count
  is below capacity
- **THEN** the order persists as `REGISTERED` with the requested `booking_time`
- **AND** the slot count for that window increases by one

#### Scenario: Slot counting uses live pre-registered orders only

<!-- scenario superseded in place: the retired shape (counting live BOOKED only) and
the REGISTERED-don't-occupy clause survive in this comment for history -->

- **WHEN** the capacity check counts a slot window
- **THEN** it counts only non-deleted, non-cancelled orders whose `booking_time` falls
  in that window (未取消即占位 — the reservation survives every live stage)
- **AND** walk-in orders (NULL `booking_time`) do not occupy slot capacity

#### Scenario: Pre-registration refused outside open windows or in the past

- **WHEN** a pre-registration create carries a `booking_time` outside the modality's
  configured open time windows
- **THEN** the create is refused with an `A0400` business refusal naming the window rule
- **WHEN** a pre-registration create carries a `booking_time` in the past
- **THEN** the create is refused with an `A0400` business refusal

#### Scenario: Unconfigured modality refuses pre-registration

- **WHEN** a pre-registration create targets a modality with no slot capacity
  configuration
- **THEN** the create is refused with an `A0400` business refusal (pre-registration is
  opt-in per modality — the safe default)
- **AND** creates without `booking_time` are unaffected by the missing configuration

#### Scenario: Vendored DDL keeps the department-owned config storage

- **WHEN** a department service adopts pre-registration
- **THEN** it vendors the kernel slot-config DDL into its own Flyway ledger (the
  exam_order/execution/report vendoring precedent)
- **AND** the kernel owns only the entity shape, the validation service, and the
  copy-from DDL source — config rows belong to the department schema
