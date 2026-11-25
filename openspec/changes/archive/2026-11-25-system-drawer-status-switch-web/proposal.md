# Proposal: system-drawer-status-switch-web

## Why
Binary 启停 status currently appears as a Select in create drawers even though a new record should start enabled; the control also does not match the settled number-rule drawer pattern.

## What Changes
- Organization, department, and room drawers remove the binary status Select.
- Create drawers hide status and submit the record as enabled.
- Edit drawers render status as a Switch in the common boolean-switch row and persist through the page's existing save path.

## Affected Stacks
- single-stack: lumen-web

## Depends-On
- none - standalone

## Scope
- In scope: `/system/orgs`, `/system/deps`, and `/system/room` drawer lifecycle status controls.
- Out of scope: multi-state device status, status APIs, backend payloads beyond the existing fields, table status switches.

## Success Criteria
- Create drawers contain no status Select or Switch and create enabled records.
- Edit drawers contain no status Select; the status control is a Switch.
- Existing edit/create payloads remain API-compatible.

## Constraints
- Follow the number-rule / common boolean-switch row pattern.
- Keep dedicated table status endpoints unchanged.

## Risks
- Room status has a dedicated endpoint → use that endpoint from the edit switch without expanding the room update API.
