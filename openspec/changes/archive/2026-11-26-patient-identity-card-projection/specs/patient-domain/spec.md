# patient-domain Delta

## MODIFIED Requirements

### Requirement: Internal Service Surface

The patient domain SHALL expose an internal, internal-key-guarded service surface (the system
`/issue` precedent — NEVER behind user perms) for consuming services: a composite
`resolve-or-register` endpoint that atomically runs the existing resolution chain and falls back to
archive creation (placeholder archives included, `A0715` conflict semantics unchanged), and
outbox `pull`/`ack` endpoints that hand out PENDING event entries in batches and mark them consumed.
The `lumen-feign` module SHALL declare the matching `PatientApi` Feign contract (add-only, `NumberApi`
precedent, direct-url target `lumen.feign.patient.url`). The identity projection SHALL carry the
archive's visit card as the OPTIONAL `cardNo` (the LATEST live `patient_identifier` row with
`id_type 'card'`, highest id — archives accumulate cards per registration; an archive with no
card identifier projects null, never a fabricated number).

#### Scenario: Resolve-or-register resolves an exact match

- **WHEN** the composite endpoint receives demographics whose `id_no` exactly matches a live archive
- **THEN** the existing archive identity returns without a new row

#### Scenario: Resolve-or-register registers a new archive

- **WHEN** the resolution chain finds no acceptable match
- **THEN** a new archive is created (PROVISIONAL placeholders included, `pat_id` issued) and its
  identity returns; a conflicting `id_no` surfaces `A0715` unchanged

#### Scenario: Outbox pull and ack hand out events exactly once

- **WHEN** a consumer pulls a PENDING batch and later acks an entry
- **THEN** pulled entries stop appearing in subsequent pulls only after their ack, and a failed
  dispatch that skips the ack leaves the entry PENDING

#### Scenario: Internal-key guard refuses foreign callers

- **WHEN** a caller hits any internal endpoint without the shared internal key
- **THEN** the request is refused before reaching the service layer

#### Scenario: The identity projects the archive's latest visit card

<!-- added by patient-identity-card-projection: the desk's 就诊卡号 backfill -->

- **WHEN** the identity projection runs for an archive that holds live card identifiers
- **THEN** the projected `cardNo` carries the LATEST card identifier's `id_no` (highest
  identifier id — the current visit card)
- **WHEN** the archive holds no live card identifier
- **THEN** `cardNo` projects null (the consuming desk backfills an empty 卡号, never a
  fabricated number)
