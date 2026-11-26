# Design: us-slot-options-window-fix

> Single-stack lightweight change (backend). Fixes the 2026-11-25 review
> findings on the slot-options path; the create path is the reference
> semantics and stays untouched.

## Context
The create path (the contract): `resolveSlotWindow` floors booking minutes
onto the MIDNIGHT grid (`(minutesOfDay / g) * g`), parses windows with the
strict trimmed `HH:mm` (`parseHhMm`), and fails CLOSED on any malformed window
(`end <= start` included) with A0400 — one bad window poisons every booking of
the modality. The picker diverged on all three axes and hangs on wrap-around
granularities. The main spec's "floors onto the grid from its start" clause
encoded the divergence; the delta corrects it.

## Goals / Non-Goals
**Goals:**
- Termination for any stored granularity (1440/720 wrap cases).
- The picker offers exactly the create's bookable set with matching occupancy
  windows and one aggregate query.

**Non-Goals:**
- No create-path / kernel change (`resolveSlotWindow` is the fixed reference).
- No config-surface validation (the fix mirrors whatever is stored verbatim —
  input validation on SlotConfigRequest is a separate concern, not this scope).
- No response-shape change (SlotOptionsView fields untouched).

## Decisions
- **D1 — day-minute integers everywhere**: window spans and grid enumeration
  compute in `int` day-minutes (`startMinutes`, `endMinutes`, `t += g`);
  `LocalTime` appears only for formatting. Termination is structural: the loop
  runs while `t < endMinutes`, incrementing by `g > 0` — no wrap path.
- **D2 — midnight-grid selection (the create mirror, review-revised)**: first grid
  point `t0 = ceil(startMinutes / g) * g`; offered slots are `t0, t0+g, … < endMinutes`.
  Each slot's occupancy cell is `[t, t+g)` — identical to the create's floored
  window, so picker counts and create acceptance agree. An off-grid window
  (e.g. 08:00 @ 45) yields 08:15…11:15; a window with no grid point inside
  yields zero slots. Two review corrections (2026-11-26): (a) the create's
  future-only guard (`requireFutureBooking`, strictly `after(dbNow)`) is part of
  bookability — only slot starts strictly after the DB clock (`selectDbNow`) are
  offered, so past dates offer nothing; (b) cell arithmetic runs on ABSOLUTE
  minutes from dayStart and the span load extends past midnight to the largest
  cell end, because granularities that do not divide 1440 (e.g. 50) put the
  last cell's tail on the next day — the tail counts, exactly as the create's
  `[t, t+g)` range query does.
- **D3 — strict parse + LAZY dead-end mirror (review-revised)**: reuse the
  create's parse contract (trimmed `HH:mm`; blank/null → malformed). The create
  validates windows LAZILY in order — a booking is accepted by the first
  containing VALID window and a malformed window only refuses bookings that
  REACH it — so the eager whole-modality refusal the first review drafted
  mirrored a semantics that does not exist. The read instead enumerates the
  valid windows in order and DEAD-ENDS at the first malformed one: that window
  and everything after is unreachable for acceptance and never advertised; the
  valid windows before it stay offered (a [bad-first] config answers
  configured=true with an empty list). The unconfigured/empty-config fail-soft
  branch stays first (unchanged).
- **D4 — one span load, in-memory bucketing**: a single
  `selectList` (booking_time column only) over `[dayStart, dayStart +
  maxCellEnd)` with the create's predicates (live not-cancelled + first-item
  modality token), then bucket each booking by
  `floor(minutesFromDayStart / g) * g` on the ABSOLUTE minute scale; a slot's
  `booked = bucket.getOrDefault(t, 0)`. Identical counts — the create's range
  query `[t, t+g)` and the bucket partition agree on the absolute scale, tails
  included — one round trip.
- **D5 — javadoc sync (doc-only)**: `RegistrationCreateRequest` class doc,
  `registerPreRegistration` doc, the walk-in comment, and
  `ExamSlotCapacityService(.Impl)` docs lose the retired BOOKED-entry wording
  (REGISTERED + PRE_REGISTERED stamp, live not-cancelled occupancy). The
  message-copy finding's interface promotion was REVERTED in review round 2 —
  the dead-end correction removed the picker's refusal site, leaving the
  promoted constant as dead public API; the kernel keeps its package-private
  constant and the kernel interface is untouched.
- **D6 — sorted, deduped union (review round 2)**: stored windows may overlap
  or arrive out of order — the candidate grid collects into a `TreeSet` (the
  web groups by `slot.start < 12:00` and keys rows on `slot.start`), and an
  empty candidate set answers empty BEFORE the `selectDbNow` probe (no DB
  round trip for configs that can never offer a slot).

## Risks / Trade-offs
- The offered slot set CHANGES for off-grid windows (by design — those slots
  were unbookable); the web renders whatever arrives, no contract change.
- The lazy dead-end mirror means a config with a malformed LATER window still
  advertises the earlier windows' slots — exactly what the create accepts; the
  first review's eager refusal would have darkened a working booking path
  (review correction, 2026-11-26).
- The bucketing load fetches the day's bookings once (rows, not counts) — for
  realistic daily volumes this is strictly cheaper than N indexed-less COUNTs;
  the span extends past midnight only when a granularity tail actually does.
