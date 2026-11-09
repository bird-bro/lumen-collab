# Design: role-type-dict-api

## Context

`role_info.type` is a plain int column (V1: `NOT NULL DEFAULT 11` — a historical
quirk left untouched; the operative default for new rows is the frontend's
initialValue 10). The vocabulary semantics live in the copied
`common/Constants/RoleConstants.java` (`TYPE_GLOBAL=10`, `TYPE_RCP=11`) and —
until now — as a hardcoded frontend label map. The dict model is ready as the
vocabulary home: `dict_catal` (V12 shape: unique `name`, no code column) +
`dict_detail` (V9 shape: numeric `code`, single-text `value`, `enable`, `sort`).
Existing dict endpoints are CRUD + `GET /dicts/detail/list?caId=` gated behind
the dict page perm — unusable by role managers. Decisions D1-D6 are recorded in
`openspec/coordination/role-type-dict.md`.

## Goals / Non-Goals

**Goals:**
- V23 seed: 「角色类型」 catalog + the two RoleConstants-mirroring items
- `GET /api/v1/system/dicts/items/{catalogName}` — slim `{code, value}[]`,
  authenticated-only, sort-ordered, unknown-name → empty array
- Zero `common`/`framework` edits; `roles/list` DTO untouched (D3)

**Non-Goals:**
- No `type ∈ dict codes` validation on role create/update (D5)
- No server-side label join into `RoleItemDto` (D3 — frontend resolves)
- No refactor of the existing dict management endpoints
- No V1 column-default change

## Decisions

- **Controller placement**: new endpoint in `DictDetailController` (the family it
  belongs to), implemented in `DictDetailManageService`. The controller's
  class-level `@PreAuthorize("hasAuthority('system:dict:list')")` would 403 the
  role managers, and a method with NO annotation inherits that class gate — so
  the method carries its own `@PreAuthorize("isAuthenticated()")`: explicit,
  self-documenting, overrides the class gate, and keeps the authenticated-only
  contract (D2; the same override pattern the button-perm pins already use per
  server-side-perm-enforcement D2).
- **Slim response DTO**: `DictItemDto { Integer code; String value; }` — NOT the
  entity. The endpoint is a label-lookup projection; exposing
  tag/flag/color/css/audit columns invites coupling. Same layering rationale as
  `RoleItemDto` (explicit field set = the guarantee).
- **Name lookup query**: `dict_catal` by `name = ?` AND `enable = 1` (unique per
  V12) → collect live enabled details of that `caId` ordered by `sort` asc;
  unknown/disabled catalog → empty list (D2: never an error).
- **Service method**: `DictDetailManageService.listEnabledItemsByName(String
  name)` — two queries (catalog then details) or one join; two queries preferred
  (no mapper XML churn, both are cheap indexed lookups).
- **Seed (V23)**: follows the V6 house style — explicit ids, `ON CONFLICT DO
  NOTHING` guards, header comment recording provenance (RoleConstants lineage,
  ids clear of V6's 1001-1007 catalog band and +100000 detail band). Catalog id
  1020, detail ids 1020001/1020002.
- **Tests** (TDD, per the house red→green): integration tests on the new endpoint
  — known catalog (order + slim shape + disabled excluded), unknown catalog
  (200 + empty), no-perm-holder 200 vs unauthenticated 401/403 — plus a
  Flyway-s replay check of the V23 seed (catalog + 2 items + idempotence) if the
  test harness replays migrations; otherwise a data assertion against the
  shared test DB is recorded in verify.md instead.
- **Enable field (D7)**: V24 `ALTER TABLE role_info ADD COLUMN enable integer
  NOT NULL DEFAULT 1` — no backfill statement needed (default covers existing
  rows). `RoleInfo` entity + `RoleItemDto` gain the `enable` field (list payload
  only; create/update bodies stay as-is — a role is born enabled). Toggle
  endpoint `PUT /{id}/enable` in `RoleController` mirrors
  `OrgController`'s status toggle verbatim in shape: body `{"enable": 0|1}`
  validated strictly (other values rejected without mutation), requires
  `system:role:update` (method-level pin on top of the family page gate, same
  as the orgs toggle), returns `data: true`. Deliberately NO behavioral wiring
  (no login/perm recalculation reads `enable` yet) — coordination D7 records
  that boundary; the column and endpoints are the deliverable.

## Risks / Trade-offs

- **Ungated read surface**: the endpoint leaks classification vocabulary to any
  authenticated user — accepted: the same class already covers city reference
  data, and dict values are display labels, not secrets.
- **Name-addressed catalogs are i18n-fragile**: renaming the catalog breaks
  consumers. Accepted for now (V12 made name the only handle); the frontend
  fallback (raw code) bounds the blast radius to a label regression, and the
  coordination doc records the name as a stable contract.
- **Soft type validation (D5)**: a role row can carry a type absent from the
  dict → the console renders the raw number. Bounded by the frontend Select;
  revisiting requires a backend change, recorded as a non-goal.
