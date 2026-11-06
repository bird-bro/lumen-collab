# Design: org-status-toggle-web

## D1: Consistent control

Keep the AntD Switch with 启/停 labels, disabled without `system:org:update`.

## D2: Explicit client

Expose `setOrgStatus` as a typed API function and use it only for the table switch.
The profile drawer continues to submit the whole editable profile via `updateOrg`.
