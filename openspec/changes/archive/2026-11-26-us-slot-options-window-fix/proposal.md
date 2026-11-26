# Proposal: us-slot-options-window-fix

> Lightweight single-stack change (backend only; the web renders whatever slots
> arrive — response shape unchanged). Fixes the four review findings the
> archived `us-booking-slot-picker` left in the slot-options path, plus the
> stale BOOKED-entry javadoc `us-prereg-as-registered` left behind.

## Why
The 2026-11-25 code review surfaced four defects in
`RegistrationOrderServiceImpl.slotOptions` (and one doc debt family):

1. **Infinite loop** — `cursor.plusMinutes(granularity).isAfter(windowClose)`
   never advances when `LocalTime.plusMinutes` wraps (granularityMinutes=1440,
   or 720 with an 08:00–20:00 window): the servlet thread pins and one
   unbounded `selectCount` runs per iteration.
2. **Grid misalignment** — the picker enumerates from the raw window start,
   but the create path floors booking times onto the MIDNIGHT granularity grid
   (`ExamSlotCapacityServiceImpl.resolveSlotWindow`): an off-grid window start
   (e.g. 08:00 @ granularity 45) offers slots the create refuses (08:00 floors
   to 07:30, outside the window) and counts occupancy on different windows
   (08:45 is booked into the 08:15–09:00 cell, not the offered 08:45–09:30).
3. **Parse divergence** — the picker's ISO `LocalTime.parse` accepts the
   seconds form the create's strict trimmed `HH:mm` refuses; the picker must
   use the create's strict contract and mirror the create's LAZY window
   validation (dead-end at the first malformed window) — plus the create's
   future-only guard (past dates offer nothing) and cross-midnight tail
   counting. (The 2026-11-25 review draft said "fail CLOSED the whole
   modality" — corrected by the apply-time review: the kernel validates
   lazily, and an eager refusal would darken a working booking path.)
4. **N+1 counts** — one unindexed `exam_order` COUNT with a jsonb predicate
   per slot of the grid (up to ~72 per picker open).

The main spec's clause "each open window floors onto the grid from its start"
itself encodes divergence (2) — this change corrects the clause to the create's
midnight-anchored semantics.

## What Changes
- `slotOptions` enumerates in day-minute integers (no `LocalTime` wrap —
  termination is unconditional) and offers exactly the create's bookable set:
  midnight-grid points `t` with window `start <= t < end`; each slot's
  occupancy window is `[t, t+g)` — the same cell the create floors into.
- Window parsing mirrors the create: strict trimmed `HH:mm`; ANY malformed
  window (unparseable or `end <= start`) refuses A0400 with the create's
  fail-closed message — the picker never advertises an unbookable modality.
- Occupancy becomes ONE day-span load of `booking_time` values (same
  not-cancelled + first-item-modality predicates) bucketed in memory by floored
  cell — identical counts, one round trip.
- Javadoc sync (doc-only): the retired BOOKED-entry wording on
  `RegistrationCreateRequest`, `registerPreRegistration`, the walk-in comment,
  and `ExamSlotCapacityService(.Impl)` aligns with the
  `us-prereg-as-registered` behavior (REGISTERED + PRE_REGISTERED stamp, live
  not-cancelled occupancy).

## Capabilities

### New Capabilities

### Modified Capabilities
- `us-station`: "Booking Slot Options Read" — the grid clause corrects from
  "floors onto the grid from its start" to the create's midnight-anchored grid
  selection; new scenarios pin wrap-around termination, off-grid windows, and
  the fail-closed parse mirror; the existing three scenarios are restated
  unchanged in behavior.

## Impact
- `lumen-sev/lumen-us`: `service/impl/RegistrationOrderServiceImpl.java`
  (slotOptions rewrite + registerPreRegistration/walk-in javadoc),
  `dto/RegistrationCreateRequest.java` (class javadoc only),
  `UsRegistrationApiTests.java` (new red-green scenarios + existing occupancy
  scenario as regression guard).
- `lumen-sev/lumen-core`: `ExamSlotCapacityService.java` — javadoc sync PLUS one
  additive public constant (`MSG_INVALID_WINDOWS`, the D5 token promoted to its
  single home); `ExamSlotCapacityServiceImpl.java` drops the package-private
  duplicate (references the inherited constant; behavior byte-identical).
- Web: none (SlotOptionsView shape unchanged; the slot list content for
  off-grid/malformed configs changes as intended).
- No migration, no error-code addition (reuses A0400 + the create's message).
