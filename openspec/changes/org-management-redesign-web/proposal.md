# Proposal: org-management-redesign-web

## Why

The current organization page is a generic tree with only a name and an always-available
root-creation affordance. It does not show hospital/campus identity, does not restrict
campus structure, and does not provide the operational profile administrators need for an
on-premise hospital deployment. Reusing that full tree for department maintenance also does
not scale: a large hospital can have many departments, and departments should not be edited
inside an organization tree.

## What Changes

- Rewrite the existing organization page in place as a dictionary-like layout:
  - left: hospital profile card with an edit action;
  - right: campus table with search/status filtering and campus actions.
- Provide create/edit dialogs for the applicable node type:
  - hospital: code, name, short name, status, province/city/district, address, contact,
    phone, email, logo URL, sort;
  - campus: the same profile fields plus default-campus handling.
- Hide normal root creation for the on-premise single-hospital model.
- Support Option B first-run initialization: render the seeded editable hospital/default
  campus, show a completion hint for bootstrap placeholders, and complete details through the
  normal edit form.
- Restrict campus creation to the hospital root.
- Prevent organization reparenting in the UI and surface backend structural rejections.
- Require delete confirmation and show backend guards for live children/departments.
- Keep organization and department page permissions unchanged.
- Redesign the existing department page as a searchable table:
  - left: hospital/campus tree used only as a filter, with the hospital meaning all campuses;
  - right: department table with keyword/status/campus filtering and server-side pagination.
- Require department creation/editing to select a live, enabled campus owner; show the
  campus context in the dialog and the campus in cross-campus results.
- Keep department code immutable after creation and support the same code on different
  campuses.
- Add department status handling and surface backend duplicate/owner errors.
- Use the typed API clients, including the additive department page endpoint, and the real
  backend through the Vite proxy; no mock layer.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `web-system-console`: the generic org tree page becomes hospital profile plus campus-table
  management, and department management becomes a table backed by the hospital/campus filter
  tree.

## Impact

- `lumen-web`: `src/pages/system/orgs/index.tsx`, shared hospital/campus presentation,
  `src/pages/system/deps/index.tsx`, `src/api/orgs.ts`, `src/api/org-deps.ts`,
  `src/api/types.ts`, and related tests.
- Existing-page edit: the `frontend-design` prototype step is skipped per workflow.
- Depends on `org-management-redesign-api` for the tightened backend contract and the
  department page endpoint.
- Spec sync on archive: `openspec/specs/web-system-console/spec.md`.
