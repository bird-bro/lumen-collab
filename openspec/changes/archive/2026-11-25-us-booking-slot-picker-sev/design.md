# Design: us-booking-slot-picker-sev (retrospective closure)

> Written retrospectively at archive time — the implementation landed first
> (GET /orders/slot-options, verified PASS 2026-11-25). Coordination parent:
> `openspec/coordination/us-booking-slot-picker.md`.

## Context

The booking slot dialog (the 2026-11-24 trigger+dialog redesign) needs the day's
slot grid with live occupancy to render the 满员标灰 picker; the create path owns
the capacity truth.

## Goals / Non-Goals

**Goals:** a read-only slot-options endpoint mirroring the create path's count
predicate and the config grid; fail-soft on unconfigured modalities.

**Non-Goals:** no config-row lock on the read (the FOR UPDATE serialization stays
create-side; a stale read only costs a gray/white tint); no dialog state semantics.

## Decisions

- **D1 — one strict GET**, `/api/v1/us/orders/slot-options?date&modality`, gated to
  `us:order:create` ∪ `setting:slot:list`; invalid date refuses A0400 (a booking
  aid, not a general calendar).
- **D2 — the occupancy predicate mirrors create**: live not-cancelled orders in the
  booking_time window scoped by the first-item modality token (refreshed to the
  us-prereg-as-registered anchor — the original BOOKED-only wording predates that
  ruling).
- **D3 — broken windows skip on read** (fail-soft mirrors create's fail-closed):
  an unparseable open window drops from the grid; the create still refuses the
  same booking.

## Risks / Trade-offs

- [A stale read between render and submit could overbook] → the create path's FOR
  UPDATE + capacity validation remains the authority; the picker tint is advisory.

## Open Questions

(none)
