# Design: us-booking-slot-picker-web (retrospective closure)

> Written retrospectively at archive time — the dialog landed first (verified PASS
> 2026-11-25). Coordination parent: `openspec/coordination/us-booking-slot-picker.md`.
> UI authority: `docs/prototypes/us-booking-slot-dialog.html`.

## Context

The 预约时间 cell collapsed the inline date picker + slot select into ONE trigger +
dialog (the 2026-11-24 mid-apply ruling: calendar always visible, no popover).

## Goals / Non-Goals

**Goals:** D+1 booking domain, day-level remaining badges over the existing
slot-options read, the grouped slot grid with capacity meters, the tight/full
emphasis, null-bookingTime walk-in preservation, fail-soft unconfigured.

**Non-Goals:** a dedicated day-level range endpoint (future optimization — the
client aggregates per-date reads); changing the bookingTime submit shape.

## Decisions

- **D1 — one trigger + dialog** (`aria-label="选择预约时段"`, `role="dialog"`): the
  prototype's density without a popover layer.
- **D2 — client-side badge aggregation**: parallel prefetch of the displayed
  month's future days over `/orders/slot-options`; unconfigured/failed days carry
  no badge (no fabricated data — the backend-first ruling).
- **D3 — the submit contract is untouched**: bookingTime stays the slot START
  composition; clearing submits null (walk-in unchanged).

## Risks / Trade-offs

- [Month prefetch multiplies slot-options calls] → bounded by the month grid and
  the fail-soft degradation; the dedicated range endpoint is the recorded future
  optimization.

## Open Questions

(none)
