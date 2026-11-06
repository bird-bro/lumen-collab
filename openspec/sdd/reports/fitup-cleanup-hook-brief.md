# Follow-up Brief - test-fixture DB cleanup hook (user ruling 2026-11-05, post-archive follow-up to sign-log-query)

## Problem

`./mvnw test` seeds the SHARED team-test-server DB on every run: fixture-account log rows
(`b2*`, `*_u_<ts>`, `ua-probe`), admin-triggered rows (suites log in as the seeded `admin`,
whose logins and `@Log`-endpoint CRUD writes land under `account='admin'`), and leaked
fixture users/roles. On 2026-11-05 the user ordered a manual sweep (816 log_sign + 839
log_info + 5 users soft-deleted, V5-precedent style) AND ruled a standing cleanup hook.

## Design (fix this exactly - do not redesign)

Per-run run-admin identity + family sweep in `AuthIntegrationTestBase`. NO prod-code changes
(zero edits outside `lumen-system/src/test/`). `Constants.APP_CODE` is a compile-time
constant - do NOT touch it.

### 1. Base class additions (`AuthIntegrationTestBase.java`)

- `public static final String RUN_ACCOUNT = "itadmin_" + System.currentTimeMillis();`
  (one value per JVM/fork; underscore+ts matches the fixture convention)
- `private static volatile Long runUserId;` + accessor `runUserId()` (Long, null before clone)
- `static final String ADMIN_TEST_PASSWORD = "admin123";` (already the documented test
  credential in these sources)
- `@BeforeAll static void ensureRunAdminIdentity()` - plain JDBC (DriverManager, connection
  params re-read via `AuthTestEnv.load()` exactly like `teamTestServerProperties` does;
  database name from `AuthTestEnv.DB`/`DEFAULT_DB`; URL `?currentSchema=system,public`):
  - guard: `SELECT id FROM system.user_info WHERE username = ? AND deleted = 0` - when absent:
    clone `INSERT INTO system.user_info (username, password, name)
    SELECT ?, password, 'it-admin run fixture' FROM system.user_info WHERE username='admin'
    AND deleted=0` (same bcrypt hash ⇒ same password works), then
    `INSERT INTO system.user_role (user_id, role_id) VALUES ((SELECT id ... WHERE username=?),
    1)` (role 1 = the seeded admin role; same menu/perm set as admin)
  - capture `runUserId`; if the SELECT above found an existing live row, reuse it
  - MUST run after context refresh (Flyway applied) - `@BeforeAll` in a Spring Boot test
    qualifies; add a clear IllegalStateException if the SELECT returns no admin row
- `@AfterAll static void sweepRunResidue()` - single JDBC transaction, all statements
  soft-delete (`SET deleted = 1`, plus `update_time = CURRENT_TIMESTAMP` where the table has
  it - user_info/role_info have it, the log tables DO NOT):
  1. `public.log_sign` / `public.log_info`: `WHERE deleted = 0 AND (account = RUN_ACCOUNT
     OR account LIKE '<p>%' for each ACCOUNT_PREFIXES OR account = 'ua-probe')`
  2. `system.user_role`: `WHERE deleted = 0 AND user_id IN (SELECT id FROM system.user_info
     WHERE username = RUN_ACCOUNT OR username LIKE '<p>%' ...)`
  3. `system.user_info`: same predicate as 2 minus the IN (direct username match)
  4. `system.role_menu`: `WHERE deleted = 0 AND role_id IN (SELECT id FROM system.role_info
     WHERE deleted = 0 AND name LIKE '<r>%' ...)`
  5. `system.role_info`: `WHERE deleted = 0 AND name LIKE '<r>%' ...`
