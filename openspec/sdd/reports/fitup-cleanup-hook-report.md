# Report - test-fixture DB cleanup hook (fitup-cleanup-hook, post-archive follow-up ruled 2026-11-05)

**Status: DONE** · Commit `1dc6c37` on `dev` (`✨ feat(system): run-scoped test admin + residue sweep in the shared test DB`) · Brief: `openspec/sdd/reports/fitup-cleanup-hook-brief.md`

## What was built (per the brief's design, test-only)

`AuthIntegrationTestBase` (the seam for 21 suites) now owns two lifecycle hooks:

1. **`@BeforeAll ensureRunAdminIdentity`** - clones the seeded `admin` into
   `RUN_ACCOUNT = itadmin_<epoch-millis>` (one per JVM/fork) via plain JDBC
   (`AuthTestEnv.load()` params, URL `?currentSchema=system,public`, identical
   resolution to `teamTestServerProperties`): guard-SELECT for a live `RUN_ACCOUNT`
   row (reuse), clone `INSERT ... SELECT ?, password, 'it-admin run fixture' FROM
   system.user_info WHERE username='admin' AND deleted=0` (same bcrypt hash), bind
   role 1 (the seeded 系统管理员 grant set). Explicit `IllegalStateException` when no
   live seeded-admin row exists. Idempotent and self-healing: a mid-JVM sweep of an
   earlier class soft-deletes the run account, later classes re-clone (the partial
   unique index allows it) - proven live in the verification run (clone ids 692 etc.).
2. **`@AfterAll sweepRunResidue`** - one JDBC transaction (`commit` / `rollback` on
   failure, failure = explicit `IllegalStateException`, never a silent skip), in
   dependency-safe order: `public.log_sign` + `public.log_info` (run account +
   `ACCOUNT_PREFIXES` + `ua-probe`; no `update_time` touch - the log tables have no
   audit block, verified against V13), `system.user_role` (bindings of run/fixture
   users; inner select deliberately without a `deleted` filter so bindings of
   already-dead fixture users are caught too), `system.user_info`, `system.role_menu`,
   `system.role_info` (`ROLE_NAME_PREFIXES`). The four `system` tables also get
   `update_time = CURRENT_TIMESTAMP`.
3. **`runAdminLoginBody()`** (package-private) and **`public static Long runUserId()`**
   for the suites. `Constants.APP_CODE` untouched. Underscores in LIKE patterns are
   escaped (`ESCAPE '\'`) so families match exactly their shapes.

### Design deviation, probe-justified (documented in the class javadoc)

The brief asserted "`@BeforeAll` in a Spring Boot test qualifies [as after context
refresh]". A throwaway ordering probe (deleted after use; bytecode inspection of
spring-test 5.3.33's `TestContextManager.beforeTestClass` confirms) proved a static
`@BeforeAll` runs BEFORE the context refresh on this train - on a fresh database the
first class's attempt would precede Flyway. Kept the brief's exact `@BeforeAll`
signature, but made the ensure idempotent and re-attempted lazily at first use
(`runAdminLoginBody()` / `runUserId()` - both strictly post-refresh). Deferred, never
silently skipped; on today's pre-migrated team DB the first attempt succeeded
immediately (0 "deferred" lines in the run log).

## Migration list (grep-exhaustive; `"admin"` + `admin123` + `uid/create_by = 1`)

Login-body sites migrated (11 files, 13 bodies - all mechanical):

| File | Site |
|:--|:--|
| AuthAuditIdentityTests | `postJson(LOGIN_URL, ...)` login |
| AuthDictCityTests | `adminToken()` (its own `HttpEntity` composition) |
| AuthDictCrudTests | `adminToken()` |
| AuthLogQueryTests | `adminToken()` |
| AuthOperationLogTests | `adminToken()` |
| AuthOrgCrudTests | `adminToken()` |
| AuthOrgDepTests | `adminToken()` |
| AuthRoleMenuTests | `adminToken()` (single admin site; the four fixture-user login bodies stay) |
| AuthSignLogQueryTests | `adminToken()` -> `loginToken(RUN_ACCOUNT, ADMIN_TEST_PASSWORD)` |
| AuthSignLogTests | `adminToken()` (the `postJsonWithUa` admin body; fixture `b2sign_`/`b2out_` bodies stay) |
| AuthUserAgentResilienceTests | `adminToken()` |
| AuthUserManagementTests | `adminToken()` |
| AuthPermEnforcementTests | `adminToken()` + the `loginToken` password ternary (`RUN_ACCOUNT.equals(username) ? ADMIN_TEST_PASSWORD : ...`) |

KEPT on the real admin (per brief): `AuthLoginTests` (tests the seeded-admin contract),
`AuthSeedMigrationTests` (hash assertion, no login). NOT touched:
`AuthMenuManageTests` - untracked file owned by the parallel session's in-flight
menu-management work (its `adminToken()` still inherits the hook the moment it is
migrated; until then it adds real-admin residue like every pre-hook suite).

