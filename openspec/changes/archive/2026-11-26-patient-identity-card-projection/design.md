# Design: patient-identity-card-projection

## D1 — The projection source: the latest live card identifier

`toIdentity(PatientMaster)` currently reads the master only. The card lives in
`patient_identifier` (`idType 'card'` — the resolve chain's own constant
`IDENTIFIER_TYPE_CARD`). The controller injects `PatientIdentifierMapper` and the
projection reads ONE query: live rows of the master with `id_type = 'card'` ordered by
`id desc`, taking the first — the LATEST registered card is the current one (an archive
accumulates cards per registration; the resolve-created path already persists each
card). No identifier → null (never fabricated). The lookup-by-card match naturally
re-projects the searched card when it is the latest one.

## D2 — Wire: additive optional field, the chain is shape-transparent

`PatientIdentityResponse.cardNo` (@JsonInclude NON_NULL would be nicer but the DTO
family keeps the plain shape — null rides, the web's `?? null` absorbs it). The us
resolve passthrough forwards the Feign `PatientLookupResult` verbatim (zero code); the
web's `UsPatientIdentity.cardNo` + the backfill mapping already exist
(us-orders.ts:180, the page's fillFromIdentity). The candidate modal rides the same
projection for free.

## D3 — Test seam

The patient internal API tests (PatientInternalApiTests) exercise toIdentity through
the real chain — the card fixture rides the existing resolve-or-register fixtures
(their resolve-created archives already persist card identifiers via
insertIdentifier); the no-card archive case needs one fixture WITHOUT a card.
