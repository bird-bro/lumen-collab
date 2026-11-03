# Spec Delta

## MODIFIED Requirements

### Requirement: Foundation Module Set
The backend SHALL provide the v1 foundation modules: `lumen-bom`, the OPS reference modules
`framework` and `common` copied verbatim, and the `lumen-system` service. Department and
workflow modules (`lumen-exam-core`, `lumen-api`, `lumen-patient`, `lumen-radiology`,
`lumen-integration`, `lumen-us`, `lumen-endo`) SHALL NOT be created by this capability; the
changes that give them behavior add them.

#### Scenario: Foundation modules resolve
- **WHEN** the aggregator build completes
- **THEN** `framework`, `common`, and `lumen-system` have each produced a jar
- **AND** `lumen-bom` participates as a `pom` module (no jar - it emits only its resolved
  flattened POM as the version source)

#### Scenario: Deferred module requested
- **WHEN** a later feature needs `lumen-radiology`
- **THEN** that feature's change adds the module and declares it in the aggregator
- **AND** no placeholder `lumen-radiology` module exists before that change

### Requirement: Service Identity
Each service SHALL declare `spring.application.name` equal to its module name, and SHALL
listen on a port reserved for it. `lumen-system` MUST use port 8081; the unified entry nginx
listens on 80/443 and is not a backend module. Allocations live in
`openspec/coordination/init-project-skeleton.md`.

#### Scenario: Service starts with its declared identity
- **WHEN** a service application context is loaded in a test
- **THEN** `spring.application.name` resolves to that service's module name
- **AND** `server.port` resolves to its reserved port

#### Scenario: Two services claim the same port
- **WHEN** a new service is added without a distinct port
- **THEN** the coordination doc's port table is not satisfied
- **AND** the conflict must be resolved before the service is accepted
