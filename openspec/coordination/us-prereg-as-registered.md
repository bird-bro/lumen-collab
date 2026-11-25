# us-prereg-as-registered — Coordination

> Feature: 预约(预登记)直接落「已登记」+ 登记类别字段 `registration_type`。
> Cross-stack pair tracked here. Created 2026-11-24.

## Problem

The archived us-registration-redesign modelled 预约 and 登记 as separate stages: a
pre-registered (预约) order enters `BOOKED` and needs a manual 登记 (arrival
confirmation) on the list before the exam can start. User ruling 2026-11-24: 预约 IS
预登记 — the registration already happened when the booking was made; the patient
arrives and goes straight into the exam with NO re-check-in. Once both entry kinds
merge into one state, the status column cannot distinguish them, and the
登记时间/登记人 question resolves to the create stamps — not new columns.

## User rulings (2026-11-24)

1. **预约=预登记**: a pre-registered order enters `REGISTERED` at creation, same as
   walk-ins; no arrival confirmation step. The kernel state machine and its edges are
   UNCHANGED — `BOOKED` stays a defined but US-unreached state.
2. **登记时间/登记人 = `exam_order.create_time / create_by`** for BOTH kinds. The
   earlier `registered_time / registered_by` idea is RETRACTED. Exam start keeps
   `exam_execution.start_time / operator_id`; the arrival moment itself defers to the
   future queue system (取号/签到).
3. **New kernel column `exam_order.registration_type`** varchar(16) NOT NULL DEFAULT
   'WALK_IN', values `WALK_IN` / `PRE_REGISTERED`, stamped SERVER-SIDE at creation by
   the branch taken — never accepted from the client body. This is the future
   queue-system (排队叫号) discriminator; the `booking_time` NULL/NOT NULL inference is
   retired from both display and stats reasoning.
4. **Cancel**: a pre-registered order (REGISTERED) cancels → `CANCELLED`. The
   `BOOKING_CANCELLED` machine branch stays reserved; the US flow no longer produces
   BOOKED rows.
5. **Slot occupancy re-anchored**: capacity counts live (non-deleted, non-cancelled)
   orders whose `booking_time` falls in the window — 未取消即占位. Walk-ins (NULL
   booking_time) never occupy.
6. **View exposure**: `registrationType` + `createBy` join the order view (createBy was
   recorded but never returned; the 操作人 display decision rides the proposal).
7. **Web rider**: the wide filter labeled 申请时间 actually binds `create_time` —
   relabeled 创建时间 (the real ext apply_time from us-apply-info made the label
   collide).

## Change registry

| Change | Stack | Scope |
|---|---|---|
| us-prereg-as-registered-sev | lumen-core + lumen-us | kernel spec deltas (aggregate column, entry derivation, occupancy anchor) + us-station delta + V6 vendor copy + create-path stamps + view fields |
| us-prereg-as-registered-web | lumen-web | list category tag 现场/预约, 登记 action removal, chip default, hint text, filter relabel |

## Adjacent in-flight work

- us-apply-info-sev/-web (active): both touch `RegistrationOrderView` and the
  registration page. This pair builds ON TOP of it — the ext/apply fields are untouched
  here.
- us-booking-slot-picker-sev/-web, us-slot-list-filter (active): same page surface; the
  picker and the filter row behavior are unaffected except the filter LABEL.

## Spec touch map

- exam-workflow-kernel: Exam-Order Aggregate (MODIFIED — registration_type column),
  Pre-Registration Support (MODIFIED — entry derivation + occupancy anchor), Document
  State Machine (MODIFIED — booking entry prose/scenario; edges unchanged).
- us-station: Registration Surface (MODIFIED — create/occupancy/cancel/view scenarios).
- web-us-station: Order Form and Actions (MODIFIED — both kinds submit-register; 登记
  action dropped), Order List (MODIFIED — category tag, chip default, filter relabel).
