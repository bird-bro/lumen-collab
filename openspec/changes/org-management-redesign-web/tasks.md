# Tasks: org-management-redesign-web

## 1. API clients and types (red → green)

- [x] 1.1 Extend organization types and request bodies with the backend profile fields while
  preserving existing `id`/`upId`/`level`/`type`/`name` compatibility - verify: typed client
  contract tests pass.
- [x] 1.2 Add department status fields, the paginated query parameters, and the typed page
  endpoint while preserving the existing list endpoint - verify: typed client contract tests
  pass.
- [x] 1.3 Enforce the campus-owner and immutable-code contracts in request/type
  documentation and validation helpers - verify: `pnpm build` and existing client tests pass
  after intentional updates.

## 2. Organization page rewrite (red → green)

- [x] 2.1 Rewrite failing render tests for a left hospital profile card and right campus
  table with search/status filtering, code, default-campus state, and no generic
  root-creation control - verify: old generic-tree tests are replaced; new tests fail first,
  then pass.
- [x] 2.2 Implement campus actions: create, edit, set default, and delete an empty campus;
  render backend delete guards without changing the table until success - verify:
  selection/action tests pass.
- [x] 2.3 Implement type-aware create/edit dialogs for hospital bootstrap and campus profile
  fields; hide type/reparent controls on edit and prefill current values - verify: form tests
  pass.
- [x] 2.4 Wire create/update/delete through the real API clients, refresh the campus data on
  success, keep dialogs open on A-class rejection, and show backend messages - verify:
  mocked-seam tests pass.
- [x] 2.5 Gate mutation affordances with `system:org:list` and keep the hospital/campus views
  read-only without it - verify: permission tests pass.
- [x] 2.6 Render the Option B first-run hospital/default-campus state and completion hint;
  clear the hint after a successful hospital profile update while keeping root creation hidden
  - verify: render/update tests pass.

## 3. Department page redesign (red → green)

- [x] 3.1 Rewrite failing render tests for a left hospital/campus filter tree and a right
  department table; prove the tree never renders departments and hospital-root selection
  queries all campuses - verify: old generic-tree department tests are replaced; new tests
  fail first, then pass.
- [x] 3.2 Implement keyword/status filtering and server-side pagination; show campus names in
  cross-campus results and update the query when a campus is selected - verify: table/query
  tests pass.
- [x] 3.3 Implement create/edit with a live, enabled campus owner; prefill the selected
  campus when applicable and require an explicit campus when the hospital root is selected -
  verify: form tests cover both filter states.
- [x] 3.4 Keep the department code immutable after creation and surface same-campus duplicate
  errors on the code field; allow guarded campus reassignment - verify: tests pass.
- [x] 3.5 Verify departments with the same code under different campuses are listed separately
  with the correct campus context - verify: tests pass.
- [x] 3.6 Keep `system:dep:list` gating for department mutations, including the page endpoint
  - verify: permission tests pass.

## 4. Full verification

- [x] 4.1 Run the web type check/build and test suite in `lumen-web/` - verify: `pnpm build`
  and `pnpm test` pass.
- [x] 4.2 With `org-management-redesign-api` deployed, walk every WHEN/THEN in the
  `web-system-console` delta through the Vite proxy - verify: observed behavior matches all
  scenarios.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/web-system-console/spec.md` and the Chinese proposal brief
  happen at apply/archive time.
- Backend-first sequencing: real-backend walkthrough starts only after
  `org-management-redesign-api` lands.
