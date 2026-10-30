# Spec Delta

## Purpose

Defines how third-party and inter-module dependency versions are governed across the
backend, so that every module resolves the same versions and no child POM becomes a second
source of truth.

## ADDED Requirements

### Requirement: Single Version Source
`lumen-bom` SHALL be the only place where dependency versions are declared. A child module
POM MUST NOT contain a `<version>` literal for a third-party or inter-module dependency; it
inherits the managed version.

#### Scenario: Child module declares a dependency
- **WHEN** a module adds a dependency that `lumen-bom` manages
- **THEN** the module's `<dependency>` entry omits `<version>`
- **AND** the resolved version comes from `lumen-bom`

#### Scenario: Version literal found in a child POM
- **WHEN** a child module POM states an explicit `<version>` for a managed dependency
- **THEN** the governance rule is violated
- **AND** the version must be moved into `lumen-bom`

### Requirement: Verified Release Train from the OPS Reference
`lumen-bom` SHALL pin the release train already verified in the reference project OPS
(`ops_sev/pom.xml`): Java 11, Spring Boot 2.5.15, Spring Framework BOM 5.3.33, Spring Cloud
2020.0.3, and Spring Cloud Alibaba 2021.1.

#### Scenario: Build resolves the pinned train
- **WHEN** the aggregator build resolves dependencies
- **THEN** Spring Boot, Spring Cloud, and Spring Cloud Alibaba all come from the versions
  declared in `lumen-bom`
- **AND** compilation succeeds against those versions

#### Scenario: A later change needs a new dependency
- **WHEN** a feature adds a library not yet managed
- **THEN** its version is declared in `lumen-bom` first
- **AND** the feature module consumes it without a version literal

### Requirement: Shared Library Version Parity with OPS
For every library that OPS also manages - including Lombok, Hutool, MyBatis-Plus, Druid,
fastjson2, easyexcel, and mapstruct - `lumen-bom` MUST declare the identical version. A
deviation MUST be recorded as an explicit decision in the coordination doc.

#### Scenario: A shared jar matches OPS
- **WHEN** the BOM's managed version for a library that OPS also manages is compared against
  `ops_sev/pom.xml`
- **THEN** the versions are identical

#### Scenario: A deviation is proposed
- **WHEN** a version differs from OPS's for a managed library
- **THEN** the difference traces to a recorded decision in the coordination doc
- **AND** an unrecorded difference is treated as drift and reverted

#### Scenario: A library OPS does not manage
- **WHEN** the BOM manages a library absent from OPS, such as dcm4che, HAPI HL7v2, or
  RocketMQ
- **THEN** the version is chosen by this project and flagged as not OPS-verified in
  `design.md`
- **AND** it stays off every compile path until its own change verifies it

### Requirement: Integration Libraries Managed Not Linked
Coordinates for dcm4che, HAPI HL7v2 (`ca.uhn.hapi`, not HAPI FHIR), and RocketMQ SHALL be
version-managed in `lumen-bom`. They MUST NOT be placed on the compile path of any module in
this change, because no module yet uses them. The PostgreSQL driver IS on the base modules'
compile path - the verbatim copy of OPS `framework`/`common` declares it; its version still
resolves through `lumen-bom`.

#### Scenario: Integration dependency is available but unused
- **WHEN** the foundation build completes
- **THEN** no module has dcm4che, HAPI HL7v2, or RocketMQ on its compile classpath
- **AND** `lumen-bom` still resolves their versions for the change that needs them

#### Scenario: Integration service is added later
- **WHEN** `lumen-integration` is created by its own change
- **THEN** it consumes the already-managed dcm4che and HAPI HL7v2 versions
- **AND** it does not introduce its own version literals
