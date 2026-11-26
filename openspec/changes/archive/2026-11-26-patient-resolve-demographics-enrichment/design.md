# Design: patient-resolve-demographics-enrichment

## D1 — Where: the composite's matched branch, before the identity projection

`PatientArchiveServiceImpl`'s resolve-or-register composition: when the chain returns
a decisive hit (exact identity / card) and the request carries a formal match, run the
enrichment INSIDE the same transaction, THEN project. The plain lookup path
(`lookup`) never enriches (the no-side-effect read). The soft-candidates branch never
enriches (no decisive hit — nothing to enrich yet).

## D2 — The minimal-merge rule

Field-by-field: `request.field != null && master.field == null → master.field =
request.field` for name/gender/id_type/id_no/phone/birth_date. No diffing, no
overwrites, no audit-side special casing beyond the MetaObjectConfig fill (update_by/
update_time stamp the touch — the enrichment IS an update). The id_no's conflict
safety rides the chain's ordering (a claimed id_no resolves to its owner first — the
enrichment never sees a claimed one; recorded reasoning, no extra guard).

## D3 — The card append

`request.cardNo != null` and no live identifier row of the archive carries that exact
`id_no` (type 'card') → `insertIdentifier(master.getId(), 'card', cardNo)` (the
existing private helper — the resolve-created path's persistence semantics). Already
present → no row (idempotent). Multiple DIFFERENT cards over time accumulate (the
archive's established card-accumulation semantics).

## D4 — The response returns the ENRICHED identity

The projection runs AFTER the enrichment (the same master instance, refreshed fields)
— the create response's identity echoes what the archive now holds, so the desk sees
its own typed values reflected in the matched archive (and the NEXT resolve
backfills them).
