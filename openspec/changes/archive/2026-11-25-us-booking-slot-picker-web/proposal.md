# Proposal: us-booking-slot-picker-web

> Cross-stack parent: `openspec/coordination/us-booking-slot-picker.md` (the shared
> design - option shape, graying rule - lives THERE).
> REDESIGNED 2026-11-24 (user ruling, before apply): the inline Select form is
> superseded by the trigger + dialog form (UI authority:
> `docs/prototypes/us-booking-slot-dialog.html`); the Select implementation already
> in the working tree is REWORKED to this form, not archived as-is.

## Why
The 预约时间 cell is two raw inline inputs (DatePicker 170px + Select 128px); the
dropdown only expresses 已满 n/n - the day's slot distribution and remaining counts
are invisible, and the two controls crowd the 3-column grid cell.

## What Changes
- The cell collapses into ONE trigger (calendar icon + summary text, `aria-label`
  选择预约时段) that opens the 选择预约时段 dialog: date navigation (‹ › + mini
  month calendar) + the slot grid grouped 上午/下午; each slot card shows the
  HH:mm–HH:mm range, a segmented capacity meter (1 cell = 1 号; capacity > 10
  degrades to a progress bar) and the remaining count; full slots gray disabled
  (已满), the last-remaining slot gets the tight (仅剩 1 号) emphasis; a summary
  line totals the day's 剩余/总号.
- Booking date domain = 明天起 (D+1, user ruling 2026-11-24): the dialog opens on
  tomorrow; today/earlier are not selectable (today = register in place = walk-in,
  no date picking); the calendar grays today with the note and ‹ stops at tomorrow.
- 确定 writes the date+slot pair (bookingTime = `<date> <START>:00`); the trigger's
  × / blank = walk-in (bookingTime null, unchanged; the footer carries only
  取消/确定); unconfigured (`configured=false`)
  disables the trigger (未开通预约, fail-closed unchanged); the A0400 full-refusal
  stays the backend half of the 双保险.
- `us-orders.ts` keeps `SlotOptionsView`/`listSlotOptions` (contract unchanged);
  the submit value stays the slot START 'HH:mm'.

## Affected Stacks
- single-stack: lumen-web (backend change: `us-booking-slot-picker-sev` - untouched
  by this redesign)

## Depends-On
- coordination parent `us-booking-slot-picker`; builds against the REAL backend
  once us-booking-slot-picker-sev lands

## Scope
- In scope: `lumen-web` `src/pages/us/registration/index.tsx` (the booking-cell
  trigger + the dialog), `src/test/us-registration-page.test.tsx` (the trigger +
  dialog interaction path). The Select-era implementation in the working tree is
  reworked to this form.
- Out of scope: per-day remaining badges on the calendar (needs a day-level
  aggregate endpoint), past-slot graying (covered by the D+1 floor), slot config
  editing (the 预约设置 page), the wide-list booking-time display.

## Success Criteria
- The trigger opens the dialog on tomorrow; the grid renders the configured slots
  with per-slot remaining counts; the full slot is grayed/disabled; picking a slot
  + 确定 composes bookingTime as before (date ≥ tomorrow, START 'HH:mm').
- Clearing a picked booking via the trigger's × (or closing empty) submits null
  bookingTime (walk-in path unchanged);
  configured=false / a failed fetch disables the trigger with the 未开通预约 text.

## Constraints
- Slot options reload on date change; a failed fetch degrades to a disabled trigger
  + one error message (never blocks walk-ins).
- The D+1 floor is a FRONTEND constraint (the backend create path has no date-domain
  rule; a backend-side rule would be a separate proposal).

## Risks
- The dialog is a richer custom surface than the rc-picker interaction path it
  replaces - the tests move from the select dropdown to the dialog DOM (the aria
  anchors are specified in the delta spec).
