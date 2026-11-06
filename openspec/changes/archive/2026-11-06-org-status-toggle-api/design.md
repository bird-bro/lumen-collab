# Design: org-status-toggle-api

## D1: Command-shaped endpoint

Add a focused `PUT /{id}/status` route beside the generic update route. The body uses
`status`, mirroring org model vocabulary and remaining explicit for audit and OpenAPI.

## D2: Reuse authorization

Use `system:org:update` on the method. A separate permission would require a menu
seed migration and is not needed for this interaction change.

## D3: Targeted persistence

Resolve the live org, validate `status`, and update only the status field. Do not alter
the generic partial-update method or its existing compatibility contract.

## Error handling

| case | result |
|:--|:--|
| missing/non-0/1 status | bean-validation rejection |
| org not live | existing A0400 organization-not-found behavior |
| no `system:org:update` | A0301 authorization denial |
