# patient-domain Delta

## MODIFIED Requirements

### Requirement: Identity Resolution Chain

The resolve endpoint SHALL evaluate the chain in order: exact `id_no` match on a live
archive; then exact card-number match; then soft candidates (same name + phone, or same
name + gender + birth date); and SHALL return the first decisive hit. When only soft
candidates match, the response SHALL return the candidate list (not an error). When
nothing matches, the endpoint SHALL create a new archive - PROVISIONAL or RESOLVED per
the request flag - and return it. The COMPOSITE (resolve-or-register) SHALL additionally
enrich a decisively matched archive with the request's demographics under the
MINIMAL-MERGE rule: a master field fills ONLY when the archive's stored value is NULL
(`name`/`gender`/`id_type`/`id_no`/`phone`/`birth_date` — never an overwrite of an
existing value), and a request card that is not already the archive's identifier row
appends a NEW card identifier (archives accumulate cards per registration). The plain
lookup SHALL stay side-effect-free (the recorded no-lookup-side-effect stance — the
enrichment is a create-time fact). The `id_no` fill needs no separate guard: the chain's
own ordering makes the id_no reaching the enrichment unclaimed (a claimed `id_no`
resolves to its owner decisively before any card walk).

#### Scenario: Exact id_no hit resolves immediately

- **WHEN** resolve is called with an `id_no` that matches a live archive
- **THEN** the response returns that archive and no new row is created

#### Scenario: Soft match returns candidates

- **WHEN** resolve finds no exact identity or card match but two archives share the
  requested name and phone
- **THEN** the response returns both as candidates for the caller to pick from

#### Scenario: No match creates a new archive

- **WHEN** resolve finds no exact or soft match and the request asks for a formal
  archive
- **THEN** a RESOLVED archive is created with an issued image number and returned

#### Scenario: The composite match enriches the archive's null demographics

<!-- added by patient-resolve-demographics-enrichment: the user ruling 2026-11-26
     (card 00114's second registration typed phone etc. - the archive stayed sparse) -->

- **WHEN** the composite matches a live archive whose demographics are partially null
  and the request carries values for them
- **THEN** ONLY the null master fields fill from the request (name/gender/id_type/
  id_no/phone/birth_date) and a request card absent from the archive's identifiers
  appends as a new card row — existing values never overwrite, the response returns
  the ENRICHED identity
- **WHEN** the same demographics arrive again (nothing left null, the card already
  an identifier)
- **THEN** the archive is unchanged (idempotent — no duplicate identifier rows, no
  rewrites)
