# Execution Contract

## Intent Lock

- **Change name**: org-status-toggle-web
- **Problem to solve**: campus status switch should use the explicit status command.
- **In scope**: typed API client, switch wiring, tests.
- **Out of scope**: profile drawer status handling, permissions redesign.

## Approved Behavior

- Toggling calls `PUT /system/orgs/{id}/status` with `0|1`.
- Switch state refreshes from the org tree.
- Without `system:org:update`, the switch is disabled.

## Task Batches

### Batch 1
- **Goal**: frontend command call and coverage.
- **Output**: API client, page change, tests.
- **Completion criteria**: web tests and build pass.

## Test Obligations

- Request contract, toggle refresh, permission gating.

## Execution Mode

- **Mode**: Inline
- **Selection rationale**: narrow UI/API wiring change.