Operator-identity assertions re-pointed to `runUserId()` (5 sites, 4 files): the
brief-ruled `AuthAuditIdentityTests` `createBy == 1`, plus three numeric-id siblings
the brief did not name - `AuthDictCrudTests` (`create_by == 1` on dict_catal),
`AuthOperationLogTests` (two `log_info` polls keyed `uid = 1`),
`AuthUserAgentResilienceTests` (`log_info` count keyed `uid = 1`). None depends on the
literal username `"admin"`; all preserve the same contract (operator identity
propagates to the audit/log columns) exactly like the brief's ruled transformation,
so I judged them inside the ruling rather than BLOCKED. Listed here for the
coordinator to veto - a revert is 5 lines.

## Verification

- **Suite**: `./mvnw test` (reactor) green - 81/81, 0 failures/errors/skipped
  (21 classes). The brief's 74 baseline + 7 in-flight tests from the parallel
  session (`AuthMenuManageTests` 6 + `menuWriteFamilyIsPageGated` 1); my change adds
  zero tests, as ruled. The run log shows the hook working live (e.g. final sweep
  `[system-test-hook] swept run residue - log_sign=8 log_info=8 user_role=1
  user_info=1 role_menu=0 role_info=0` - the 1/1 being the run admin's own binding
  and row, re-cloned by the next class).
- **Post-run DB verification** (brief's SQL via docker psql, params sourced from the
  gitignored env file, no values printed):
  - `itadmin_users_live = 0`, `family_users_live = 0`, `family_roles_live = 0` - the
    hook's contract holds.
  - `log_sign_live = 147`, `log_info_live = 131` - larger than the anticipated small
    AuthLoginTests tail (see Concerns). By construction the live remainder can only
    sit under accounts/uids OUTSIDE the swept namespace. The deferred per-account
    attribution query (lock-queued during the run, completed afterwards) proves it:
    `log_info` live rows are `uid=1` ONLY (131, the real admin); `log_sign` live rows
    are `admin` (152 - pre-hook legacy from every suite's earlier real-admin logins
    plus AuthLoginTests' fresh tail) plus 5x `menuperm_u_<ts>` - the parallel
    session's new `menuWriteFamilyIsPageGated` test, whose prefix sits outside the
    brief's frozen `ACCOUNT_PREFIXES` (Concern 4).

## Self-review

- Scope: 14 committed files, all under `lumen-system/src/test/`; zero prod-code
  changes. The parallel session's in-flight edits (`MenuController.java`,
  their `AuthPermEnforcementTests` test + `AuthMenuManageTests`, lumen-web,
  openspec docs) are NOT in the commit - `AuthPermEnforcementTests` was staged
  hunk-selectively (`git apply --cached`) so only my `adminToken`/`loginToken`
  lines are committed and their new test stays unstaged.
- House rules respected: no `.env-test.local` values printed or committed; rcp-test
  untouched; no applied migration rewritten; Chinese role literals match the existing
  fixture names in AuthRoleMenuTests/AuthPermEnforcementTests.
- Cross-suite safety: sweep runs after EVERY class (21 sweeps per run), continuously
  cleaning; it only touches the test-owned namespace, so keys/seed rows
  (`dict_city` SEED_TOTAL, menus, role 1 grants) are out of reach; assertions in
  migrated suites are marker-keyed, never global counts.
- Residue is swept transactionally, but Redis token state from test logins is out of
  scope (the brief covers the DB only).

## Concerns / handoffs

1. **Pre-hook `admin` backlog (152 sign / 131 info live rows, all `admin`/uid 1 -
   attribution query confirmed)**: the standing hook keeps everything at zero EXCEPT
   real-admin rows, which it must never touch. AuthLoginTests alone now regrows only
   ~3-4 sign rows/run. To reach a fully clean baseline, one manual V5-style sweep of
   `account='admin'` / `uid=1` log rows remains a user action (same ruling style as
   this morning's 816+839 sweep).
2. **Parallel session**: if a suite run from the other session predates this commit,
   its rows still land under `admin` (pre-hook code). Once both sessions run with
   this base class, `itadmin_`/family residue stays at 0 by construction.
3. **Concurrent-run caveat**: the sweep deletes by family namespace globally; two
   simultaneous suite runs against the shared DB (as happening right now between
   sessions) can soft-delete the other run's live fixture users mid-test. Single-JVM
   surefire runs (the normal case) are unaffected; for parallel sessions the fix is
   sequencing, not code.
4. The parallel session's new fixtures (`menuperm_u_`, `无权菜单角色_`, `ut门禁探针_`)
   are outside the brief's frozen prefix lists and rely on their own `finally`
   cleanup - CONFIRMED LEAKING already: 5 live `menuperm_u_<ts>` sign rows (their
   fixture user cleanup deletes the user but not the sign rows its logins wrote).
   Flagging so the menu-management change adds `menuperm_u_` to `ACCOUNT_PREFIXES`
   when it lands (additive, one line each); until then each of their runs leaves one
   `menuperm_u_` sign row behind.

Report file: `openspec/sdd/reports/fitup-cleanup-hook-report.md` (this file).
