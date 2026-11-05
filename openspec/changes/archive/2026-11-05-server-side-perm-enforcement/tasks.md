# Tasks

## 1. Session permission pipeline (TDD)

- [x] 1.1 RED: extend the integration base with a perm-matrix helper (login as a user
      with a given role, call an endpoint, assert envelope); write failing tests -
      denied call yields `A0301`, admin passes, session of a pre-change vintage (no
      `permissions` field) denies. Confirm red.
      RED CONFIRMED 2026-11-05: 3 failures (POJO null authorities, cross-family leak
      200-vs-A0301, perm-less 200 + full user list); pass-through guards green as
      expected pre-implementation.
- [x] 1.2 GREEN: session permissions were ALREADY populated (`AuthServiceImpl` passes
      `permissionSet(userId)` into the 3-arg `UserLogin` constructor - proposal fact
      corrected); the only gap was `getAuthorities()` -> now maps permissions to
      `SimpleGrantedAuthority`s (empty-safe). Matrix: 3 red -> 2 red (deny tests wait
      for task 2.2 method security; POJO test green).
- [x] 1.3 Record coordination-doc adaptation (11): the one `UserLogin.getAuthorities()`
      body change (additive-first deviation, rationale D1). (8/9/10 were already taken
      - the proposal read a stale spec comment.)

## 2. Enforcement wiring (TDD)

- [x] 2.1 RED: extend the matrix - for each of the seven families, a perm-less
      authenticated call rejects `A0301` and the admin call succeeds; city endpoints
      succeed for any authenticated user. Confirm red.
      Matrix authored in full during 1.1 (families + cross-family + city + 203);
      after 1.2 the remaining 2 failures (family denies) ARE this task's red state.
- [x] 2.2 GREEN: class-level `@PreAuthorize` on the seven controller families (8 files,
      dict family covers both controllers); lumen-owned `AuthDenyAdvice`
      (@RestControllerAdvice, HIGHEST_PRECEDENCE) renders the `A0301` envelope over
      HTTP 200. CORRECTIONS vs design draft: the copied SecurityConfig ALREADY had
      `@EnableGlobalMethodSecurity` (a new config class double-registers
      `metaDataSourceAdvisor` - removed), and the deny path is an advice, not a
      registered handler bean. Matrix 6/6 GREEN.
- [x] 2.3 Full-suite regression: every existing test class stays green
      (`./mvnw -pl lumen-system -am test`); fix any test that relied on unguarded
      endpoints by granting its fixture role - never by weakening the guard.
      PASS: 71/71 (65 existing + 6 matrix), BUILD SUCCESS - no existing test relied on
      unguarded endpoints (non-admin flows only touch /me).

## 3. Spec, docs, closure

- [x] 3.1 Verify deltas against the landed behavior (`/opsx:verify`), write verify.md.
      PASS - verify.md (backend L1/L2/L3 = PASS/8-8/PASS, 71/71; lumen-web SKIP).
- [x] 3.2 Write the Chinese proposal intro `docs/proposals/server-side-perm-enforcement.zh.md`.
      Done at propose time per the zh-intro convention.
- [x] 3.3 `/opsx:archive` — sync the auth-rbac + errors deltas into the main specs.