- Family constants (test-owned namespace; document in the class javadoc: real users must
  NEVER take these prefixes; `AuthLoginTests` deliberately stays on the real admin and is
  the one accepted residue - ~2 sign rows per run):
  - `ACCOUNT_PREFIXES = { "b2", "dup_u_", "pw_u_", "en_u_", "replace_u_", "role_del_u_",
    "audit_u_", "single_u_", "noperm_u_", "city_u_", "no.such.user." }`
  - `ROLE_NAME_PREFIXES = { "报告审核员_", "单页角色_", "无权角色_", "denied_role_" }`
- `static String runAdminLoginBody()` → `{"username":"<RUN_ACCOUNT>","password":"admin123"}`

### 2. Migrate admin login sites (mechanical, sweep exhaustively)

Enumerate with `grep -rn '"admin"' *.java` in `lumen-system/src/test/java/org/birdbro/
lumen/system/`. Replace every login request body that authenticates `admin`/`admin123`
with `runAdminLoginBody()` (or `RUN_ACCOUNT` where a helper composes its own JSON, e.g.
AuthPermEnforcementTests' loginToken ternary). Known sites: AuthAuditIdentityTests,
AuthDictCityTests, AuthDictCrudTests, AuthLogQueryTests, AuthOperationLogTests,
AuthOrgCrudTests, AuthOrgDepTests, AuthRoleMenuTests (multiple - CHECK EACH: only migrate
bodies that authenticate admin; bodies logging in created fixture users stay),
AuthSignLogQueryTests, AuthSignLogTests (check its postJsonWithUa bodies), plus any others
the grep finds.

- KEEP on real admin (do NOT migrate): `AuthLoginTests` (it tests the seeded-admin login
  contract itself), `AuthSeedMigrationTests` (asserts the seeded hash, may not log in).
- `AuthAuditIdentityTests`: ALSO change its `createBy == 1` assertion to
  `createBy == AuthIntegrationTestBase.runUserId()` (same contract - operator identity
  propagates to audit columns - now identity-isolated). If you find any OTHER assertion
  that hard-depends on the literal username "admin" outside AuthLoginTests/AuthSeedMigrationTests,
  STOP and report BLOCKED - do not weaken assertions to fit.

### 3. Verify

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q          # 74/74 green (no test-count change)
```

Then on the team test server via docker psql (params from gitignored
`lumen-sev/.env-test.local`; NEVER print values):

```sql
SELECT 'log_sign_live', count(*) FROM public.log_sign WHERE deleted=0
UNION ALL SELECT 'log_info_live', count(*) FROM public.log_info WHERE deleted=0
UNION ALL SELECT 'itadmin_users_live', count(*) FROM system.user_info WHERE deleted=0 AND username LIKE 'itadmin_%'
UNION ALL SELECT 'family_users_live', count(*) FROM system.user_info WHERE deleted=0 AND (username LIKE 'b2%' OR username LIKE '%\_u\_%')
UNION ALL SELECT 'family_roles_live', count(*) FROM system.role_info WHERE deleted=0 AND (name LIKE '报告审核员_%' OR name LIKE '单页角色_%' OR name LIKE '无权角色_%');
```
Expect: everything 0 EXCEPT the AuthLoginTests residue (`log_sign_live`/`log_info_live`
will show a small nonzero tail from the real-admin login tests - report the exact numbers).

## Constraints

- ONLY files under `lumen-system/src/test/` change. The worktree carries a parallel
  session's in-flight edits (lumen-web, coordination docs) - stage file-by-file, commit
  ONLY yours.
- Follow the existing file style (javadoc explaining WHY, @author birdbro on new members'
  docs is not needed - this edits an existing class; keep its header intact).
- Never print/commit `.env-test.local` values; never touch rcp-test; never rewrite applied
  migrations.

## Report + commit

- Full report: `openspec/sdd/reports/fitup-cleanup-hook-report.md` (migration list with
  per-file sites, DB verification numbers, self-review, concerns)
- Commit: `✨ feat(system): run-scoped test admin + residue sweep in the shared test DB`
  mentioning it is a post-archive follow-up ruled 2026-11-05. Style per `git log --oneline -5`.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha.
