# exam-workflow-kernel Delta

## MODIFIED Requirements

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
