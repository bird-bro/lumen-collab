# Spec Delta

## Purpose

The platform number-rule service owns EVERY number family (影像号 pat_id, 检查号
accession, 预约号 booking, future families) as ONE rule directory in the system schema
with ONE row-lock burned-serial issuance mechanism and ONE machine issuance endpoint —
replacing the per-domain duplicates. Consumers issue numbers through the Feign
contract; the kernel consumes the same machinery behind its `NumberIssuerPort`.

## ADDED Requirements

### Requirement: Number Rule Directory

The system service SHALL manage the platform's number-rule directory — a `number_rule`
table in the SYSTEM schema (the generic rule-family shape, one ACTIVE live rule per
type backstopped by a partial unique index) exposed as rule CRUD at
`/api/v1/system/number-rules[/{id}]`, gated by the `system:number:*` button perms, with
the current families seeded at migration time:

#### Scenario: Rule CRUD enforces system perms

- **WHEN** an administrator creates, edits, or deletes a rule through the system
  number-rule endpoints
- **THEN** each write requires its `system:number:*` button perm
- **AND** a duplicate active rule for one type is rejected

#### Scenario: Family defaults are seeded

- **WHEN** the migrations run on a fresh database
- **THEN** the `pat_id`, `accession`, and `booking` families each carry one active
  rule row with usable defaults

### Requirement: Platform Number Issuance

The system service SHALL issue numbers through a REQUIRES_NEW row-lock burned-serial
issuer (the increment commits in its OWN transaction; a caller's later rollback BURNS
the number) exposed at the machine endpoint `POST /api/v1/system/number-rules/issue`
(body `{ruleType}`):

#### Scenario: Concurrent issuance does not duplicate

- **WHEN** two issuances for the same family race
- **THEN** both succeed with distinct serials and no duplicate number

#### Scenario: Stamp rule resets by day

- **WHEN** a stamped family's day token rolls over
- **THEN** the serial restarts at 1 and the stamp token advances

#### Scenario: Burned serial survives a caller rollback

- **WHEN** a caller fails after a successful issuance
- **THEN** the increment has committed — the number is burned and never reissued

### Requirement: Internal Service Issuance Contract

The platform SHALL expose the FIRST inter-service Feign contract through the new
`lumen-api` module: `NumberApi` (`POST /api/v1/system/number-rules/issue`) with a
FAIL-FAST fallback — a fallback that fabricates numbers is forbidden. The `/issue`
endpoint SHALL require the internal-service key (`X-Internal-Key` header, gitignored
config on both sides) and SHALL reject anonymous or wrong-key calls:

#### Scenario: Issue endpoint rejects non-internal callers

- **WHEN** a request reaches `/issue` without the internal key or with a wrong key
- **THEN** it is rejected with an auth failure and no number is issued

#### Scenario: Feign failure fails fast

- **WHEN** the number-rule service is unreachable for a consumer
- **THEN** the Sentinel fallback THROWS (the caller's operation refuses)
- **AND** no fallback path ever fabricates a number
