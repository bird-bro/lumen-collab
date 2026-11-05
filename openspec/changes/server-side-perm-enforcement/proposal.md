# Proposal

## Why

The permission model is data-complete (menu_info `perms` strings flow to the session
and gate the frontend via `usePerm`) but **the backend enforces nothing**: any valid
token can call every `/api/v1/system/**` endpoint regardless of the caller's perms.
The frontend gates are cosmetic against a direct API call — unacceptable for a
multi-role hospital deployment, and the gap was objectively confirmed on 2026-11-05
(0 `@PreAuthorize`/`hasAuthority` checks across lumen-system; `UserLogin.getAuthorities()`
returns null; nothing populates `UserLogin.permissions`). The 随访 reference has the
same gap; lumen deliberately goes one step beyond it.

## What Changes

- **Session perms population**: the login flow stores the caller's permission set
  (the `perms` strings already assembled from the granted menu rows) into
  `UserLogin.permissions` before the session is cached in Redis.
- **Authorities wiring**: `UserLogin.getAuthorities()` maps `permissions` to Spring
  Security `GrantedAuthority`s (empty list, never null) so the copied
  `JwtAuthenticationTokenFilter` chain carries real authorities.
- **Server-side enforcement**: every lumen-system controller family (except login/me)
  requires its page-level permission string via `@PreAuthorize("hasAuthority('...')")`:
  users→`system:user:list`, roles→`system:role:list`, menus→`system:menu:list`,
  orgs→`system:org:list`, deps→`system:dep:list`, dicts→`system:dict:list`,
  logs→`system:log:list`. City reference queries stay authenticated-only (reference
  data any future role may read). Method-security enablement lives in a lumen-owned
  configuration class (no framework file edits beyond the one-line `getAuthorities`
  body fix, recorded in the coordination adaptation list).
- **Denied-response contract**: a lumen-owned `AccessDeniedHandler` renders the unified
  envelope with a dedicated permission-denied code, added to the errors spec.
- **Button-level roadmap (documented, NOT implemented)**: per-button F-node gating
  (the 随访 recipe — F nodes + per-button perms + directive-level hiding) is recorded
  as the follow-up when workstation job roles land; this change deliberately stays at
  page-level granularity.

**BREAKING** (behavioral, intentional): endpoints previously callable with any valid
token now return 403 unless the caller's session carries the family's permission
string. Existing seeds are unaffected (the administrator role owns all menus).

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `auth-rbac`: the Roles and Menu-Button Permissions requirement gains server-side
  enforcement scenarios (session perms population, per-family rejection, admin
  pass-through).
- `errors`: a permission-denied code joins the error catalog (403 envelope shape).

## Impact

- `lumen-sev/common` — one method body (`UserLogin.getAuthorities()`), recorded as a
  coordination-doc adaptation (additive-first deviation, user ruling 2026-11-03 allows
  recorded OPS-lineage adjustments).
- `lumen-sev/lumen-system` — login service populates session perms; a new
  method-security config + access-denied handler (lumen-owned files); `@PreAuthorize`
  on the seven controller families; new TDD test class (deny/pass-through matrix).
- Frontend: none (it already gates on the same strings).
- Specs: auth-rbac + errors deltas; coordination doc gains adaptation (8).
- Deployment: Redis-cached sessions created BEFORE this change lack the `permissions`
  field — after deploy, existing sessions degrade to empty perms (deny) until re-login;
  acceptable on the test server (sessions are short-lived), noted in design.
