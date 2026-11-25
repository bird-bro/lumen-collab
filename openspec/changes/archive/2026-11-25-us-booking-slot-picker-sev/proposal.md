# Proposal: us-booking-slot-picker-sev

> Cross-stack parent: `openspec/coordination/us-booking-slot-picker.md` (the shared
> design - endpoint shape, grid rules, gate - lives THERE).

## Why
The booking picker (the 2026-11-24 slot-dialog form; originally an inline Select -
the read contract is form-agnostic) needs the configured slot grid + live
occupancy; the admin slot-config CRUD is permission-gated away from the booking
operator and carries no occupancy.

## What Changes
- New read endpoint `GET /api/v1/us/orders/slot-options?date&modality` (gated
  `us:order:create` ∪ `setting:slot:list`): the modality's live config rendered as
  the date's granularity grid with the create path's occupancy predicate, plus the
  `configured` flag (fail-soft) and a strict A0400 on bad dates.
- `SlotOptionsView` (+ nested `SlotOption`), `RegistrationOrderService.slotOptions`,
  the impl grid enumeration (windows floor from their start; unparseable windows skip).

## Affected Stacks
- single-stack: lumen-sev (frontend change: `us-booking-slot-picker-web`)

## Depends-On
- coordination parent `us-booking-slot-picker`; builds on the landed kernel
  pre-registration capacity machinery (exam_slot_config + ExamSlotCapacityService)

## Scope
- In scope: `lumen-sev/lumen-us` DTO + service method + controller endpoint +
  `UsRegistrationApiTests.slotOptionsExposeTheConfiguredGridWithLiveOccupancy`.
- Out of scope: past-slot graying (DB-clock based - later enhancement), the admin
  CRUD surface, write paths (the create path's FOR UPDATE dance is untouched).

## Success Criteria
- A configured modality answers the 8-slot grid (08:00–12:00 @ 30min) with the live
  BOOKED count on the occupied slot and the config capacity.
- An unconfigured modality answers `configured=false` (200, no slots).
- The create path's capacity behavior is byte-identical (read-only addition).

## Constraints
- Occupancy predicate mirrors the create path verbatim (BOOKED + window + modality token).
- No config-row lock on the read (stale reads only cost a tint, never correctness).

## Risks
- N+1 count per slot (15ish slots/day) — negligible at desk scale; a GROUP BY could
  replace it if the desk grows.
