# Spec Delta: service-topology

## MODIFIED Requirements

### Requirement: Foundation Module Set

The backend SHALL provide the v1 foundation modules: `lumen-bom`, the OPS-lineage reference
modules `framework` and `common`, and the `lumen-system` service. Department and workflow
modules (`lumen-exam-core`, `lumen-api`, `lumen-patient`, `lumen-radiology`,
`lumen-integration`, `lumen-us`, `lumen-endo`) SHALL NOT be created by this capability; the
changes that give them behavior add them. The standalone `lumen-setting` module is
REMOVED from the plan (2026-11-13 merge ruling): the exam-setting domain is hosted inside
`lumen-system` per the Hosted Domain Packages requirement, and a future re-extraction is
the only path that re-creates a `lumen-setting` module.

#### Scenario: Foundation modules resolve

- **WHEN** the aggregator build completes
- **THEN** `framework`, `common`, and `lumen-system` have each produced a jar
- **AND** `lumen-bom` participates as a `pom` module (no jar - it emits only its resolved
  flattened POM as the version source)
- **AND** the `lumen-sev/` module set contains no `lumen-setting` module

#### Scenario: Deferred module requested

- **WHEN** a later feature needs `lumen-radiology`
- **THEN** that feature's change adds the module and declares it in the aggregator
- **AND** no placeholder module exists before that change

## ADDED Requirements

### Requirement: Hosted Domain Packages

A domain MAY be hosted inside an existing service module instead of getting its own
executable service (2026-11-13 merge ruling, precedent: the exam-setting domain inside
`lumen-system`). The hosted domain SHALL keep its own package root
(`org.birdbro.lumen.<domain>`) inside the hosting module's jar, its own URL prefix, and
its own perms-string family; its tables SHALL live in the hosting service's own schema
under the ONE service Flyway ledger; and the arrangement SHALL stay re-extractable.

#### Scenario: Hosted domain discovered by the hosting service

- **WHEN** the hosting service boots
- **THEN** the hosted domain's controllers, services, and mappers register through the
  service's existing `org.birdbro` scan and wildcard mapper scan with no hosting-side
  annotation changes
- **AND** exactly ONE application class exists in the module, at the service root
  package, and the hosted domain adds no second `UserDetailsService` when the hosting
  service already owns the account store

#### Scenario: Hosted domain migrates through the one ledger

- **WHEN** the hosting service boots against a database
- **THEN** Flyway applies the single version chain (V1..VN) whose history lives in the
  hosting service's schema, and the hosted domain's tables are created there
- **AND** no second schema, ledger, or database role is provisioned for the domain

#### Scenario: Hosted domain keeps its external contract

- **WHEN** the hosting move completes
- **THEN** the domain's URL prefix, endpoint paths, perms strings, and menu seeds are
  byte-identical to the pre-move contract, and clients (console pages, future Feign
  consumers) require no changes
- **WHEN** real consumer pressure later justifies a standalone service
- **THEN** re-homing the package into a new module and re-pointing the schema is the
  sanctioned split path (the package FQNs survive the move unchanged)
