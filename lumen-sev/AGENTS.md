<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# CLAUDE.md - lumen-collab Backend Agent

## Role
You are a **Backend Agent**. Your scope: server-side logic, APIs, data access, business rules.
**NEVER generate frontend code** (that lives in `../lumen-web/CLAUDE.md`).
**NEVER modify `../openspec/specs/`** - specs are shared truth, read-only.

## Project Overview
- **System**: lumen-collab - open-source medical imaging collaboration platform (RIS v1; pairs with dcm4chee-arc-light)
- **Stack**: Java 11 + Spring Boot 2.5.15 + Spring Cloud 2020.0.3 + Spring Cloud Alibaba 2021.1 + OpenFeign + MyBatis-Plus + Flyway (OPS-verified train - all versions come from `lumen-bom`, copied 1:1 from the OPS reference project; do not change versions without a new proposal)
- **Database**: PostgreSQL - one shared database `lumen`, one schema per service (`system` / `patient` / `radiology` / `us` / `endo` / `integration`)

## Before You Code
1. Read `../openspec/specs/api/spec.md` (authoritative contract)
2. Read `../openspec/specs/data/spec.md` and `../openspec/specs/errors/spec.md`
3. Check `../openspec/changes/` for active proposals
4. If no spec exists, run `/opsx:propose` first - never code without a spec

## Module Structure (Maven multi-module, aggregator at this dir's `pom.xml`)
- `lumen-bom` - dependency versions (single source, only place versions are declared; OPS managed set 1:1)
- `framework` + `common` - ★ copied **verbatim** from the OPS reference project (packages `org.birdbro.framework` / `org.birdbro.common`); shared base of all Servlet services; only the five recorded adaptations may differ (see coordination doc)
- `lumen-exam-core` - ★ exam workflow kernel: booking → registration → exam → report state machine (services + extension points, **no controllers**)
- `lumen-api/{patient-api,integration-api}` - inter-service contracts (OpenFeign interfaces + DTO, add-only)
- `lumen-system` - users / roles / departments / permissions / dicts / log query (JWT issuer; renamed from lumen-auth, 2026-11-03) · `lumen-patient` (EMPI) · `lumen-radiology` (v1 ★, radiology) · `lumen-us` / `lumen-endo` (v2) · `lumen-integration` (DICOM MWL/MPPS SCP + HL7v2, routes by department)
- **No Java gateway** - nginx is the unified API entry (`/api/v1/<service>/**`, port 80/443); Spring Cloud Gateway is a future cloud-form change
- Packages: copied modules keep `org.birdbro.{framework,common}`; new modules use `org.birdbro.lumen.*`; layering `controller → service → mapper` per service (see `.claude/rules/naming.md`)
- Department services depend on `lumen-exam-core` and extend it - never fork the kernel

## Architecture Rules
- Sync query = OpenFeign + Sentinel fallback; state change = RocketMQ event (transactional messages for atomic local-DB + event publish) - no cross-service distributed transactions
- Department services never touch DICOM/HL7 protocols - `lumen-integration` owns MWL / MPPS / HL7v2
- Study linkage: dcm4chee-arc QIDO-RS by Accession Number
- Flexible structures (report templates, structured findings, department forms) → PostgreSQL `jsonb` + GIN index
- IDs: snowflake at application layer (serialize as JSON string per API spec), PG sequence fallback
- Sensitive config (DB passwords) via env vars / Nacos - never committed

## Coding Standards
- Layering: controller → service → mapper; DTOs never leak into domain entities
- Unified response + global exception handler from the copied `framework` (`GlobalResponseAdvice` / `GlobalExceptionHandler`); error codes per `../openspec/specs/errors/spec.md`
- Every table: `id` + the OPS `EntityBase` audit fields (`deleted`, `create_by`, `create_time`, `update_by`, `update_time`) via the copied `framework`'s `MetaObjectConfig`
- Schema changes ONLY via Flyway migrations (`src/main/resources/db/migration`) - never manual DDL
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
