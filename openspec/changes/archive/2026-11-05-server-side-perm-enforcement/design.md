# Design

## Context

The security chain is a half-built pipeline: `JwtAuthenticationTokenFilter` already puts
`loginUser.getAuthorities()` into the `SecurityContext`, and `UserLogin` already carries a
`permissions` field - but `getAuthorities()` returns null and nothing ever calls
`setPermissions`. The perms strings exist at login time (`MenuTreeService.permsSet`).
`ExceptionEnum.ERROR_A0301("A0301", "访问未授权")` already exists in the copied enum.
See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- End-to-end server-side page-perm enforcement on lumen-system with minimal copied-file
  surface (one method body) and lumen-owned wiring.
- TDD deny/pass matrix proving: no-token → "203", wrong-perm → "A0301", admin → 200.

**Non-Goals:**
- Button-level enforcement and F-node seeding (workstation-context territory; recorded
  roadmap only).
- Department-service enforcement (radiology/us/endo do not exist yet; the pattern lands
  with them).
- Superadmin wildcard (`*:*:*`) - the explicit `role_menu` grant ruling stands.
- Frontend changes (it already gates on the same strings).

## Decisions

- **D1 — Populate at login, derive in the POJO.** `AuthServiceImpl` sets
  `userLogin.setPermissions(permsSet)` right before `TokenService` caches the session
  (lumen-owned file, free edit). `UserLogin.getAuthorities()` maps `permissions` →
  `SimpleGrantedAuthority` list (empty list when null/empty - returning null today can
  NPE Spring internals). That is the ONE copied-line edit; recorded as coordination
  adaptation (8). Alternative rejected: building authorities in the filter (another
  copied-file edit for no gain).
- **D2 — Method security: already enabled by the copied chain; annotate only.**
  CORRECTED during apply: the copied `SecurityConfig` ALREADY carries
  `@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)` - method
  security was never the missing piece (only the authorities were, D1). So NO new
  config class: class-level `@PreAuthorize("hasAuthority('...')")` on the seven
  controller families is the whole wiring. Class-level (not per-method) matches the
  page-perm granularity; city controller stays un-annotated. (A first implementation
  added a lumen-owned `MethodSecurityConfig`, which double-registered
  `metaDataSourceAdvisor` and broke the context - removed; recorded here so the
  correction is auditable.)
- **D3 — Deny advice mirrors the entry-point transport.** Lumen-owned
  `AuthDenyAdvice` (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`) catching
  `AccessDeniedException` from the method-security AOP layer and writing the unified
  envelope with `A0301` over HTTP 200, mirroring the `"203"` entry-point ruling. The
  order matters: the copied `GlobalExceptionHandler` (unordered) carries an
  `Exception.class` catch-all that would otherwise win the advice race and mask the
  denial as a 500. Filter-level denials are impossible here (method security throws
  inside the DispatcherServlet), so no `SecurityConfig` handler registration is needed.
- **D4 — Stale sessions deny by default.** Sessions cached before this change lack
  `permissions` → empty authorities → deny. Short-lived test-server sessions make this
  a non-event; re-login fixes. No migration/back-compat shim.

## Risks / Trade-offs

- The integration suite's non-admin flows (AuthRoleMenuTests users) only call `/me` -
  unaffected; any test calling gated endpoints with a menu-less user now denies BY
  DESIGN and gets its own assertion in the deny/pass matrix.
- `getAuthorities()` is on the hot path of every request - a Set→List map per request;
  negligible, and cached with the session anyway.
- If a future role needs city data but not dict management, the un-annotated city
  controller is the escape hatch already in place.
