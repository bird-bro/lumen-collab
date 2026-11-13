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

<!-- synced from: rename-auth-to-system 2026-11-04 -->

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

<!-- synced from: spec-hygiene 2026-11-04 -->

### Requirement: Verbatim Adoption of the OPS Base Modules
`framework` and `common` are the OPS-lineage shared platform layer: module names,
artifactIds, and packages (`org.birdbro.framework` / `org.birdbro.common`) SHALL stay
unchanged, and the modules MAY evolve on the OPS basis (user decision 2026-11-03,
superseding the verbatim-only reading of 2026-10-29). Evolution SHALL be ADDITIVE-FIRST -
new files over modified OPS lines - so future upstream syncs stay conflict-free. Any
deviation beyond the recorded adaptations MUST be a recorded decision.

#### Scenario: Base modules compile on the pinned train
- **WHEN** the aggregator builds `framework` and `common`
- **THEN** both compile successfully against the OPS-verified release train
- **AND** no `@SpringBootApplication` class exists in either module

#### Scenario: Deviation from the OPS source
- **WHEN** a copied class differs from the OPS original for a reason other than the recorded
  adaptations
- **THEN** the difference is a governance violation
- **AND** it must be reverted or recorded as a decision

#### Scenario: Additive evolution lands in the shared layer
- **WHEN** a lumen capability needs platform-wide machinery (e.g. the no-gateway
  USER-HEADER bridge)
- **THEN** it lands as NEW `framework`/`common` files keeping the OPS namespaces
- **AND** the adaptation is recorded in the coordination doc's adaptation list

<!-- synced from: system-data 2026-11-03 -->

### Requirement: Recorded Adaptations to the Base Modules
The adaptations enumerated in the coordination doc (`init-project-skeleton.md`) MAY be
applied to the shared platform layer, and no other change SHALL touch it without a
recorded decision. The list grows only by recorded decision - as of 2026-11-03 it spans,
among others: (6) no-gateway audit bridging + per-service anonymous paths; (7)
`ice.`-free log table names (entities AND the log mapper XMLs - the real write path)
resolving via `search_path = <service-schema>, public` (the 随访 precedent); (8) the
request-context filter that lets the copied logout handler record sign events.

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

#### Scenario: No-gateway audit bridging is platform machinery
- **WHEN** a service with authenticated writes handles an INSERT
- **THEN** `MetaObjectConfig` fills `create_by` from the USER-HEADER bridged by the
  framework `UserHeaderBridgeFilter` (present in every service via component scan)
- **AND** anonymous paths come from the service's `security.permit-all` config

#### Scenario: Log entities resolve without the ice schema
- **WHEN** the copied `framework/log` entities (`LogInfoEntity`/`LogSignEntity`) run
  against the lumen database
- **THEN** their table names are unqualified (`log_info`/`log_sign`) and resolve through
  the connection's search_path to the `public` schema
- **AND** no `ice` schema exists in lumen

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

<!-- synced from: rename-auth-to-system 2026-11-04 -->

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

<!-- synced from: auth-rbac-foundation 2026-11-03 -->

### Requirement: Context Load Without Infrastructure
Each service SHALL load its Spring application context and run its tests without any
**deployed production** infrastructure. `lumen-system`'s data-layer tests SHALL run against
the team test server's PostgreSQL and Redis (externally provisioned), with connections
read from a gitignored local env file - never committed. Unconfigured connections SHALL
fail the tests explicitly (never silently skipped). Other modules (`framework`, `common`)
keep their zero-dependency tests unchanged.

#### Scenario: Context load test with no services running
- **WHEN** `./mvnw test -q` runs from `lumen-sev/` with no deployed production services,
  and the test-server env file present
- **THEN** it exits 0
- **AND** Flyway has applied the migrations to the test server's `lumen` database and the
  mappers have been exercised against it

#### Scenario: Nacos unreachable during verification
- **WHEN** verification runs where no Nacos server exists or responds - the stack has no
  service registry and no remote config center at all since user decision 2026-11-03
  (configuration is local, 随访 fu-launch shape, design D8)
- **THEN** service discovery registration and remote config import are inactive for that
  run - the starters are removed and nothing attempts either
- **AND** the context still loads from the local `application.yml` (+ the gitignored
  profile file), so the absence is not a build failure

<!-- synced from: auth-rbac-foundation 2026-11-03 -->

### Requirement: Sensitive Configuration Exclusion
Credentials and environment-specific connection values SHALL come from environment
variables or a GITIGNORED profile file (`application-dev.yml`, with the template
`application-dev.yml.example` committed - the 随访 fu-launch pattern, user decision
2026-11-03). No configuration center exists in the stack. Real secret values MUST NOT
be committed to the repository; committed `application.yml` may carry feature defaults
and explicit replace-me placeholders only.

#### Scenario: A database password is needed for a future service
- **WHEN** a service requires a credential
- **THEN** it is read from an environment variable or the gitignored
  `application-dev.yml`
- **AND** no literal secret value appears in any committed file
