<!-- kernel-pre-registration delta. Parent coordination: openspec/coordination/us-registration-redesign.md
     (拍板 02-B / 06-A). MODIFIED blocks are supersets of the main-spec scenarios (delta layering rule). -->

## MODIFIED Requirements

### Requirement: Exam-Order Aggregate

The kernel SHALL model a registration order as an aggregate of four members persisted
together — the order document (`exam_order`), the exam execution (`exam_execution`),
the report placeholder (`exam_report`), and the patient reference (`patient_uid` NOT
NULL plus a jsonb 患者快照). No member SHALL carry a department-specific field; the
snapshot refresh SHALL be guarded by report state and the channel SHALL reserve the
remote value. The order document additionally carries the pre-registration slot anchor
`booking_time` (nullable timestamp — walk-in orders carry NULL):

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

## ADDED Requirements

### Requirement: Pre-Registration Support

The kernel SHALL derive the create entry status from `booking_time` in one transaction —
future slot → `BOOKED` (预登记态), NULL/now/past → `REGISTERED` — and SHALL validate every
future-slot create against the modality's slot capacity configuration. Refusals SHALL ride
the house `A0400` business-refusal pattern with a descriptive Chinese message. The
`BOOKED → REGISTERED` state-machine edge is unchanged.

#### Scenario: Entry status derives from booking time in one transaction

- **WHEN** the create flow runs with `booking_time` NULL or now/past
- **THEN** the order enters `REGISTERED` (现场单 submit registers directly)
- **WHEN** the create flow runs with a future `booking_time`
- **THEN** the order enters `BOOKED` (预登记态)
- **AND** both derivations persist in the same transaction as the aggregate save

#### Scenario: Pre-registration refused when the slot is at capacity

- **WHEN** a pre-registration create targets a slot whose live `BOOKED` order count
  already equals the modality's configured capacity
- **THEN** the create is refused with an `A0400` business refusal naming the full slot
  (e.g. 该时段已满)
- **AND** nothing persists

#### Scenario: Pre-registration below capacity occupies the slot

- **WHEN** a pre-registration create targets an open slot whose live `BOOKED` count is
  below capacity
- **THEN** the order persists as `BOOKED` with the requested `booking_time`
- **AND** the slot count for that window increases by one

#### Scenario: Slot counting uses live pre-registered orders only

- **WHEN** the capacity check counts a slot window
- **THEN** it counts only non-deleted `BOOKED` orders whose `booking_time` falls in that
  window
- **AND** `REGISTERED` orders (arrived patients) and walk-in orders (NULL `booking_time`)
  do not occupy slot capacity

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
