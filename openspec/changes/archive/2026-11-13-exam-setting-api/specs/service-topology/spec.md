# Spec Delta: service-topology

## MODIFIED Requirements

### Requirement: Foundation Module Set

The backend SHALL provide the v1 foundation modules: `lumen-bom`, the OPS-lineage reference
modules `framework` and `common`, and the `lumen-system` service. Department, workflow, and
setting modules (`lumen-setting`, `lumen-exam-core`, `lumen-api`, `lumen-patient`,
`lumen-radiology`, `lumen-integration`, `lumen-us`, `lumen-endo`) SHALL NOT be created by
this capability; the changes that give them behavior add them.

#### Scenario: Foundation modules resolve

- **WHEN** the aggregator build completes
- **THEN** `framework`, `common`, and `lumen-system` have each produced a jar
- **AND** `lumen-bom` participates as a `pom` module (no jar - it emits only its resolved
  flattened POM as the version source)

#### Scenario: Deferred module requested

- **WHEN** a later feature needs `lumen-radiology` or `lumen-setting`
- **THEN** that feature's change adds the module and declares it in the aggregator
- **AND** no placeholder module exists before that change
