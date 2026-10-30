# Proposal: init-backend-skeleton

## Why

`lumen-sev/` today contains only `CLAUDE.md` and `AGENTS.md` - there is no `pom.xml`, no
module directory, and nothing to compile. Every v1 feature (patient, ris, integration)
needs a Maven aggregator to land in, a single place where dependency versions are decided,
and service shells that boot. Establishing that foundation now means later changes add
behavior instead of relitigating module layout and version governance.

## What Changes

- Create the `lumen-sev/pom.xml` Maven aggregator declaring the v1 foundation modules.
- Add `lumen-bom` as the single source of dependency versions (OPS's managed set 1:1 -
  Spring Boot 2.5.15 train, MyBatis-Plus, Druid, HAPI, dcm4che, RocketMQ, ...). No child POM
  declares a version literal.
- Copy the OPS base modules **verbatim** - `framework` and `common` keep their names,
  artifactIds, and packages (`org.birdbro.framework` / `org.birdbro.common`) - applying only
  the four recorded adaptations: Redis-conditional config, security wiring boundary
  (lumen-auth issues tokens), per-service operation-log table ownership. This supersedes
  the earlier five-way `lumen-common-*` plan.
- Add the `lumen-auth` service shell with a Spring Boot entry point, `application.yml`
  carrying its name and port (8081), and Nacos discovery/config wiring. **There is no Java
  gateway module** - the unified API entry is nginx (user decision 2026-10-29); Spring
  Cloud Gateway is reserved for a future cloud-form change.
- Guarantee every module compiles and every Spring context loads with **no external
  infrastructure running** (no Nacos, PostgreSQL, Redis, or RocketMQ), because `docker/` is
  out of scope for this feature and no test may depend on it.
- **No business logic, no REST endpoints, no database schema, no Flyway migrations.** The
  copied `framework` carries OPS's exception handler and `R` envelope code, but nothing
  invokes them - no endpoints exist yet; they are exercised by the feature changes that
  need them.

## Capabilities

### New Capabilities

- `service-topology`: which backend services and shared libraries exist, how they are
  assembled into the aggregator, their module boundaries, their identity (service name and
  port), and the guarantee that a service boots without its runtime dependencies present.
  Future services (patient, ris, integration, us, endo) add requirements here.
- `dependency-governance`: the rule that third-party and inter-module versions are declared
  exactly once, in `lumen-bom`, and that shared libraries stay non-executable. Applies to
  every module added later.

### Modified Capabilities

Contract alignment with the OPS reference (user decision 2026-10-29: the OPS `R` envelope
and `EntityBase` audit contract are adopted verbatim, so the ported code and the specs
agree). This change **modifies the contracts only - it implements none of them**:

- `api`: response envelope changes from `{ code, data, message }` (numeric codes) to the OPS
  `R` shape `{ timestamp, code: string, msg, data }` with `"200"`/`"204"` success codes.
- `errors`: the numeric `0` / `4xxxx` / `5xxxx` scheme is replaced by the OPS
  `ExceptionEnum` letter scheme (`"0000"`/HTTP-status success strings, `A`-class client,
  `B`-class system, `C`-class third-party), error field `msg`.
- `data`: the audit-field requirement changes from `created_at`/`updated_at`
  (timestamptz, UTC) to the OPS `EntityBase` contract - `deleted` logical delete,
  `create_by`/`update_by` operator ids filled by MyBatis-Plus, `create_time`/`update_time`
  serialized `yyyy-MM-dd HH:mm:ss` GMT+8.

## Affected Stacks

single-stack: backend (`lumen-sev/`)

## Depends-On

Cross-stack tag `init-project-skeleton` - see
`openspec/coordination/init-project-skeleton.md`. No code dependency on the sibling change
`init-web-skeleton`; the two share only the port allocation recorded in the coordination
doc.

## Scope

- In scope:
  - `lumen-sev/pom.xml` aggregator + `.mvn` / wrapper as needed
  - `lumen-bom` (OPS managed set 1:1)
  - `framework` + `common` copied verbatim from OPS (four recorded adaptations only)
  - `lumen-auth` service shell (entry point + config + Nacos wiring, port 8081)
  - Build-time and context-load tests that pass with zero infrastructure
- Out of scope:
  - `lumen-exam-core`, `lumen-api/{patient-api,integration-api}`, `lumen-patient`,
    `lumen-radiology`, `lumen-integration`, `lumen-us`, `lumen-endo`
  - **Any Java gateway** - nginx is the unified entry (80/443); Spring Cloud Gateway is a
    future cloud-form change
  - Any REST endpoint, including health/ping
  - Security wiring beyond what the copied classes provide (token issuance logic belongs to
    the auth feature change)
  - Flyway migrations, entities, mappers, any datasource actually connecting
  - RocketMQ producers/consumers, Sentinel rules, JWT issuing/validation
  - `docker/` compose files and dcm4chee-arc-light
  - CI workflow, GitHub organization registration

