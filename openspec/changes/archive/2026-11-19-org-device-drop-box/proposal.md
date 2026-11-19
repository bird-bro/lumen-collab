# Proposal

## Why

The device form no longer edits the Box (采集盒) parameters, and the backend's D9
NULL-aware full-replace update writes `NULL` into all five box columns on every
save — the receive→overwrite pipeline for data nobody sends is dead code that
misleads readers. Clear the fields end-to-end (entity, DTO, service, DB schema,
API contract, web type mirror) so the device model matches what the product
actually uses. User ruling 2026-11-19 (device drawer rework session).

## What Changes

- **BREAKING** Device create/update request bodies and device detail responses
  drop `boxAdapter` / `boxIp` / `boxPort` / `boxMac` / `boxPrint` — the fields
  disappear from the contract, not just from clients.
- Backend: remove the five box fields from `OrgDevice` entity,
  `DeviceCreateRequest` / `DeviceUpdateRequest` DTOs, and the explicit `.set(...)`
  chain in `OrgDeviceServiceImpl.update()` (create's `BeanUtil.copyProperties`
  narrows automatically with the DTO).
- DB: new Flyway migration `V48__org_device_drop_box.sql` drops the five columns
  from `system.org_device` (V27 created them; V27 is applied history and stays
  untouched per the never-rewrite rule).
- Web: remove the mirrored fields from `OrgDevice` / `DeviceCreateBody` in
  `src/api/types.ts` and from the test fixtures that satisfy the type
  (`device-page.test.tsx`, `room-page.test.tsx`). The device page form already
  dropped the fields (direct UI change, 2026-11-19) — no page behavior change
  here.
- Data note: dropping the columns discards any existing box values permanently.
  Accepted — the user ruled the parameters unneeded, and full-replace updates
  were already nulling them on every edit.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: the Device Create Validation requirement's optional acquisition block
  narrows from `boxAdapter/boxIp/boxPort/boxMac/boxPrint/captureW/...` to
  `captureW/captureH/prune/pruneU/pruneD/pruneL/pruneR` (box fields removed
  everywhere the device body contract names them).

## Impact

- `lumen-sev/lumen-system`: entity / two DTOs / `OrgDeviceServiceImpl` / new
  migration `V48` / test fixtures `AuthDeviceApiTests`, `AuthDeviceRoomSeedTests`.
- `lumen-web`: `src/api/types.ts` + two test fixtures (type-satisfaction only).
- `openspec/specs/api/spec.md`: one requirement's field list (delta spec).
- Risk: low — no functional consumer of box fields exists (verified: room page
  source and all pages render none; only type-level references). The OPS-lineage
  Box capture-box concept returns via a future proposal if the v2 US workstation
  needs it.
