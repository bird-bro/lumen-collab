# exam-workflow-kernel Delta

## MODIFIED Requirements

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
