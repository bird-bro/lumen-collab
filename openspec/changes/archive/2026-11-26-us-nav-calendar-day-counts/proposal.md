# Proposal: us-nav-calendar-day-counts

## Why

The date-nav popover's mini month calendar (the 2026-11-26 in-session addition) shows
dates but no workload signal — the desk flips days blind. A per-day registration count
turns the calendar into a workload preview (which days carry orders), mirroring the
booking dialog's day-level badge pattern.

## What Changes

- The datenav popover's day cells carry the day's registration count as a small
  badge (the booking dialog's 余 N visual family): count > 0 renders the number in
  the primary tint under the day number; days without orders render no badge.
- The counts aggregate CLIENT-SIDE over the existing per-date page read
  (parallel prefetch of the displayed month's days, `pageSize: 1`, reading the
  `total` — the recorded booking-dialog deferral of a dedicated day-level range
  endpoint stands; no new backend contract).
- Refetch when the displayed month changes; the active day's cell keeps its
  selected fill; the calendar grows to fit the badge line.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-us-station`: the Registration Page Shell gains the datenav-calendar-counts
  scenario (the popover calendar itself is this change's spec surface — it was an
  in-session addition never spec'd).
