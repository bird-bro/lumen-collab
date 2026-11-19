# Spec Delta

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Number Rule Management API

Number rules SHALL be manageable through the `/api/v1/patient/**` surface under the
`patient:rule:*` permission family: list, create, update, and delete. Exactly one rule
SHALL be active per rule type (`pat_id` in v1); archive creation SHALL resolve the
active rule and ignore inactive rows. Rule parameters (prefix, suffix, serial length,
timestamp stamp) SHALL be data - changing the number format SHALL NOT require a code
change.

#### Scenario: Rule CRUD enforces perms

- **WHEN** a user without `patient:rule:create` posts a rule create
- **THEN** the request is rejected by the permission layer and no rule row is written

#### Scenario: Creation resolves the active rule

- **WHEN** two rules exist for `pat_id` with one active
- **THEN** archive creation issues numbers from the active rule only

<!-- REMOVED by number-rule-unification-sev (user ruling 2026-11-18): the patient-side
rule CRUD surface retires — rule administration moves to the platform's system-side
directory (the `number-rule` capability's Number Rule Directory requirement, gated
`system:number:*`, console page migrated by `number-rule-unification-web`). The
in-flight `patient-number-rules` → `number-rules` rename is superseded before landing.
"Creation resolves the active rule" survives platform-side via NumberApi delegation. -->
