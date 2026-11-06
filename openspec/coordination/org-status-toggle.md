# Coordination: org-status-toggle

> Cross-stack parent. Shared design/decisions + per-stack change registry.

## Why

`/system/orgs` now uses a status switch in the campus table. To mirror the user
management interaction and make lifecycle toggling an explicit command, the switch
should call a dedicated status endpoint rather than submit a partial org-profile update.

## Shared Design

- Endpoint: `PUT /api/v1/system/orgs/{id}/status`.
- Body: `{ "status": 0 | 1 }`; response: standard envelope with `data: true`.
- Semantics: explicit org status command; validation rejects absent and non-0/1 values.
- Permission: keep the existing org mutation authorization (`system:org:update`).
  No new F node or seed migration is introduced in this change.
- Backward compatibility: `PUT /system/orgs/{id}` continues to accept `status` for
  the profile editor and existing callers.

## Decisions

- Mirror user management at the HTTP action level while reusing the existing org
  update permission — user direction 2026-11-06.

## Change Registry

| Stack | Change | Status |
|:--|:--|:--|
| backend | `org-status-toggle-api` | implemented |
| frontend | `org-status-toggle-web` | implemented |

Gate: both registered changes verify PASS before archive.
