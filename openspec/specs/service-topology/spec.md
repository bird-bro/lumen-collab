# service-topology Specification

## Purpose
Defines which backend services and shared libraries exist, how they are assembled into the
Maven aggregator, where their boundaries lie, and the guarantee that a service can be
verified without its runtime infrastructure present.

## Requirements
### Requirement: Aggregator Module Declaration
The backend SHALL be built from a single Maven aggregator at `lumen-sev/pom.xml`. Every
module compiled as part of the backend build MUST be declared there. The monorepo root MUST
NOT be a Maven project, and no `lumen-services/` grouping layer SHALL exist.

#### Scenario: Full backend build from the aggregator
- **WHEN** `./mvnw compile -q` runs from `lumen-sev/` in a clean checkout
- **THEN** it exits 0
- **AND** every module declared in the aggregator has been compiled

#### Scenario: Module present on disk but not declared
- **WHEN** the aggregator build completes
- **THEN** the set of `pom.xml` files under `lumen-sev/` equals the declared `<module>` list
  plus the aggregator itself - no orphan module directory participates in the build

### Requirement: Foundation Module Set
The backend SHALL provide the v1 foundation modules: `lumen-bom`, the OPS reference modules
`framework` and `common` copied verbatim, and the `lumen-auth` service shell. Department and
workflow modules (`lumen-exam-core`, `lumen-api`, `lumen-patient`, `lumen-radiology`,
`lumen-integration`, `lumen-us`, `lumen-endo`) SHALL NOT be created by this capability; the
changes that give them behavior add them.

#### Scenario: Foundation modules resolve
- **WHEN** the aggregator build completes
- **THEN** `framework`, `common`, and `lumen-auth` have each produced a jar
- **AND** `lumen-bom` participates as a `pom` module (no jar - it emits only its resolved
  flattened POM as the version source)

#### Scenario: Deferred module requested
- **WHEN** a later feature needs `lumen-radiology`
- **THEN** that feature's change adds the module and declares it in the aggregator
- **AND** no placeholder `lumen-radiology` module exists before that change

### Requirement: Unified Entry Without a Java Gateway
This project SHALL contain no Java gateway module. The unified API entry SHALL be nginx
serving `/api/v1/<service>/**`; Spring Cloud Gateway is reserved for a future cloud-form
change.

#### Scenario: Unified entry is nginx
- **WHEN** this project's backend build is inspected
- **THEN** no Java gateway module exists under `lumen-sev/` and no Spring Cloud Gateway
  dependency appears on any compile path
- **AND** the nginx entry contract (`/api/v1/<service>/**`, 80/443) is reserved for the
  deferred `docker/` change to deploy

### Requirement: Verbatim Adoption of the OPS Base Modules
`framework` and `common` SHALL be copied from the OPS reference project with their module
names, artifactIds, and packages (`org.birdbro.framework` / `org.birdbro.common`) unchanged.
Any deviation beyond the recorded adaptations MUST be a recorded decision.

#### Scenario: Base modules compile on the pinned train
- **WHEN** the aggregator builds `framework` and `common`
- **THEN** both compile successfully against the OPS-verified release train
- **AND** no `@SpringBootApplication` class exists in either module

#### Scenario: Deviation from the OPS source
- **WHEN** a copied class differs from the OPS original for a reason other than the recorded
  adaptations
- **THEN** the difference is a governance violation
- **AND** it must be reverted or recorded as a decision

### Requirement: Recorded Adaptations to the Base Modules
Exactly the five adaptations enumerated in `design.md` D5 MAY be applied to the copied
modules, and no other change SHALL touch them.

#### Scenario: Redis is optional
- **WHEN** a service loads its context with no Redis server available
- **THEN** the context loads with zero Redis connection attempts (the conditional config is
  harmless by default; connections are lazy)
- **AND** the config is default-on: services that use Redis get the copied serializer and
  cache beans with no extra property, and a service opts out explicitly with
  `lumen.redis.enabled: false`

#### Scenario: Business baggage is removed
- **WHEN** the copy of `common` completes
- **THEN** `DingTalkConstants` and `WxCryptUtil` do not exist in this repository
- **AND** no other class from the OPS source is deleted

#### Scenario: Copied POM is governance-aligned
- **WHEN** the copied `common/pom.xml` is compared against the OPS original
- **THEN** the `bcprov-jdk16:1.46` version literal is absent
- **AND** `lumen-bom` manages `bcprov-jdk16:1.46`, so the resolved version is unchanged

### Requirement: Shared Library Boundary
`framework`, `common`, and the future `lumen-exam-core` SHALL be non-executable library
jars. They MUST NOT contain a Spring Boot application entry point, and they MUST NOT contain
business rules belonging to a single department service. The HTTP surface of a service
belongs to that service: controllers live in the service module, never in a shared library.

#### Scenario: Library is not runnable
- **WHEN** the build packages `framework` or `common`
- **THEN** the artifact is a plain jar with no `Main-Class` boot entry point
- **AND** no Spring Boot repackage goal has produced an executable jar

#### Scenario: HTTP surface ownership
- **WHEN** the copied base modules and the service shells are inspected
- **THEN** neither `framework` nor `common` declares a Spring MVC controller
  (`@RestControllerAdvice` advice classes are response/error plumbing, not endpoints)
- **AND** shared libraries expose service beans and extension points, not controllers

#### Scenario: Service-specific rule proposed for a shared library
- **WHEN** a rule applies to only one department's workflow
- **THEN** it belongs in that service, not in the shared modules

### Requirement: Service Identity
Each service SHALL declare `spring.application.name` equal to its module name, and SHALL
listen on a port reserved for it. `lumen-auth` MUST use port 8081; the unified entry nginx
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

### Requirement: Context Load Without Infrastructure
Each service SHALL load its Spring application context with no external infrastructure
running - no Nacos, PostgreSQL, Redis, or RocketMQ. Test execution MUST NOT require
`docker/` or any network service.

#### Scenario: Context load test with no services running
- **WHEN** `./mvnw test -q` runs from `lumen-sev/` on a machine where Nacos, PostgreSQL,
  Redis, and RocketMQ are all absent
- **THEN** it exits 0
- **AND** the context-load test for `lumen-auth` has passed

#### Scenario: Nacos unreachable during verification
- **WHEN** no Nacos server responds at verification time
- **THEN** service discovery registration and remote config import are inactive for that
  run
- **AND** the context still loads, so the absence is not a build failure

### Requirement: Sensitive Configuration Exclusion
Credentials and environment-specific connection values SHALL come from environment
variables or the configuration center. They MUST NOT be committed to the repository,
including in `application.yml` defaults and test resources.

#### Scenario: A database password is needed for a future service
- **WHEN** a service requires a credential
- **THEN** it is read from an environment variable or Nacos
- **AND** no literal secret value appears in any committed file
