# Tasks: menu-management-api

## 1. Request DTOs (red → green)

- [x] 1.1 Create `CreateMenuRequest` / `UpdateMenuRequest` (`upId` `@NotNull`, `title` `@NotBlank`, `type` `@Pattern("M|C|F")`, `perms`/`path`/`component`/`icon`/`sort`/`visible` optional) with the family's javadoc style - verify: `./mvnw -pl lumen-system -am compile -q` passes with JDK 11.
- [x] 1.2 Bean Validation test: blank title / bad `type` both produce the standard `A0400`-shape envelope - verify: new cases in the existing controller-test style pass.

## 2. `MenuManageService` guards and writes (red → green)

- [x] 2.1 Failing `MenuManageServiceTest` cases on the in-memory guard map: unknown parent, parent = `F` node, `upId = id`, `upId` inside own descendant subtree - verify: test fails before the service exists, then passes once the map + guards land (order: map → parent guards → cycle walk).
- [x] 2.2 Failing cases for create semantics: insert writes all request fields (`BeanUtil.copyProperties` 1:1), auto-grant inserts `role_menu(1, newId)` only when no live binding exists (NOT EXISTS guard) - verify: mocked-mapper tests assert the two inserts and the guarded second insert, all inside one transaction (`@Transactional` present).
- [x] 2.3 Failing cases for update semantics: full field update incl. `upId` move; grants untouched; type flip to `F` with live children rejected (design D5/Risks back-door guard) - verify: mocked-mapper tests pass.
- [x] 2.4 Failing cases for delete semantics: live children → `BusinessException(ERROR_A0400)` and no deletes; childless → `deleteById` + `roleMenuMapper.delete(by menuId)` in one transaction - verify: mocked-mapper tests pass.
- [x] 2.5 Update service javadoc to document the guard map, auto-grant, and the transaction shapes (house doc style of `OrgServiceImpl`/`DictDetailManageServiceImpl`) - verify: javadoc review against design D1-D5.

## 3. Controller endpoints (red → green)

- [x] 3.1 Failing `MenuControllerTest` (or extend the existing controller-test suite) for `POST /api/v1/system/menus`, `PUT /api/v1/system/menus/{id}`, `DELETE /api/v1/system/menus/{id}`: 200 envelope with the service result, `A0400` msg passthrough on `BusinessException` - verify: tests pass; existing `GET /tree` test untouched (additive only).
- [x] 3.2 Confirm the class-level `@PreAuthorize("hasAuthority('system:menu:list')")` covers the new methods (no method-level additions) - verify: existing `AuthPermEnforcementTests` pattern gains a case per new endpoint asserting the gate.

## 4. Full verification

- [x] 4.1 Whole-reactor build + tests from the aggregator with explicit JDK 11 - verify: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home && ./mvnw test -q` passes in `lumen-sev/`.
- [x] 4.2 Walk each WHEN/THEN of the change's `specs/api` + `specs/auth-rbac` deltas against the implemented behavior - verify: every scenario has a passing test or an explicit mapped proof; record the mapping in the apply summary.

## Workflow follow-up (reference, outside tracked progress)

- Spec sync to `openspec/specs/api/spec.md` + `auth-rbac/spec.md` and zh brief per root CLAUDE.md happens at `/opsx:apply` / archive time.
- Frontend change `menu-management-web` builds against the real endpoints after this change lands (backend-first ruling); see `openspec/coordination/menu-management.md` for the gate.
