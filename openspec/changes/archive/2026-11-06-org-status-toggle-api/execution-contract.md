# Execution Contract

## Intent Lock

- **Change name**: org-status-toggle-api
- **Problem to solve**: org status toggling is buried in the generic profile update.
- **In scope**: `PUT /system/orgs/{id}/status`, validation, service method, tests.
- **Out of scope**: new permission seed, removing generic status support, frontend.

## Approved Behavior

- Valid `0|1` status toggles a live org and returns `data: true`.
- Missing/non-0/1 status is rejected without mutation.
- Authorization remains `system:org:update`.

## Task Batches

### Batch 1
- **Goal**: backend endpoint and integration coverage.
- **Output**: controller, DTO, service, tests.
- **Completion criteria**: lumen-system tests pass.

## Test Obligations

- Valid toggle and invalid status rejection.

## Execution Mode

- **Mode**: Inline
- **Selection rationale**: narrow additive endpoint with one integration path.
