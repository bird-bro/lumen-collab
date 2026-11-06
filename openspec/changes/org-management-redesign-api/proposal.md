# Proposal: org-management-redesign-api

## Why

`org_info` currently supports an arbitrary multi-root hierarchy, and the console can create
a second root with nothing more than a name. That model does not express the on-premise
hospital deployment's main scaling scenario: one hospital with multiple campuses/branches.
It also allows departments to attach directly to the hospital, which makes department
identity ambiguous. The current department list has no keyword/status/campus-filtered
pagination, which becomes unusable in large hospitals with many departments.

## What Changes

- Tighten the organization tree into a controlled **hospital -> campus** model for the
  on-premise v1 deployment.
- Extend `org_info` with stable organization and profile fields: `code`, `short_name`,
  `status`, province/city/district, address, contact, phone, email, logo URL, sort, and
  `is_default`.
- Define organization types for v1: hospital (`0`) and campus (`1`); campus nodes are
  leaves and cannot contain child organizations.
- Enforce at most one live hospital root; keep the seeded hospital as that root.
- Initialize fresh deployments with an editable first-run hospital root and default campus
  (Option B); administrators complete profile details through the existing update flow.
- Guard organization updates: type is immutable, reparenting is rejected, and level remains
  server-derived.
- Guard organization deletes: refuse deletion while live child organizations or live
  departments exist.
- Migrate each existing hospital root to have one default campus and move its directly
  attached departments to that campus.
- Tighten department ownership and uniqueness: a department must belong to a live, enabled
  campus, and its live code is unique within that campus.
- Make the existing department code immutable after create and add `status` to departments.
- Add a paginated department query with keyword, status, and campus filters for the
  department management table while preserving the existing list endpoint.
- Preserve the existing endpoint paths, response envelope, audit behavior, and page-level
  permissions.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: the org and department endpoint contracts gain profile fields, controlled-tree
  validation, and a department table query while retaining the current core routes.
- `auth-rbac`: the generic multi-root organization model is replaced by the controlled
  hospital/campus model and campus-anchored department ownership.

## Impact

- `lumen-system`: `OrgInfo`/`OrgDep` entities, org/department request DTOs, a department
  page-query DTO, org/department services and controllers, and a Flyway migration (V16).
- Existing public service method signatures remain stable where possible; behavioral
  tightening is implemented in the existing seams or through additive overloads.
- The department page endpoint is additive; existing department callers continue to use
  `GET /system/org-deps/list`.
- Spec sync on archive: `openspec/specs/api/spec.md` and
  `openspec/specs/auth-rbac/spec.md`.
- Shared design source: `openspec/coordination/org-management-redesign.md`.
