# Coordination: us-booking-slot-picker

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "us-booking-slot-picker"`.

## Why
The workbench's 预约时间 cell is two raw inputs (date + free-text slot). The
interaction is specified by the workbench prototype (us-registration-redesign.html)
plus - superseding its original inline Select form per the 2026-11-24 redesign
ruling - the slot DIALOG prototype (docs/prototypes/us-booking-slot-dialog.html):
a trigger + dialog that lays out the CONFIGURED slots (预约设置's open windows ×
granularity) with per-slot remaining counts and GRAYS the full ones, with the
create path's A0400 refusals as the other half of the 双保险. Two hard gaps block
it: `GET /slot-configs` is `setting:slot:list`-gated (the booking operator cannot
read it), and no endpoint exposes per-slot occupancy.

## Shared Design
- New read endpoint: `GET /api/v1/us/orders/slot-options?date=yyyy-MM-dd&modality=<token>`
  - Gate: `hasAnyAuthority('us:order:create', 'setting:slot:list')` — the booking
    actors plus the slot-config administrators.
  - Response: `{ configured: boolean, slots: [{ start 'HH:mm', end 'HH:mm', booked, capacity }] }`.
  - Grid: each open window floors onto the granularity grid from the window start
    (08:00–12:00 @ 30min → 8 slots); occupancy = the CREATE path's count predicate
    verbatim (status BOOKED + booking_time in the window + `exam_items->0->>'modality'`).
  - Read-only (no FOR UPDATE — the serialization point stays create-side); unparseable
    windows skip; unconfigured modality answers `configured=false` fail-SOFT (the
    create path stays the fail-closed half). Bad date → A0400.
- Frontend (redesigned 2026-11-24): the 预约时间 cell collapses into ONE trigger
  (calendar icon + summary text, `aria-label` 选择预约时段) that opens the
  选择预约时段 dialog (docs/prototypes/us-booking-slot-dialog.html): date navigation
  (‹ › + mini month calendar) with the booking date domain = 明天起 (D+1 - today =
  register in place = walk-in, the calendar grays today), the slot grid grouped
  上午/下午 with per-slot capacity meter (1 cell = 1 号) + remaining count, full
  slots grayed disabled (已满), the last-remaining slot emphasized (仅剩 1 号), and
  a day summary line (剩余/总号). 确定 writes the date+slot pair (the bookingTime
  composition is unchanged); the trigger's ×/blank = walk-in (即到即检); no config → the
  trigger disables (未开通预约). The submit value stays the slot START 'HH:mm'.

## Decisions
- purpose-built endpoint over relaxing the admin CRUD gate - the picker needs
  occupancy, not the config row, and keeps setting:slot:list admin-only - 2026-11-24
- trigger + dialog over the inline DatePicker+Select pair - the dropdown cannot
  show remaining counts and the pair crowds the grid cell (user ruling 2026-11-24,
  prototype docs/prototypes/us-booking-slot-dialog.html; the working-tree Select
  implementation is reworked, not archived) - 2026-11-24
- booking date domain = tomorrow (D+1): the dialog opens on tomorrow and today is
  NOT selectable - today's exams register in place (walk-in), no date picking
  (user ruling 2026-11-24); the D+1 floor is a frontend constraint, a backend-side
  rule would be a separate proposal - 2026-11-24
- per-day remaining badges on the dialog's calendar need a day-level aggregate
  endpoint (the contract queries date-by-date) - out of scope - 2026-11-24
- past-slot graying (today's earlier windows) stays out of scope - the backend's
  预登记时间不能早于当前时间 refusal already intercepts; DB-clock graying is a
  later enhancement (in effect superseded by the D+1 floor - the dialog has no
  reachable past days) - 2026-11-24

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | us-booking-slot-picker-sev | implemented (US suites 25/25) | pending |
| frontend | us-booking-slot-picker-web | implemented (the 2026-11-24 dialog form; US page suites 24/24) | pending |

## Gate
Verify each stack independently. When ALL registered changes have an acceptable verify credential
(`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and batch-archive them.
