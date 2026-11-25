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

### Requirement: Exam-Order Aggregate

The kernel SHALL model a registration order as an aggregate of four members persisted
together — the order document (`exam_order`), the exam execution (`exam_execution`),
the report placeholder (`exam_report`), and the patient reference (`patient_uid` NOT
NULL plus a jsonb 患者快照). No member SHALL carry a department-specific field; the
snapshot refresh SHALL be guarded by report state and the channel SHALL reserve the
remote value. The order document additionally carries the pre-registration slot anchor
`booking_time` (nullable timestamp — walk-in orders carry NULL) and the entry-category
marker `registration_type` (varchar, `WALK_IN` 现场登记 / `PRE_REGISTERED` 预约预登记,
NOT NULL with the `WALK_IN` default) — the category is a business fact stamped
SERVER-SIDE from the create branch, never accepted from a client body, and it is the
discriminator the queue system (排队叫号) consumes instead of a `booking_time`
NULL/NOT NULL inference:

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

### Requirement: Document State Machine

The kernel SHALL drive every order through a single state machine —
已预约 →(签到)→ 已登记 → 处理中 → 已完成 — with the branches 取消预约 (from 已预约) and
已取消 (from 已登记 or 处理中). 预登记 (booking) creates an order directly as 已登记
(预约=预登记, user ruling 2026-11-24 — the arrival-free model); walk-in registration and
HIS two-phase confirm enter directly as 已登记; the 已预约 state stays defined and every
edge is unchanged, but no current flow produces it. The kernel SHALL reject any other
transition with a client-error signal and leave the stored status unchanged:

#### Scenario: Booking enters at 已预约

<!-- scenario superseded in place by the 2026-11-24 ruling 预约=预登记: the retired
shape (entering 已预约) is kept only in this comment for history -->

- **WHEN** a 预登记 (booking) creates an order
- **THEN** the order is stored in the 已登记 state
- **AND** the 已预约 state remains defined but unreachable from current flows

#### Scenario: Walk-in and HIS confirm enter at 已登记

- **WHEN** a walk-in registration commits without a booking
- **THEN** the order is stored in the 已登记 state
- **AND** the same holds for an order confirmed from the HIS intake cache

#### Scenario: Happy path advances through each stage

- **WHEN** 签到, 处理中, and 已完成 fire in sequence on a 已预约 order
- **THEN** the status advances 已登记 → 处理中 → 已完成, each step persisted

#### Scenario: Cancellation branches split by stage

- **WHEN** cancellation fires on a 已预约 order
- **THEN** the order lands in 取消预约
- **WHEN** cancellation fires on a 已登记 or 处理中 order whose report is not 已出
- **THEN** the order lands in 已取消
- **WHEN** cancellation fires on an order whose report is 已出
- **THEN** it is refused and the status is unchanged

#### Scenario: Illegal transition rejected

- **WHEN** a transition not defined by the state machine fires (e.g. 处理中 back to
  已预约, or any move out of 已完成)
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
Chinese message. The state-machine edges are unchanged (`BOOKED → REGISTERED` stays
defined).

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
