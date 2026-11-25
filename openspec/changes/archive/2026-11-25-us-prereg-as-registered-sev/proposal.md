# Proposal: us-prereg-as-registered-sev

> Coordination parent: `openspec/coordination/us-prereg-as-registered.md`
> (user rulings 2026-11-24). Sibling: `us-prereg-as-registered-web`.

## Why

The archived us-registration-redesign made 预约 a separate lifecycle stage: a
pre-registered order enters `BOOKED` and a nurse must run 登记 (arrival confirmation)
before the exam. User ruling 2026-11-24: **预约 IS 预登记** — the registration already
happened when the booking was made, the patient arrives and goes straight into the exam.
The manual confirmation step contradicts the business, and once both entry kinds merge
into one state, `booking_time` NULL/NOT NULL is the only discriminator left — a fragile
inference for the coming queue system (排队叫号), not a business fact.

## What Changes

- **Kernel `exam_order` gains `registration_type`** varchar(16) NOT NULL DEFAULT
  'WALK_IN' (`WALK_IN` 现场登记 / `PRE_REGISTERED` 预约预登记), stamped SERVER-SIDE by
  the create branch — never accepted from the client body.
- **Pre-registration enters `REGISTERED`** (预登记=已登记): a future-slot create no
  longer lands `BOOKED`; the kernel state machine and its edges are UNCHANGED (`BOOKED`
  stays defined but US-unreached).
- **Slot occupancy re-anchored** (**BREAKING** for the counting rule): capacity counts
  live non-deleted, non-cancelled orders whose `booking_time` falls in the window
  (未取消即占位) — previously live `BOOKED` orders only. Walk-ins (NULL booking_time)
  never occupy.
- **Cancel**: a pre-registered order (REGISTERED) cancels → `CANCELLED`; the
  `BOOKING_CANCELLED` branch stays machine-reserved but the US flow no longer produces
  it.
- **The order view exposes `registrationType` and `createBy`** — the 登记人 stamp was
  recorded but never returned; the view also carries `createTime` (登记时间).
- **登记时间/登记人 ruling recorded in specs**: both stamps ARE the create
  `create_time`/`create_by` for both kinds; no new timestamp columns (the earlier
  `registered_time/registered_by` idea is retracted). Exam start stays on
  `exam_execution.start_time/operator_id`.
- us ledger vendors the kernel DDL addition as its next migration (V6, additive).

## Capabilities

### New Capabilities

(none — the exam workflow and US station capabilities already own this behavior)

### Modified Capabilities

- `exam-workflow-kernel`: Exam-Order Aggregate (add `registration_type` to the document
  shape), Pre-Registration Support (entry derivation future→REGISTERED; occupancy
  anchored to booking_time + not-cancelled), Document State Machine (booking enters
  已登记; edges unchanged).
- `us-station`: Registration Surface (create stamps registration_type and enters
  REGISTERED for both kinds; occupancy/cancel scenarios re-anchored; the view carries
  registrationType + operator stamps).

## Impact

- `lumen-core`: canonical kernel DDL source (exam_order column), `ExamOrder` entity
  field. NO state-machine or validation-service logic change beyond the occupancy query
  anchor used by department flows.
- `lumen-us`: V6 migration (vendor copy), create-path stamps in
  `RegistrationOrderServiceImpl`, occupancy/count queries re-anchor, cancel branch note,
  `RegistrationOrderView` fields. Builds on top of the in-flight us-apply-info-sev.
- API shape: create response + page rows gain `registrationType`; page rows gain
  `createBy` (additive — existing consumers unaffected).
- Frontend consumption handled by `us-prereg-as-registered-web` (builds after this).
