# Proposal: us-rail-quick-search

## Why

The rail quick-search landed as a user-directed follow-up slice (commits `9ca86f4` /
`e737153`, user ruling 2026-11-26) AFTER `registration-order-status-dict` archived —
the `keyword` query condition and the rail-search box are implemented and tested but
the synced specs do not carry them yet. This change RECORDS the contract: it exists to
sync the delta, not to drive new implementation.

## What Changes

- The us wide query gains the `keyword` condition (already implemented): ONE text box
  OR-matching the identity handles — patient name, accession (检查号; the v1 影像号
  rides the accession family), snapshot `patId`/`cardNo` — AND-composed with the other
  wide conditions; the status-count meter narrows with the same keyword.
- The compact rail gains the quick-search box (already implemented): full-width row
  under the chips block in the prototype's rail-chips shape, 400ms debounce.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `us-station`: Workbench Query Surface — the keyword condition joins the wide
  condition union (page query + status-count meter).
- `web-us-station`: Order List — the compact rail's quick-search box joins the
  workbench surfaces.

## Impact

- Backend: `RegistrationOrderPageQuery.keyword`, `appendWideConditions` OR-block,
  the keyword API test — all committed (`9ca86f4`).
- Frontend: `UsOrderPageQuery.keyword`, the debounced rail-search wiring, the
  prototype's two-block rail head (chips/quick-search on their own white block) —
  all committed (`9ca86f4`/`e737153`).
- The room change (`us-order-exam-room-*`) owns other hunks of the same service file;
  this change touches nothing further.