## Success Criteria

- `./mvnw compile -q` exits 0 with all declared modules built.
- `./mvnw test -q` exits 0 on a machine running no Nacos, PostgreSQL,
  Redis, or RocketMQ.
- No `<version>` literal for a third-party dependency appears in any child POM; versions
  resolve through `lumen-bom`.
- Every version `lumen-bom` shares with OPS's `ops_sev/pom.xml` is **identical** to OPS's;
  differences exist only for libraries OPS does not manage (dcm4che, HAPI HL7v2, RocketMQ).
- `diff -r` between the copied `framework`/`common` and the OPS originals shows only the
  four recorded adaptations.
- `lumen-auth` starts a Spring application context in a test, with
  `spring.application.name` and its reserved server port (8081), on a machine running no
  infrastructure.
- `openspec validate init-backend-skeleton --strict` passes.

## Constraints

- Backend version train is the **OPS-verified set** (`/Users/birdbro/work/01-JF/00-gitlab/OPS/ops_sev/pom.xml`):
  Java 11, Spring Boot 2.5.15, Spring Cloud 2020.0.3, Spring Cloud Alibaba 2021.1, and OPS's
  shared jars matched exactly (Lombok 1.18.30, Hutool 5.8.20, MyBatis-Plus
  `mybatis-plus-boot-starter` 3.5.3.1, Druid 1.2.18, fastjson2 2.0.38, easyexcel 3.3.2,
  mapstruct 1.5.5.Final, and the rest of OPS's managed set). This supersedes the earlier
  Java 17 + Boot 3.x line in the handoff doc and root `CLAUDE.md` (user decision 2026-10-29 -
  OPS's jar set is verified mutually compatible).
- Aggregator lives at `lumen-sev/pom.xml`; the monorepo root is **not** a Maven project.
- Services sit flat under `lumen-sev/` - the former `lumen-services/` grouping layer is
  abolished (handoff doc §5).
- **Unified API entry is nginx** (80/443); no Java gateway module exists in this project -
  Spring Cloud Gateway is a future cloud-form change. **Disciplines:** only Servlet services
  depend on `framework`/`common`; any reactive/gateway module never does.
- Sensitive config (DB passwords) via env vars / Nacos only, never committed.
- File names kebab-case; do not modify existing public methods, use overloading.
- Auto-loaded files English only.
- Tests MUST NOT require external infrastructure - `docker/` is deferred.

## Risks

- Spring Cloud Alibaba Nacos discovery/config fails fast at startup when no Nacos server is
  reachable, breaking the "boots without infrastructure" criterion → disable registration
  and the config import check in the test profile; assert context load only in tests. See
  `design.md` for the exact properties.
- The OPS train runs Spring Boot 2.5.15, whose open-source support ended (2.5.x OSS EOL).
  A new project shipping on it inherits that: no more OSS security patches, `javax`
  namespace, and libraries still on the Boot 2 line. Accepted deliberately (2026-10-29) in
  exchange for a jar set whose mutual compatibility is production-proven in OPS; the
  upgrade to a newer train is a future proposal, not this change's concern.
- Two OPS-managed versions carry known CVEs at those exact coordinates: PostgreSQL driver
  42.2.18 (CVE-2022-21724, fixed in 42.2.25) and commons-fileupload 1.3.1 (CVE-2016-1000033,
  fixed in 1.3.3). Kept identical to OPS per the user's directive; patch-level bumps within
  the same minor line are pre-approved as candidate deviations once the user signs off.
- The verified part of the train is what OPS manages. dcm4che, HAPI HL7v2, and RocketMQ are
  **not** in OPS, so their versions carry no OPS verification → manage them in `lumen-bom`
  but keep them off every compile path in this change; their real verification happens in
  the changes that introduce `lumen-integration` and the MQ layer.
- Verbatim-copied `framework`/`common` invite contributors to keep editing them freely,
  eroding the byte-faithful reuse guarantee → the spec makes verbatim adoption normative
  (only four recorded adaptations) and the contract makes any other diff a blocking review
  category.
- dcm4che and HAPI HL7v2 artifacts have non-obvious coordinates and may be absent from
  Maven Central mirrors → declare them in `lumen-bom` version management only, without
  pulling them into any module's compile path in this feature.
