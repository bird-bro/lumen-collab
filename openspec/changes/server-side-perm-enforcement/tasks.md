# Tasks

## 1. Session permission pipeline (TDD)

- [ ] 1.1 RED: extend the integration base with a perm-matrix helper (login as a user
      with a given role, call an endpoint, assert envelope); write failing tests -
      denied call yields `A0301`, admin passes, session of a pre-change vintage (no
      `permissions` field) denies. Confirm red.
- [ ] 1.2 GREEN: `AuthServiceImpl` populates `userLogin.setPermissions(permsSet)` before
      the session cache; `UserLogin.getAuthorities()` maps permissions → authorities
      (empty-safe). Confirm green.
- [ ] 1.3 Record coordination-doc adaptation (8): the one `UserLogin.getAuthorities()`
      body change (additive-first deviation, rationale D1).

## 2. Enforcement wiring (TDD)

- [ ] 2.1 RED: extend the matrix - for each of the seven families, a perm-less
      authenticated call rejects `A0301` and the admin call succeeds; city endpoints
      succeed for any authenticated user. Confirm red.
- [ ] 2.2 GREEN: lumen-owned `MethodSecurityConfig` (`@EnableMethodSecurity`) +
      class-level `@PreAuthorize` on the seven controller families; lumen-owned
      `AccessDeniedHandler` emitting the unified `A0301` envelope (HTTP 200) registered
      for access denials. Confirm green.
- [ ] 2.3 Full-suite regression: every existing test class stays green
      (`./mvnw -pl lumen-system -am test`); fix any test that relied on unguarded
      endpoints by granting its fixture role - never by weakening the guard.

## 3. Spec, docs, closure

- [ ] 3.1 Verify deltas against the landed behavior (`/opsx:verify`), write verify.md.
- [ ] 3.2 Write the Chinese proposal intro `docs/proposals/server-side-perm-enforcement.zh.md`.
- [ ] 3.3 `/opsx:archive` — sync the auth-rbac + errors deltas into the main specs.
