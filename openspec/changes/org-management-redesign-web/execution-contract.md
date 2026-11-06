# Execution Contract

> LoopForge workflow bridge. No implementation without an approved contract.

## Intent Lock

- **Change name**: org-management-redesign-web
- **Problem to solve**: the organization console exposes a generic tree, and the department
  page treats a hospital as a possible department owner while listing departments without
  scalable filters/pagination.
- **In scope**: existing organization page rewrite, hospital card, campus table, campus
  profile forms, department filter tree, department table/dialog, typed API updates, tests.
- **Out of scope**: group/partner UI, logo upload, drag ordering, user-campus permissions,
  department nodes in the filter tree, mock layer.

## Approved Behavior

- **Approved requirements summary**: hospital profile card plus campus table; no normal root
  creation; profile/default-campus management; hospital/campus department filter tree;
  paginated department table; campus-only department owner; immutable department code;
  permission gating; backend rejection surfacing.
- **Key scenarios**: hospital card render, campus CRUD, no normal root creation, profile
  defaults, all-campus versus campus department query, keyword/status pagination, same code
  across campuses.
- **Acceptance checks**: web build/test pass and real-backend walkthrough matches deltas.

## Design Constraints

- **Architecture constraints**: React 18 + TypeScript + Vite + AntD 5; typed API client seam.
- **Interface constraints**: use real `/api/v1` through the Vite proxy; no mock layer.
- **Dependency constraints**: existing AntD/console components; no new UI framework.
- **Data constraints**: preserve organization/department ids and department code immutability.

## Task Batches

### Batch 1
- **Goal**: align API types and clients.
- **Input**: backend API delta.
- **Output**: typed org/department contracts and page query client.
- **Completion criteria**: client tests and build pass.

### Batch 2
- **Goal**: rewrite organization and department interactions.
- **Input**: approved web delta.
- **Output**: dictionary-like org page and tree-filtered department table.
- **Completion criteria**: all web tests pass.

## Test Obligations

- **Behaviors requiring failing-test-first**: hospital card/campus table rendering, no root
  creation, hospital-root all-campus query, campus-only department owner, profile form
  prefill/rejection, keyword/status pagination, duplicate-code field error.
- **Required edge cases**: permission absence, backend structural rejection, default campus
  uniqueness, same code across campuses, disabled campus, campus reassignment.
- **Regression-sensitive areas**: layout routing, permission hook, api-client seam, existing
  department tests.

## Execution Mode

- **Mode**: Batch Inline
- **Selection rationale**: bounded existing-page rewrite with backend-first dependency.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: no generic root creation; department tree contains no
  departments; campus-only department owner; backend-first real-API verification.
- **Blocking categories**: mock-layer reintroduction, permission bypass, ambiguous campus
  context, unpaginated large-list regression.

## Escalation Rules

- **When to rewind to proposing**: group/partner modeling enters scope; backend page contract
  changes materially; department code must become editable; department editing returns to the
  left tree.
- **When NOT to continue**: if backend campus semantics or the department page endpoint are
  not available as specified.
