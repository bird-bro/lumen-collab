# Proposal: patient-resolve-demographics-enrichment

## Why

The composite resolve-or-register, when the chain MATCHES an existing archive, returns
the identity WITHOUT any update — the desk's typed demographics (证件号/出生日期/手机号)
ride the ORDER's snapshot only, so sparse archives (created from a minimal first
registration: name + gender + the resolve card) stay sparse forever, and every later
resolve keeps backfilling nulls (observed 2026-11-26: card 00114's second registration
typed phone etc., the archive 2244 still reads all-null). The user ruling: the desk's
information SHOULD update the patient info.

## What Changes

- The composite's MATCHED branch (resolve-or-register ONLY — the lookup stays
  side-effect-free, the recorded no-side-effect stance) enriches the matched archive
  with the request's demographics under the MINIMAL-MERGE rule: a field fills ONLY
  when the archive's value is NULL (name/gender/id_type/id_no/phone/birth_date —
  never an overwrite); a request card that is NOT already the archive's identifier
  row appends a NEW card identifier (the archive accumulates cards per registration,
  the existing semantics); nothing else changes.
- The id_no fill needs NO extra guard: the chain's own ordering guarantees it — a
  request `id_no` claimed by another archive resolves to THAT archive decisively
  (exact identity first), so an id_no reaching the enrichment is unclaimed.
- The projection (patient-identity-card-projection, archived) reads the enriched
  master — the NEXT resolve backfills everything the desk ever typed.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `patient-domain`: the Identity Resolution Chain's composite match branch enriches
  the matched archive's null demographics (minimal-merge, card append).
