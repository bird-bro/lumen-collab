<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# CLAUDE.md - lumen-collab Backend Agent

## Role
You are a **Backend Agent**. Your scope: server-side logic, APIs, data access, business rules.
**NEVER generate frontend code** (that lives in `../lumen-web/CLAUDE.md`).
**NEVER modify `../openspec/specs/`** - specs are shared truth, read-only.

## Project Overview
- **System**: lumen-collab - open-source medical imaging collaboration platform (RIS v1; pairs with dcm4chee-arc-light)
- **Stack**: Java 11 + Spring Boot 2.5.15 + Spring Cloud 2020.0.3 + Spring Cloud Alibaba 2021.1 + OpenFeign + MyBatis-Plus + Flyway (OPS-verified train - all versions come from `lumen-bom`, copied 1:1 from the OPS reference project; do not change versions without a new proposal)
- **Database**: PostgreSQL - one shared database `lumen`, one schema per service (`auth` / `patient` / `radiology` / `us` / `endo` / `integration`), one connection role per service (user decision 2026-11-02, auth-rbac-foundation; handoff script `docs/deployment/pg-init.sql`)

## Before You Code
1. Read `../openspec/specs/api/spec.md` (authoritative contract)
2. Read `../openspec/specs/data/spec.md` and `../openspec/specs/errors/spec.md`
3. Check `../openspec/changes/` for active proposals
4. If no spec exists, run `/opsx:propose` first - never code without a spec

## Module Structure (Maven multi-module, aggregator at this dir's `pom.xml`)
- `lumen-bom` - dependency versions (single source, only place versions are declared; OPS managed set 1:1)
- `framework` + `common` - OPS-lineage shared platform layer (packages `org.birdbro.framework` / `org.birdbro.common`); base of all Servlet services; EVOLVABLE on the OPS basis per user decision 2026-11-03 - additive-first adaptations recorded in the coordination doc (six as of 2026-11-03)
- `lumen-exam-core` - ★ exam workflow kernel: booking → registration → exam → report state machine (services + extension points, **no controllers**)
- `lumen-api/{patient-api,integration-api}` - inter-service contracts (OpenFeign interfaces + DTO, add-only)
- `lumen-auth` - users / roles / departments / permissions (JWT issuer) · `lumen-patient` (EMPI) · `lumen-radiology` (v1 ★, radiology) · `lumen-us` / `lumen-endo` (v2) · `lumen-integration` (DICOM MWL/MPPS SCP + HL7v2, routes by department)
- **No Java gateway** - nginx is the unified API entry (`/api/v1/<service>/**`, port 80/443); Spring Cloud Gateway is a future cloud-form change
- Packages: copied modules keep `org.birdbro.{framework,common}`; new modules use `org.birdbro.lumen.*`; layering `controller → service → mapper` per service (see `.claude/rules/naming.md`)
- Department services depend on `lumen-exam-core` and extend it - never fork the kernel

## Architecture Rules
- Sync query = OpenFeign + Sentinel fallback; state change = RocketMQ event (transactional messages for atomic local-DB + event publish) - no cross-service distributed transactions
- Department services never touch DICOM/HL7 protocols - `lumen-integration` owns MWL / MPPS / HL7v2
- Study linkage: dcm4chee-arc QIDO-RS by Accession Number
- Flexible structures (report templates, structured findings, department forms) → PostgreSQL `jsonb` + GIN index
- IDs: Integer auto-increment, serialized as JSON numbers (user decision 2026-11-02, OPS/随访 lineage - the former snowflake-bigint rule was withdrawn by auth-rbac-foundation's api/data deltas)
- Sensitive config (DB passwords) via env vars / the gitignored application-dev.yml (随访 fu-launch pattern, design D8 - no Nacos since 2026-11-03) - never committed

## Coding Standards
- Layering: controller → service → mapper; DTOs never leak into domain entities
- Unified response + global exception handler from the copied `framework` (`GlobalResponseAdvice` / `GlobalExceptionHandler`); error codes per `../openspec/specs/errors/spec.md`
- Every table: `id` + the OPS `EntityBase` audit fields (`deleted`, `create_by`, `create_time`, `update_by`, `update_time`) via the copied `framework`'s `MetaObjectConfig`
- Entity classes carry the OPS Lombok combo `@Data @Builder @AllArgsConstructor @NoArgsConstructor` (user decision 2026-11-03) - `@Builder` alone removes the no-arg constructor MyBatis-Plus row mapping needs. Entity assignment: `BeanUtil.copyProperties(request, entity, true)` (OPS house idiom, ignore-null) when fields match 1:1; `Xxx.builder()...build()` for computed/no-source patches; renamed or semantic-converting fields stay explicit
- Hutool (`hutool-all` 5.8.20, on the classpath via `common`) is the DEFAULT utility toolkit - reach for `StrUtil`/`CollUtil`/`MapUtil`/`BeanUtil.copyProperties(src, dest, true)`/`IdUtil`/... before hand-rolling any check or copy (user decision 2026-11-03, applies broadly); lumen-bom version pins still require a proposal
- Schema changes ONLY via Flyway migrations (`src/main/resources/db/migration`) - never manual DDL
- Every service's committed `application.yml` ships explicit `spring.flyway.schemas` + `spring.flyway.default-schema` = ITS OWN schema (e.g. `radiology`) - never public, never left to the implicit `currentSchema` first-entry fallback (user ruling 2026-11-03); a misconfigured URL must fail loudly, not migrate public
- Shared conventions in `../.claude/rules/`

## Superpowers Workflow (auto-triggered by /opsx:propose, /opsx:apply)
1. brainstorm → clarify requirements
2. writing-plans → TDD plan
3. executing-plans → implement (tests first)
4. code-review → self/peer review
5. verification-before-completion → prove it against WHEN/THEN

## TDD
- Red: failing test for the WHEN/THEN scenario
- Green: minimum code to pass
- Refactor: keep tests green

## Build Commands
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home   # OPS-verified train runs on JDK 11
./mvnw compile -q                # all modules, from lumen-sev/ (wrapper pins Maven 3.9.9)
./mvnw test -q                   # all tests (zero infrastructure required - no Nacos/PG/Redis/MQ)
./mvnw -pl lumen-radiology -am test -q   # one service + its dependencies
```
Reactor-only rule: children resolve `lumen-bom` through the aggregator's import (the BOM is
a standalone reactor module - Batch 1 decision), so build from the aggregator; a standalone
`mvn -f <module>/pom.xml` fails until `./mvnw -pl lumen-bom install` has been run.
