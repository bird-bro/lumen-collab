# patient-domain Specification

## Purpose

Owns the patient master index domain: one-person-one-archive master records, multi-number
identity binding, the identity resolution chain, the PROVISIONAL/RESOLVED archive state
machine with minimal merge, image-number issuance rules, and the changed/merged event
outbox contract that department workflows will consume.

<!-- synced from: patient-domain 2026-11-18 -->

## Requirements

### Requirement: Patient Master Archive

The `patient` schema SHALL keep one master archive per person (`patient_master`) carrying
demographics (name, server-side generated pinyin, gender, birth date, phone, nation,
address), an `archive_status` of PROVISIONAL or RESOLVED, and a `merged_into` pointer for
merged rows. Creating an archive SHALL issue the image number (`pat_id`) from the active
number rule inside the creation transaction, and the archive SHALL carry the issuer
organization id. Archives SHALL NOT be hard-deleted; merged rows stay traceable.

#### Scenario: Creation issues image number and pinyin

- **WHEN** a client posts a create-archive request with name and demographics
- **THEN** the archive row stores the server-generated pinyin for the name and a `pat_id`
  issued from the active rule, and the response returns the archive with its id and
  image number

#### Scenario: Merged archive stays traceable

- **WHEN** an archive has been merged into a survivor
- **THEN** the row still exists with `merged_into` set to the survivor id and remains
  retrievable by id

### Requirement: Conditional Unique Identity

The natural identity key SHALL be `(id_type, id_no)` with a partial unique index over
live archives only (`id_no IS NOT NULL AND merged_into IS NULL`). Creating or updating an
archive whose `id_no` duplicates a live archive SHALL be rejected with an explicit
conflict error naming the duplicate. Additional numbers (visit card, physical exam,
outpatient, inpatient, HIS id) SHALL live in `patient_identifier` rows bound to the
archive, with no uniqueness enforced on the visit card number.

#### Scenario: Duplicate id_no is rejected

- **WHEN** a create or update request carries an `id_no` that already exists on a live
  archive
- **THEN** the request is rejected with a conflict error identifying the duplicate, and
  no archive row is written

#### Scenario: Card numbers are not unique

- **WHEN** two archives share the same visit card number
- **THEN** both identifier rows persist - the card number is a soft resolution key, not
  a unique constraint

#### Scenario: Merged rows do not block re-issuance

- **WHEN** an archive's `id_no` matches only a merged (non-live) archive
- **THEN** the uniqueness check passes and the new archive is created

### Requirement: Identity Resolution Chain

The resolve endpoint SHALL evaluate the chain in order: exact `id_no` match on a live
archive; then exact card-number match; then soft candidates (same name + phone, or same
name + gender + birth date); and SHALL return the first decisive hit. When only soft
candidates match, the response SHALL return the candidate list (not an error). When
nothing matches, the endpoint SHALL create a new archive - PROVISIONAL or RESOLVED per
the request flag - and return it.

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

### Requirement: Provisional and Merge State Machine

`archive_status` SHALL move one-way PROVISIONAL -> RESOLVED. A PROVISIONAL placeholder
(unnamed emergency patient) SHALL still receive an image number at creation. The
resolve-or-merge endpoint SHALL perform the minimal merge: the merged row points
`merged_into` at the survivor, the survivor's archive stays RESOLVED, a merge log row
records both ids with operator and reason, and a PATIENT_MERGED outbox row is written.
Merging an already-merged archive SHALL return the existing survivor (idempotent).
Default archive search SHALL exclude merged rows.

#### Scenario: Emergency placeholder receives an image number

- **WHEN** a PROVISIONAL archive is created with no name and no id_no
- **THEN** the row persists with an issued `pat_id` and archive_status PROVISIONAL

#### Scenario: Minimal merge writes pointer and log

- **WHEN** resolve-or-merge merges a PROVISIONAL placeholder into an existing archive
- **THEN** the placeholder row points at the survivor, the merge log records both ids,
  and a PATIENT_MERGED outbox row exists for the pair

#### Scenario: Merge is idempotent

- **WHEN** resolve-or-merge is called for a placeholder already merged into a survivor
- **THEN** the response returns the same survivor and no duplicate log or outbox row is
  written

#### Scenario: Default search hides merged rows

- **WHEN** the archive list or resolve chain queries archives
- **THEN** merged rows are excluded unless explicitly requested by id

### Requirement: Image Number Issuance

The patient service SHALL obtain the 影像号 (`pat_id`) from the PLATFORM number-rule
service through the `NumberApi` Feign contract (the row-lock rule machinery is
platform-owned — the burned-serial increment commits platform-side, and a patient-side
rollback after issuance burns the number):

#### Scenario: Concurrent issuance does not duplicate

- **WHEN** two archive creations race for a pat_id
- **THEN** the platform issuer serializes them — both succeed with distinct numbers

#### Scenario: Stamp rule resets by day

- **WHEN** the pat_id family's day token rolls over
- **THEN** the platform issuer restarts the serial at 1

#### Scenario: Rolled-back archive creation burns the number

- **WHEN** an archive creation issues a pat_id and then fails
- **THEN** the platform-side increment has committed — the number is burned and never
  reissued

<!-- synced from: number-rule-unification-sev 2026-11-19 (issuance delegates to the platform NumberApi; the row-lock mechanism moved to the system schema) -->

### Requirement: Patient Change Event Outbox

Archive events SHALL be written to `patient_event_outbox` rows: PATIENT_CHANGED on any
archive correction (with a jsonb payload of the changed archive), PATIENT_MERGED on
merge (payload naming survivor and merged ids). Outbox rows SHALL be append-only and
start in PENDING status; v1 SHALL NOT publish to any message broker and SHALL NOT
delete outbox rows.

#### Scenario: Correction writes a change event

- **WHEN** an archive's demographics are updated
- **THEN** a PATIENT_CHANGED outbox row exists carrying the archive id and payload

#### Scenario: Merge writes a merged event

- **WHEN** the minimal merge completes
- **THEN** a PATIENT_MERGED outbox row names the survivor and merged archive ids

#### Scenario: No broker is required

- **WHEN** the service runs with no message broker deployed
- **THEN** outbox rows accumulate in PENDING status and all archive flows succeed

<!-- REMOVED by number-rule-unification-sev 2026-11-19 (user ruling 2026-11-18): the patient-side rule CRUD surface (patient:rule:* perms, /api/v1/patient/** routes) retired - rule administration lives in the number-rule capability (system side, system:number:*), console page migrated by number-rule-unification-web. -->

### Requirement: Patient Service Surface

The patient domain SHALL be served by its own executable module with URL prefix
`/api/v1/patient/**`, its own `patient` schema and Flyway ledger, and the `patient:*`
permission family. The module SHALL declare its reserved port per the coordination port
table and SHALL carry no message-broker dependency.

#### Scenario: Endpoints are reachable under the unified entry

- **WHEN** a client calls any patient-domain endpoint
- **THEN** the path is `/api/v1/patient/**` behind the unified nginx entry and the
  tables resolve in the `patient` schema through the module's own ledger

#### Scenario: Service identity is declared

- **WHEN** the application context loads
- **THEN** `spring.application.name` is the module name and the port matches the
  coordination port table's patient row

<!-- synced from: patient-identity-card-projection 2026-11-26 -->
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

