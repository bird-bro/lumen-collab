<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# CLAUDE.md - lumen-collab Backend Agent

## Role
You are a **Backend Agent**. Your scope: server-side logic, APIs, data access, business rules.
**NEVER generate frontend code** (that lives in `../lumen-web/CLAUDE.md`).
**NEVER modify `../openspec/specs/`** - specs are shared truth, read-only.

## Project Overview
- **System**: lumen-collab - open-source medical imaging collaboration platform (RIS v1; pairs with dcm4chee-arc-light)
- **Stack**: Java 17 + Spring Boot 3.x + Spring Cloud Alibaba (Nacos, Sentinel) + OpenFeign + MyBatis-Plus + Flyway
- **Database**: PostgreSQL - one DB per service (`lumen_auth` / `lumen_patient` / `lumen_ris` / `lumen_us` ...)

## Before You Code
1. Read `../openspec/specs/api/spec.md` (authoritative contract)
2. Read `../openspec/specs/data/spec.md` and `../openspec/specs/errors/spec.md`
3. Check `../openspec/changes/` for active proposals
4. If no spec exists, run `/opsx:propose` first - never code without a spec

## Module Structure (Maven multi-module, aggregator at this dir's `pom.xml`)
- `lumen-bom` - dependency versions (single source, only place versions are declared)
- `lumen-common/{core,web,mybatis,security,dicom}` - shared libs (jar, no business logic)
- `lumen-exam-core` - ★ exam workflow kernel: booking → registration → exam → report state machine
- `lumen-api/{patient-api,integration-api}` - inter-service contracts (OpenFeign interfaces + DTO)
- `lumen-gateway` - Spring Cloud Gateway · `lumen-auth` - users / roles / departments / permissions (JWT)
- `lumen-patient` (EMPI) · `lumen-ris` (v1 ★) · `lumen-us` / `lumen-endo` (v2) · `lumen-integration` (DICOM MWL/MPPS SCP + HL7v2, routes by department)
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
- Unified response + global exception handler from `lumen-common-web`; error codes per `../openspec/specs/errors/spec.md`
- Every table: `id` + audit fields (`created_at` / `updated_at`) via `lumen-common-mybatis`
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
mvn compile -q                  # all modules (run inside lumen-sev/)
mvn test -q                     # all tests
mvn -pl lumen-ris -am test -q   # one service + its dependencies
```
