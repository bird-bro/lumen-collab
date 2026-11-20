## MODIFIED Requirements

### Requirement: Foundation Module Set

The backend SHALL provide the v1 foundation modules: `lumen-bom`, the OPS-lineage reference
modules `framework` and `common`, and the `lumen-system` service. Department and workflow
modules (`lumen-core`, `lumen-feign`, `lumen-patient`, `lumen-radiology`,
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

<!-- rename-lumen-api-feign 2026-11-20: module list name only (lumen-api → lumen-feign), zero behavior change -->
