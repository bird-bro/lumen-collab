# Coordination: org-management-redesign

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "org-management-redesign"`.

## Why

The current organization page models a generic, multi-root tree, while the department page
reuses that tree and can make any organization node the department owner. That is too loose
for the on-premise hospital product, where the deployment is scoped to one hospital and the
common growth path is multiple campuses. It is also poor UX when a large hospital has many
departments: a single tree that mixes campuses and departments does not scale for search,
status filtering, and pagination.

## Shared Design

### Concepts

- **Hospital** is the deployment root in the on-premise v1 model.
- **Campus** is a controlled child of the hospital and represents a physical
  branch/location, such as 总院区, 东院区, or 浦东分院.
- **Department** remains a separate `org_dep` row and MUST anchor to a live, enabled campus
  for clinical work in v1.
- Departments are not organization-tree nodes. They are managed on the department page.
- Group-of-hospitals modeling and external partner organizations are out of scope; this
  change intentionally avoids turning campuses into arbitrary organization nodes.

### Page architecture

- **Organization management** uses a dictionary-like split layout:
  - left: hospital profile card and edit action;
  - right: campus table with search/status filtering and campus CRUD/default actions.
- **Department management** keeps a separate page:
  - left: a hospital/campus tree used only as a retrieval/filter aid;
  - right: a department table with keyword/status/campus filtering and pagination.
- The department page's tree contains hospital and campus nodes only; it never renders
  departments. Selecting the hospital means "all campuses"; selecting a campus filters to
  that campus.
- Organization management does not manage departments directly. It may provide a jump into
  the department page with campus context, but department CRUD stays on the department page.

### Controlled tree invariants

- A live deployment has at most one hospital root.
- A hospital root may have campus children.
- A campus is a leaf in `org_info`; it has no nested `org_info` children.
- A department's `org_id` references a live, enabled campus.
- Organization type is immutable after creation.
- Organization reparenting is not exposed in v1.
- Deleting an organization is refused while it has live child organizations or departments.
- Department code is immutable after creation and unique among live departments within the
  same campus; the same code may exist on different campuses.

### Data contract

`org_info` gains operational/profile fields while preserving `id`, `up_id`, `level`,
`type`, `name`, logical delete, and audit columns:

- `code` - stable organization code, unique among live siblings (`up_id` scope).
- `short_name`
- `type` - `0` = hospital, `1` = campus in v1.
- `status` - `1` = enabled, `0` = disabled.
- `province_code` / `city_code` / `district_code` / `address`
- `contact` / `phone` / `email`
- `logo_url`
- `sort`
- `is_default` - identifies the migrated default campus; only one default campus per
  hospital.

`org_dep` keeps its existing identity and audit shape and gains `status` for administrator
filtering/lifecycle display. Department code uniqueness changes from a global live-code rule
to a per-campus live-code rule. Existing department ids and codes remain stable through
migration.

### Migration contract

- The existing seeded hospital remains the single hospital root and receives a stable code.
- Each existing hospital root receives one default campus (for example 总院区).
- Existing departments attached directly to the hospital root migrate to that default
  campus without changing their department codes.
- Existing department rows become enabled by default.
- Existing integrations that still reference only a department code continue to resolve,
  while new rules make campus ambiguity explicit.

### Initialization contract (Option B)

Fresh on-premise deployments do not require deployment-time hospital profile
configuration. The product seed resolves to an editable first-run hospital root
(`未命名医院`) and one default campus (`总院区`), each with deterministic bootstrap
codes. Administrators complete the real hospital/campus profile through the normal
organization edit dialogs. This does not add a generic root-creation control.

For existing custom hospital roots, migration preserves the operator's name and adds a
default campus. The original product seed may be normalized to the first-run placeholder
so new environments start in a clearly editable state.

### API contract

The existing `/api/v1/system/orgs` and `/api/v1/system/org-deps` paths remain. The change
is contract-tightening plus one additive department page endpoint:

- `GET /system/orgs/tree` returns the controlled hospital/campus tree with the new profile
  fields.
- `POST /system/orgs` creates only a hospital root when no live root exists, or a campus
  under that hospital; type/level are server-controlled.
- `PUT /system/orgs/{id}` updates profile fields and status; it rejects type changes and
  reparenting.
- `DELETE /system/orgs/{id}` is guarded by live child organizations and live departments.
- `GET /system/org-deps/list?orgId=` remains for existing clients: absent `orgId` returns
  all live departments; present `orgId` filters by the owning organization.
- `GET /system/org-deps/page` is added for the department table and supports optional
  `orgId`, `keyword`, `status`, `pageNum`, and `pageSize`. Absent `orgId` means all
  campuses; present `orgId` must be a campus.
- `POST/PUT /system/org-deps` enforce that the owning org is a live, enabled campus, enforce
  per-campus code uniqueness, and prevent changing an existing department code.
- Existing page-level permission gates remain: orgs use `system:org:list` and departments
  use `system:dep:list`.

## Decisions

- On-premise v1 is single-hospital, multi-campus; group mode is a later change - 2026-11-05.
- Campus is a controlled organization node type, not a department or arbitrary tree child -
  2026-11-05.
- Departments anchor to campuses so two campuses can each have a 放射科 - 2026-11-05.
- Existing endpoints are retained and tightened to reduce frontend and integration churn -
  2026-11-05.
- Existing-page UI edits skip the frontend-design prototype step per project workflow -
  2026-11-05.
- Initialization uses first-run editable seed data, then normal profile editing; normal
  organization management does not create hospital roots - 2026-11-06.
- Organization management uses a hospital profile card plus campus table; the generic
  left organization tree is not the organization page layout - 2026-11-06.
- The hospital/campus tree moves to department management as a filter; departments remain
  in a searchable, paginated table - 2026-11-06.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | org-management-redesign-api | proposing | - |
| frontend | org-management-redesign-web | proposing | - |

## Gate

Feature done = ALL registered changes verify PASS -> archive each.
