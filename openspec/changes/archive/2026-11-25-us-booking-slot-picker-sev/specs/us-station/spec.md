## ADDED Requirements

### Requirement: Booking Slot Options Read

The US registration service SHALL expose `GET /api/v1/us/orders/slot-options?date&modality`
gated to `us:order:create` ∪ `setting:slot:list`: the modality's live slot config rendered
as the date's granularity grid (each open window floors onto the grid from its start) with
LIVE occupancy per slot (the create path's count predicate: live NOT-CANCELLED orders
whose booking_time falls in the window — 未取消即占位, the us-prereg-as-registered
anchor; the first-item modality token scopes the count) and the config's per-slot
capacity. An unconfigured
modality SHALL answer `configured=false` with no slots (200, fail-soft — the create path
stays the fail-closed half); an invalid date SHALL refuse A0400. The read SHALL NOT take
the config-row lock (the create path's FOR UPDATE serialization stays create-side).

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
