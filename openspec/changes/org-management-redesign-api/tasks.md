# Tasks: org-management-redesign-api

## 1. Migration and entities (red → green)

- [x] 1.1 Add the next Flyway migration to extend `org_info` with `code`, `short_name`,
  `status`, `province_code`, `city_code`, `district_code`, `address`, `contact`, `phone`,
  `email`, `logo_url`, `sort`, and `is_default`; add sibling-scoped live-code uniqueness and
  the default-campus constraint - verify: migration tests or repository tests prove fresh and
  upgraded databases both reach the target shape.
- [x] 1.2 Add `status` to `org_dep`; migrate every existing hospital root to one default
  campus and move directly attached departments to that campus without changing department
  ids/codes; replace the global live department-code index with a per-campus live-code index;
  normalize the original product seed to the Option B first-run placeholder details - verify:
  migration test shows the first-run hospital, default campus, and three seeded departments
  remain visible and enabled with their department codes unchanged.
- [x] 1.3 Update `OrgInfo`/`OrgDep` entities and extend request DTOs with nullable profile
  and status fields while retaining the existing endpoint bodies' compatible fields; add the
  department page-query DTO - verify: `./mvnw compile -q` passes with JDK 11.

## 2. Controlled organization service (red → green)

- [x] 2.1 Add failing service tests for tree/type invariants: only one live hospital root;
  hospital can create a campus; campus cannot create a child org; invalid or missing parent is
  rejected; server-derived `level` is persisted - verify: tests fail first, then pass.
- [x] 2.2 Add failing update tests: type is immutable, reparenting is rejected, profile fields
  update, status updates, the first-run hospital profile is editable, and `level` is not
  client-controlled - verify: tests pass after the service seam is updated.
- [x] 2.3 Add failing delete tests: hospital root and campus with live children/departments are
  rejected; an empty campus soft-deletes - verify: tests pass and existing logical-delete
  behavior remains.

## 3. Campus-scoped department service (red → green)

- [x] 3.1 Add failing tests that department create/update requires a live, enabled campus
  owner and rejects hospital/root owners - verify: tests fail first, then pass.
- [x] 3.2 Add failing duplicate-code tests for two campuses using the same code and for a
  duplicate within one campus; both campus instances persist, the same-campus duplicate is
  rejected with the A-class message - verify: tests pass.
- [x] 3.3 Add failing code-immutability tests proving an update may keep the code but cannot
  change it; add campus-reassignment validation that checks the target campus and duplicate
  code - verify: tests pass.
- [x] 3.4 Add failing service tests for the department page query: absent org owner returns
  all campuses, a campus owner filters to that campus, keyword matches code/name, status
  filters, and pagination is deterministic - verify: tests pass.
- [x] 3.5 Verify the existing department list and delete behavior remains compatible for
  existing callers - verify: existing department tests are updated only where ownership
  semantics changed and the suite passes.

## 4. API contract tests (red → green)

- [x] 4.1 Extend controller tests for org tree, create, update, and delete envelopes; invalid
  structure returns the descriptive A-class `msg` and leaves the tree unchanged - verify:
  tests pass.
- [x] 4.2 Extend controller tests for the department page endpoint with campus, keyword,
  status, and pagination parameters; preserve the existing `/list` contract - verify: tests
  pass.
- [x] 4.3 Verify `system:org:list` still gates the org family and `system:dep:list` still gates
  the department family, including `/org-deps/page`; no permission downgrade is introduced -
  verify: permission tests pass.

## 5. Full verification

- [x] 5.1 Build and test the backend reactor from `lumen-sev/` with JDK 11 - verify:
  `./mvnw compile -q` and `./mvnw test -q` pass.
- [x] 5.2 Walk every WHEN/THEN in the `api` and `auth-rbac` deltas and record test mappings -
  verify: all scenarios have passing tests or explicit mapped proof.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/api/spec.md` + `auth-rbac/spec.md` and the Chinese proposal
  brief happen at apply/archive time.
- Frontend change `org-management-redesign-web` starts real-backend work after this change
  lands; see `openspec/coordination/org-management-redesign.md`.
