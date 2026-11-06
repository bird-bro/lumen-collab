# Proposal: org-status-toggle-web

## Why

The `/system/orgs` campus status switch should match the `/system/users` interaction
and use the new explicit status command rather than a generic profile update.

## What Changes

- Add a typed API client for `PUT /system/orgs/{id}/status`.
- Switch the campus-table status control to call it with `0 | 1`.
- Keep the profile drawer's existing partial-update behavior.

## Affected Stacks

- frontend: `lumen-web`

## Depends-On

- backend: `org-status-toggle-api` (same cross-stack goal)

## Scope

- In scope: API client, status switch call, tests.
- Out of scope: changing profile drawer fields or introducing a new permission.

## Success Criteria

- Toggling a campus switch calls the dedicated status endpoint.
- A switched-off row reflects the refreshed tree state.

## Constraints

- React 18, TypeScript, Ant Design 5.
- No mock layer; use the typed client through the real API seam.

## Risks

- Backend not deployed → the web build should target the real backend only after
  `org-status-toggle-api` lands.
