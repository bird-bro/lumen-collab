# Tasks: button-perm-enforcement-api

## 1. V15 seed migration (red → green)

- [x] 1.1 Failing migration test (`AuthSeedMigrationTests` pattern): after V15, 22 `type='F'` rows exist under the console C pages with the inventory perms strings, the admin role's grant contains all 22, and the idempotency guards hold (re-executing the seed statements changes no row counts) - verify: test red before the migration exists, green after.
- [x] 1.2 Write `V15__button_perm_nodes.sql` per design D1 (explicit ids above the V14 top, guarded inserts, role-1 NOT-EXISTS grants, `setval` resync) - verify: `./mvnw -pl lumen-system -am test -q` passes with JDK 11; hand-apply on the shared test DB (psql) stays a no-op on re-run.

## 2. Method-level pins (red → green)

- [x] 2.1 Failing permission-matrix cases in `AuthPermEnforcementTests` (design D4): for each of the 22 write endpoints, a fixture role with ONLY the page perm is denied A0301 with no row written, and a fixture role with ONLY the button perm passes the write while its list read stays denied - verify: all red (writes currently pass on the page perm), scoped to the inventory table so a future endpoint addition extends the matrix.
- [x] 2.2 Add the 22 method-level `@PreAuthorize` annotations (7 controllers per the inventory; one-line javadoc pointer each, design D3) - verify: matrix green; existing suites (`AuthUserManagementTests`, `AuthRoleMenuTests`, `AuthOrgCrudTests`, `AuthOrgDepTests`, `AuthDictCrudTests`, `AuthMenuManageTests`) stay green - their fixture logins are run-admin (all perms) so only the deny-side expectations change.
- [x] 2.3 Register any new fixture account/role prefixes in `AuthIntegrationTestBase` (sweep namespaces) - verify: shared DB clean after a full class run (0 live rows with the prefixes).

## 3. Full verification

- [x] 3.1 Whole-reactor build + tests from the aggregator with explicit JDK 11 - verify: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q` passes in `lumen-sev/`.
- [x] 3.2 Walk each WHEN/THEN of the change's `specs/auth-rbac` delta against implemented behavior - verify: every scenario has a passing test or mapped proof; record the mapping in the apply summary.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/auth-rbac/spec.md` + zh brief at `/opsx:apply`/archive time.
- Frontend change `button-perm-enforcement-web` gates each affordance by the same inventory strings (see `openspec/coordination/button-perm-enforcement.md`); backend-first sequencing applies.
- Deployment note for operators: non-admin roles lose button affordances until the F nodes are granted (intended; admin auto-granted).
