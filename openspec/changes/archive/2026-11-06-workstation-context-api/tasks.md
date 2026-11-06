# Tasks: workstation-context-api

## 1. V21 migration (red → green)

- [x] 1.1 Failing migration test (`AuthButtonSeedTests` pattern): `user_workstation` table exists with the partial unique default index; the `system:user:assign-ws` F node exists under 用户管理 with the admin auto-grant; re-executing the seed statements is a row-count no-op - verify: red, then green after 1.2.
- [x] 1.2 Write `V21__user_workstation.sql` per design D6 (content-guarded F node + grant; CREATE TABLE with FK + partial unique) - verify: whole-suite green with JDK 11; hand-apply no-op on re-run.

## 2. Payloads + assignment endpoint (red → green)

- [x] 2.1 Failing payload tests: login/me carry `workstations` + `profile.primaryWorkstation`; bootstrap user (no rows) gets `["admin"]`/"admin"; grant flows to the next login - verify: red (fields absent), then green.
- [x] 2.2 Failing assignment tests: full-replace grant (reopen shows the posted set), two-defaults rejection A0400, deny without `system:user:assign-ws` (A0301); a soft-deleted user's rows stop contributing (jdbcTemplate simulates the delete - the family has no delete endpoint) - verify: red, then green.
- [x] 2.3 Implement: `UserWorkstation` entity + mapper (Lombok combo + `@TableLogic`), DTOs (`AssignWorkstationsRequest` with `@Pattern` enum validation), `WorkstationService` (assembler + full-replace, design D2/D3/D5), controller endpoint with the method-level pin (additive; javadoc pointer per the button-perm model), MeResponse/LoginResponse/ProfileDto additive fields - verify: 2.1/2.2 green; existing suites green (run-admin unaffected).

## 3. Full verification

- [x] 3.1 Whole-reactor build + tests from the aggregator with explicit JDK 11 - verify: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q` passes in `lumen-sev/`.
- [x] 3.2 Walk each WHEN/THEN of the change's `specs/api` + `specs/auth-rbac` deltas against implemented behavior - verify: every scenario has a passing test or mapped proof; record the mapping in the apply summary.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/api/spec.md` + `auth-rbac/spec.md` + zh brief at archive time.
- Frontend change `workstation-context-web` consumes the payloads (see `openspec/coordination/workstation-context.md`); backend-first sequencing applies.
