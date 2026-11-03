# Spec Delta

## MODIFIED Requirements

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

### Requirement: Recorded Adaptations to the Base Modules
The adaptations enumerated in the coordination doc (`init-project-skeleton.md`) MAY be
applied to the shared platform layer, and no other change SHALL touch it without a
recorded decision. The list grows only by recorded decision - six as of 2026-11-03
(adaptation (6): no-gateway audit-identity bridging + per-service anonymous paths).

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
