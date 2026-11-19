# Design

## Context

See proposal.md — Why. Current state verified 2026-11-19:

- The five box columns were created by `V27__org_device_room.sql` (applied
  history; the `target/classes` copy is build output, never edited).
- `OrgDeviceServiceImpl.update()` is the D9 NULL-aware full replace: an explicit
  `LambdaUpdateWrapper` writes every editable column, box fields included — so
  since the web form stopped sending them, every edit-save already nulls the
  columns. `create()` uses `BeanUtil.copyProperties(request, entity, true)`.
- Contract mentions: `openspec/specs/api/spec.md` line ~729 names the box params
  inside the Device Create Validation requirement (the ONLY spec mention —
  `data/spec.md` documents no device columns).
- Web consumers: none render box fields. Only `src/api/types.ts` (`OrgDevice`,
  `DeviceCreateBody`) and the test fixtures that satisfy the required-nullable
  type (`device-page.test.tsx`, `room-page.test.tsx`).

## Goals / Non-Goals

Goals: the device model (contract, backend, schema) carries no box fields; the
five columns leave `system.org_device` via a forward-only migration.

Non-Goals: touching `ae/ip/port` or the capture/prune family (they stay — the
user removed only the Box params); any US-workstation capture design (v2 — if
the Box concept returns, it comes back through a new proposal); reworking the
web drawer further (already done as a direct UI change).

## Decisions

- **D1 — Forward-only column drop (V48), never rewrite V27.** New migration
  `V48__org_device_drop_box.sql` in `lumen-system` executes five
  `ALTER TABLE system.org_device DROP COLUMN IF EXISTS ...` statements.
  Alternative (keeping nullable columns, dead in contract) rejected: it leaves
  the misleading receive→null pipeline in place, which is the problem the user
  is clearing. `IF EXISTS` keeps the migration idempotent-friendly for the
  delete-history-row-and-rerun recovery path (memory ruling: idempotent data
  migrations re-run cleanly).
- **D2 — Full removal, not deprecation.** The fields leave entity, both DTOs,
  the update `.set(...)` chain, and the contract in one change. No
  compatibility shims (HERO scope: no wrappers for materially cheaper work).
  Old clients sending box fields: none exist in production (single SPA).
- **D3 — Single change, backend-primary.** The web diff is a mechanical type
  mirror (types.ts + two fixtures, no behavior) — per the rep-temp-scope-perms
  precedent this rides ONE change scoped across stacks instead of a
  coordination doc + per-stack pair. The cross-domain ban holds: backend files
  are implemented by the backend agent, web files by the web agent.
- **D4 — Test fixtures shrink, not weaken.** `AuthDeviceApiTests` /
  `AuthDeviceRoomSeedTests` lose their box fixture lines and any box assertion;
  the web fixtures drop the five null lines. A new backend assertion pins the
  contract: a create/update body without box fields succeeds (already the
  behavior — the tests stop carrying removed fields).

## Risks / Trade-offs

- [Column drop discards existing box values permanently] → Accepted by the user
  (params ruled unneeded; values were already nulled by every full-replace
  edit). Rollback would require restoring columns + re-entering data — nothing
  to restore from, by design.
- [Old deployed web bundles POST box fields during a rolling upgrade] → Backend
  DTOs without the fields simply ignore unknown JSON properties (Jackson
  default); the columns are gone so the values land nowhere. Harmless.
- [Flyway checksum/ordering collision on shared test DB] → Version V48 checked
  against the ledger before apply (memory: duplicate-version = stale target
  copy; new numbers verified by ordinal scan — in-flight rep-temp-normal-range-sev claims V47; this change yields to it).

## Migration Plan

1. Backend code + V48 migration merged; service restart runs Flyway, columns
   drop.
2. Web type mirror deploys in the same train (no behavioral coupling — the page
   already stopped sending the fields).
3. Rollback strategy: none for the columns (forward-only). Code revert alone is
   safe pre-migration; post-migration, a revert of entity/DTO against dropped
   columns breaks — treat V48 as the point of no return (standard for drops).

## Open Questions

(none)
