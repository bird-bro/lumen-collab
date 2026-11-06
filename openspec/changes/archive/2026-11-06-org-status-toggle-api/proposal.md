# Proposal: org-status-toggle-api

## Why

The org campus table uses a direct status switch, but the current call submits a
partial profile payload to `PUT /system/orgs/{id}`. User management already exposes
an explicit enable endpoint; org status should have the same command-shaped API.

## What Changes

- Add `PUT /api/v1/system/orgs/{id}/status` in `lumen-system`.
- Add a validated status request body (`0` or `1`).
- Add `OrgService.setStatus(Integer, Integer)` and implement a targeted live-org patch.
- Keep the existing generic update contract unchanged.

## Affected Stacks

- backend: `lumen-sev`

## Depends-On

- frontend: `org-status-toggle-web` (same cross-stack goal)

## Scope

- In scope: status endpoint, DTO validation, service method, integration tests.
- Out of scope: a new `system:org:status` permission/seed, removing `status` from
  the generic update request, frontend implementation.

## Success Criteria

- A valid status toggle returns the standard `200` envelope with `data: true`.
- Absent or out-of-range values are rejected before mutation.
- Existing organization CRUD behavior remains green.

## Constraints

- Java 11, Spring Boot 2.5.15, MyBatis-Plus.
- Use the existing `system:org:update` authority.

## Risks

- Duplicate status semantics with generic update → mitigate by keeping generic
  behavior for compatibility and documenting the new explicit command.
