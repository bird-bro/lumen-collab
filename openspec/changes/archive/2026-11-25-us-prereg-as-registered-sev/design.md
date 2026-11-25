# Design: us-prereg-as-registered-sev

> Coordination parent: `openspec/coordination/us-prereg-as-registered.md`.

## Context

The kernel aggregate (`exam_order` + execution + report) and the US create flow
(`RegistrationOrderServiceImpl`) already implement the pre-registration machinery from
kernel-pre-registration: future slot → `BOOKED`, capacity dance over live `BOOKED`
counts, manual arrival confirmation (BOOKED → REGISTERED). The us ledger holds V1–V5;
the kernel copy-from source holds db/kernel V1–V2 applied to the shared test server's
`core` schema. The user ruling (2026-11-24) collapses 预约 into 登记 and moves the
category to a dedicated column.

## Goals / Non-Goals

**Goals:**
- `exam_order.registration_type` as kernel-generic, server-stamped business data
- US create path: both branches enter `REGISTERED`; occupancy counts not-cancelled
  slot-anchored orders
- View exposure: `registrationType` + `createBy` (alongside the existing `createTime`)
- Kernel state machine and validation service logic untouched

**Non-Goals:**
- Operator NAME resolution (the uid suffices for audit/query; name lookup is a
  system-domain concern for a later proposal)
- The queue system itself (取号/签到 arrival signals) — only the discriminator column
- Removing the kernel `BOOKED` state or its edges — they stay defined and machine-tested
- Touching `PreRegistrationPolicy.requireFutureBooking` (the past-guard stays) or the ext
  hook seam (us-apply-info owns it right now)

## Decisions

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
  the us flow, not modified.** The walk-in branch keeps `deriveEntryStatus(null, dbNow)`
  (returns REGISTERED) only for symmetry… no — both branches set
  `ExamOrderStatus.REGISTERED` explicitly; the kernel support method stays for other
  departments (never-modify rule: no overload churn on a method the us flow simply stops
  calling). The future-slot branch keeps `requireFutureBooking` + the capacity dance
  verbatim; only the derived status and the stamp differ.
- **D4 — Occupancy predicate: NOT IN the two cancel states.** Both count sites
  (`registerPreRegistration` occupancy, `slotOptions` picker display) swap
  `.eq(status, BOOKED)` for `.notIn(status, BOOKING_CANCELLED, CANCELLED)` — 未取消即占位,
  which also correctly counts any legacy `BOOKED` rows still in the test DB and every
  live stage after. Walk-ins never collide (NULL booking_time, unchanged). The MP
  logic-delete filter keeps handling non-deleted exactly as today.
- **D5 — Cancel branch: zero code change.** `cancel()` keeps its BOOKED →
  BOOKING_CANCELLED split; the US flow simply stops producing BOOKED rows, so the branch
  becomes reserved. The spec scenario is updated; the code is already correct.
- **D6 — Kernel numbering: db/kernel V3, us ledger V6.** The kernel canonical source
  gains `V3__exam_order_registration_type.sql` (ADD COLUMN IF NOT EXISTS, the V4/V5
  house style, comment lines without semicolons) for the kernel test schema; the us
  ledger vendors the same statements as `V6__exam_order_registration_type.sql`. Additive
  ALTER only — no applied migration is rewritten (the never-rewrite ruling).
- **D7 — View fields are additive.** `RegistrationOrderView` gains `registrationType`
  and `createBy`; `toView` maps them from the order. The create response
  (`RegistrationCreateResult`) gains `registrationType`. Builds on top of the in-flight
  us-apply-info-sev view fields (same files, same flattening pattern).

## Risks / Trade-offs

- [Legacy BOOKED rows in the shared test DB change meaning after the swap] → they keep
  occupying their slots under the NOT IN predicate (correct); the status chips render
  them as 预约 until they are cleaned or consumed. Smoke-test sweep may hard-delete them
  per the shared-DB ruling — task-level note.
- [Slot-count semantics change could surprise the picker tests] → the tests pin counts
  via seeded BOOKED rows; seeded rows become "not-cancelled slot-anchored" and still
  count — update fixtures only where a test asserted the OLD exclusion semantics.
- [Two sources of truth (status vs registration_type) can drift] → the type is stamped
  once at create and never rewritten by any transition (the state machine patches status
  only); drift is structurally impossible.
- [COMPLETED orders keep occupying their slot window] → irrelevant in practice:
  `requireFutureBooking` refuses past bookings, so a completed window is never
  re-validated; within a day, different windows do not overlap.

## Migration Plan

1. Kernel V3 + us V6 land additively (default WALK_IN backfills every existing row —
   wrong for legacy pre-registrations, which the shared test DB only; re-stamp or delete
   them in the sweep, see risks).
2. Code swap (stamps + counts + view) ships in the same change; no deploy ordering
   inside the monolith.
3. Rollback: the column is nullable-safe to ignore; reverting the code swap restores the
   old flow (BOOKED rows re-appear for new pre-registrations).

## Open Questions

(none — the status fork was ruled by the user 2026-11-24: 预登记直接落已登记)
