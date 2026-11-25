# Design: exam-catalog-device-fields

## Context

DICOM MWL maps the catalog to device-visible attributes: item `code` → Procedure
Code Value (exists), item `exam`(中文) → Scheduled Procedure Step Description
(Chinese — unusable on charset-strict devices), body `name` → Body Part Examined
(Chinese, and MWL wants the defined-term token). The three setting pages already
manage the three tables; the V53 demo seed was pending on the DP-3 gate and now
absorbs the device values.

## Goals / Non-Goals

**Goals:** device-facing Latin fields on the three catalog tables, maintained in
the three setting pages, seeded with values for the demo catalog; order flow
untouched.

**Non-Goals:** the MWL composer itself (lumen-integration, future); patient-name
transliteration (patient domain); making the device fields required; touching the
stray DR residue row.

## Decisions

- **D1 — three nullable columns, one migration (V53)**: `exam_item.device_desc`,
  `exam_body.device_token` + `device_name`, `exam_method.device_desc` — additive
  ALTERs; the item's existing `code` doubles as the Procedure Code Value (no new
  code column). The link table `exam_item_method` carries no text (the method
  dictionary owns the wording).
- **D2 — the seed rides the same V53** (ALTERs first, then the INSERT blocks +
  setval tails, the V52 idiom: explicit ids + ON CONFLICT (id) DO NOTHING). The
  apply-time discovery: the seed's TRANSITIVE CLOSURE is five tables — 23
  exam_body_group + 34 exam_body + 15 exam_method + 46 exam_item + 58
  exam_item_method links (bodies hang off groups, items off bodies, links off
  items+methods; seeding any subset dangles foreign conventions on a fresh
  install). Fresh installs get a complete device-facing catalog; the shared DB's
  rows replay as no-ops.
- **D3 — shared-DB device-value backfill**: ON CONFLICT DO NOTHING cannot update
  existing rows, so the seed's device values land on the shared DB via a targeted
  `UPDATE ... FROM (VALUES ...)` keyed by (modality, code) in the same migration —
  idempotent (re-running sets the same values), scoped to the seed's 46 codes, and
  the shared DB converges with fresh installs.
- **D4 — the Latin values are drafted here, reviewed by the user**: 46 item
  descriptions, 34 body tokens+names, 58 method descriptions. The draft rides the
  verify/DP presentation as a table; corrections are row-level edits before the
  seed embeds. Medical Latin/English terminology (e.g. US-ABD-01 → "US ABDOMEN",
  头颅 → HEAD/CRANIUM token "HEAD").
- **D5 — the MWL composer reads the catalog at send time** by the order's item id
  (id-anchored): the order snapshot NEVER copies the device text (the same
  discipline as the dict-lookup ruling) — a renamed device field flows on the next
  send.
- **D6 — statement hygiene**: the V2/V36 lesson — comment lines carry no
  semicolons.

## Risks / Trade-offs

- [Drafted Latin terminology may not match departmental convention] → the user
  reviews the draft table before the seed embeds; row-level corrections are cheap
  (the seed is one file).
- [device_token tokens must hit DICOM defined terms or devices reject them] → the
  draft uses the DICOM PS3.3 Body Part Examined defined terms (CHEST, ABDOMEN,
  HEAD, LSPINE, BREAST …); the token column is varchar(32) so non-standard values
  CAN be stored — the admin UI is the gatekeeper, no DB constraint (the same
  leniency as `code`).
- [Existing REAL rows keep NULL device fields] → acceptable: the radiology catalog
  gets its values when the department fills them (the UI ships now); the demo
  catalog is complete from day one.

## Migration Plan

1. V53 (ALTERs + seed + backfill) lands in the lumen-system ledger; the shared
   test DB converges; fresh installs carry the full device-facing catalog.
2. The three setting pages ship the inputs in the same change (frontend follows
   the backend user-verify gate per the house flow).
3. Rollback: additive columns + data; no Flyway rollback (delete by remark marker
   if ever needed).

## Open Questions

(none — the field set, the merge shape, and the body-field design were ruled by
the user 2026-11-25.)
