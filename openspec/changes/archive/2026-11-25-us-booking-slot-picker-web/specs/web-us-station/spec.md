## ADDED Requirements

### Requirement: Booking Slot Dialog

The 预约时间 cell SHALL collapse the inline date picker + slot select into ONE
trigger (calendar icon + summary text, `aria-label="选择预约时段"`) that opens the
选择预约时段 dialog (`role="dialog"`; the UI authority is
docs/prototypes/us-booking-slot-dialog.html). The dialog SHALL open on TOMORROW with
the mini month calendar ALWAYS VISIBLE (no popover; the 2026-11-24 mid-apply ruling)
and bound the booking date domain to tomorrow onward (D+1): today and earlier SHALL
NOT be selectable (today = register in place = walk-in), and the calendar SHALL gray
today with the 即到即检 note. Each selectable day SHALL carry a day-level remaining
badge (余 N; 满 when the day's configured grid is fully booked) aggregated
CLIENT-SIDE over the existing per-date slot-options read (parallel prefetch of the
displayed month's future days; unconfigured or failed days simply carry no badge - a
dedicated day-level range endpoint stays a future optimization). The slot grid SHALL
group
by 上午/下午 and render each slot as a card with the `HH:mm–HH:mm` range, a
segmented capacity meter (1 cell = 1 号; capacity > 10 degrades to a progress bar)
and the remaining count; a full slot (booked = capacity) SHALL render grayed and
disabled with 已满, and a slot with exactly 1 remaining SHALL carry the tight
(仅剩 1 号) emphasis. A summary line SHALL total the day's 剩余/总号. 确定 SHALL
write the date+slot pair (bookingTime = `<date> <START>:00`, date ≥ tomorrow);
clearing a picked booking via the trigger's 清除预约时段 affordance (or closing
without confirming a selection) SHALL submit a null bookingTime (walk-in unchanged).
An unconfigured modality (`configured=false`) or a failed lookup SHALL disable the
trigger (未开通预约) without blocking walk-ins. The submit value SHALL remain the
slot START `HH:mm` (the bookingTime composition is unchanged).

#### Scenario: The dialog opens on tomorrow with the grouped grid

- **WHEN** the operator clicks the 预约 trigger
- **THEN** the dialog opens with the calendar visible (tomorrow highlighted, today
  grayed/disabled) and the configured slots grouped 上午/下午, each card showing its
  range, capacity meter and remaining count
- **AND** the calendar's selectable days carry the day-level remaining badges for
  the displayed month

#### Scenario: The full slot is grayed and the tight slot emphasized

- **WHEN** the slot-options read answers a grid where one slot's booked equals its
  capacity and another has exactly 1 remaining
- **THEN** the full slot renders grayed/disabled with 已满 and cannot be picked
- **AND** the 1-remaining slot renders the 仅剩 1 号 tight emphasis

#### Scenario: Confirming a slot writes the date+slot pair

- **WHEN** the operator picks a slot and confirms
- **THEN** the trigger summary shows the picked 周X MM-DD HH:mm–HH:mm
- **AND** submitting composes bookingTime as `<picked-date> <START>:00` with the
  picked date ≥ tomorrow

#### Scenario: Blank or cleared keeps the walk-in path

- **WHEN** the operator closes without confirming a selection, or clears a picked
  booking via the trigger's 清除预约时段 affordance
- **THEN** submitting composes a null bookingTime and the order registers in place

#### Scenario: Unconfigured or failed lookup degrades without blocking

- **WHEN** the slot-options fetch fails or answers configured=false
- **THEN** the trigger renders disabled (未开通预约) and the walk-in path still works
