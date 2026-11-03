# Spec Delta

## MODIFIED Requirements

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
