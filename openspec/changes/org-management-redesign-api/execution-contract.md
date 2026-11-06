# Execution Contract

> LoopForge workflow bridge. No implementation without an approved contract.

## Intent Lock

- **Change name**: org-management-redesign-api
- **Problem to solve**: the generic multi-root org tree cannot express on-premise
  hospital/campus operations, and the unpaginated department list cannot support large
  department volumes.
- **In scope**: controlled hospital/campus model, org profile fields, guarded org CRUD,
  campus-anchored departments, department status/code immutability, paginated department
  query, migration of seeded data.
- **Out of scope**: group mode, external partners, user-campus authorization, PACS/HL7
  configuration, logo upload, department nodes inside `org_info`.

## Approved Behavior

- **Approved requirements summary**: controlled hospital/campus tree; profile fields;
  guarded create/update/delete; campus-anchored department ownership; per-campus code
  uniqueness; immutable department code; paginated keyword/status/campus query.
- **Key scenarios**: single root, campus creation, type/parent immutability, delete guards,
  duplicate code by campus, code immutability, all-campus/campus department pages, stable
  migration of seeded departments.
- **Acceptance checks**: backend compile/test pass and all delta scenarios have mapped proof.

## Design Constraints

- **Architecture constraints**: lumen-system owns the endpoints; standard envelope and
  nginx entry remain unchanged.
- **Interface constraints**: preserve `/system/orgs` and `/system/org-deps` paths and
  page-level permission gates; `/system/org-deps/page` is additive.
- **Dependency constraints**: Java 11, Spring Boot 2.5.15, MyBatis-Plus, Flyway, PostgreSQL.
- **Data constraints**: logical delete and audit columns remain; existing department ids and
  codes stay stable.

## Task Batches

### Batch 1
- **Goal**: migrate data and update entities/DTOs.
- **Input**: V14 schema and existing org/department model.
- **Output**: hospital/campus schema, department status, default campus, profile fields.
- **Completion criteria**: fresh and migrated database tests pass.

### Batch 2
- **Goal**: enforce organization/department invariants and page query.
- **Input**: approved API/auth-rbac deltas.
- **Output**: guarded service behavior, additive page endpoint, controller contract tests.
- **Completion criteria**: all backend tests pass.

## Test Obligations

- **Behaviors requiring failing-test-first**: controlled root, campus leaf, type/parent
  immutability, delete guards, department owner validation, duplicate code by campus,
  immutable department code, department filtering/pagination.
- **Required edge cases**: missing parent, disabled campus, live dependents, migrated seeded
  departments, duplicate codes across versus within campuses, absent versus present campus
  filter.
- **Regression-sensitive areas**: tree assembly, logical delete, audit filling, permission
  gates, existing department list behavior.

## Execution Mode

- **Mode**: Batch Inline
- **Selection rationale**: one backend stack with tightly coupled migration and service
  invariants.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Complete | API/auth-rbac delta scenarios map to migration, org CRUD, org-dep CRUD/page, and permission tests. |
| Correctness | Complete | `./mvnw compile -q` and full `./mvnw test -q` passed on JDK 11 on 2026-11-06. |
| Coherence | Complete | Implementation follows the shared hospital/campus contract while preserving endpoint paths, envelopes, permissions, and audit behavior. |

**Overall**: Complete

## Review Gates

- **Mandatory review points**: migration safety; API compatibility; campus uniqueness;
  page-query permissioning.
- **Blocking categories**: data loss, permission downgrade, arbitrary root creation,
  department owner ambiguity.

## Escalation Rules

- **When to rewind to proposing**: group/hospital hierarchy becomes required; API core paths
  must break; department code must stay globally unique; departments must become org nodes.
- **When NOT to continue**: if contract intent no longer matches proposal scope.
