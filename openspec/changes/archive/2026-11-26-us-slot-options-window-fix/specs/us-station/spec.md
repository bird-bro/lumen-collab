## MODIFIED Requirements

### Requirement: Booking Slot Options Read

The US registration service SHALL expose `GET /api/v1/us/orders/slot-options?date&modality`
gated to `us:order:create` ∪ `setting:slot:list`: the modality's live slot config rendered
as the date's slot grid — the CREATE path's semantics mirrored exactly. The create floors
booking times onto the midnight granularity grid (`(minutesOfDay / g) * g`) and validates
windows LAZILY in order (a booking is accepted by the first containing VALID window; a
malformed window refuses any booking that reaches it). The read therefore SHALL enumerate
the valid windows in order and DEAD-END at the first malformed one (unparseable under the
create's strict trimmed `HH:mm` contract, or `end <= start`): that window and every later
window is unreachable for acceptance and never advertised, while the valid windows before
it stay offered. A slot is offered only when its grid point `t` satisfies `start <= t <
end` AND its start is strictly after the DB-server clock (the create's future-only guard
mirrored — a past date offers nothing). A slot's occupancy window is `[t, t+g)` — the
same cell the create floors into — with LIVE occupancy per slot (the create path's count
predicate: live NOT-CANCELLED orders whose booking_time falls in the window —
未取消即占位, the us-prereg-as-registered anchor; the first-item modality token scopes the
count) and the config's per-slot capacity; a cell whose granularity tail crosses midnight
(granularities that do not divide 1440) counts the tail on the next day. The enumeration
SHALL terminate unconditionally for any stored granularity value (day-minute arithmetic,
no `LocalTime` wrap-around). An unconfigured modality SHALL answer `configured=false`
with no slots (200, fail-soft — the create path stays the fail-closed half); an invalid
date SHALL refuse A0400. The read SHALL NOT take the config-row lock (the create path's
FOR UPDATE serialization stays create-side); the day's occupancy loads in ONE span query
(extended to cover cross-midnight tails; the per-slot N+1 retires).

#### Scenario: The configured grid carries live occupancy

- **WHEN** the slot config defines a 08:00–12:00 window at 30-minute granularity with
  capacity 2 and one BOOKED order sits at 09:17 tomorrow
- **THEN** the endpoint answers 8 slots for tomorrow's date
- **AND** the 09:00–09:30 slot reports booked=1, capacity=2
- **AND** untouched slots report booked=0

#### Scenario: Unconfigured modality fails soft

- **WHEN** the modality has no live slot config
- **THEN** the endpoint answers `configured=false` with an empty slot list (200)

#### Scenario: Invalid date refuses

- **WHEN** the date parameter is not a `yyyy-MM-dd` calendar date
- **THEN** the endpoint refuses with A0400

#### Scenario: Wrap-around granularity cannot pin the enumeration

- **WHEN** the slot config stores granularityMinutes=1440 (or 720 with an 08:00–20:00
  window) — values the config surface stores verbatim
- **THEN** the endpoint returns within normal latency with a finite slot list (an
  off-grid window yields zero slots; no request hangs, no unbounded query loop)

#### Scenario: Off-grid windows offer exactly the bookable grid points

- **WHEN** the slot config defines an 08:00–12:00 window at 45-minute granularity
- **THEN** the offered slot starts are the midnight-grid points within the window
  (08:15, 09:00, 09:45, 10:30, 11:15) — not the raw window start
- **AND** a pre-registration booked at an offered slot start counts into that slot's
  own `[t, t+g)` cell (the picker's count matches the create's acceptance window)

#### Scenario: Malformed windows dead-end the enumeration

- **WHEN** the stored window list carries a VALID window followed by a malformed one
  (unparseable under the strict `HH:mm` contract, e.g. the seconds form `08:00:00`, or
  `end <= start`)
- **THEN** the read offers the valid window's grid points and nothing from the malformed
  window or any later one (a booking reaching them refuses 开台时间窗配置无效)
- **WHEN** the malformed window comes FIRST
- **THEN** the read answers `configured=true` with an empty slot list — nothing is
  bookable, nothing is advertised, and the endpoint does not refuse

#### Scenario: A past date offers nothing

- **WHEN** the date parameter names a past calendar day (a live config exists)
- **THEN** the read answers a finite slot list in which no cell starts at or before the
  DB-server clock — the create's 预登记时间不能早于当前时间 guard mirrored

#### Scenario: Cross-midnight cells count their tails

- **WHEN** granularityMinutes=50 with a 00:00–23:59 window and three live NOT-CANCELLED
  bookings sit on the day AFTER the queried date between 00:02 and 00:08
- **THEN** the queried date's 23:20 slot — whose cell `[23:20, 00:10)` crosses midnight —
  reports booked=3 (the tail counts exactly as the create's occupancy window does)
