# Batch 4 Brief: Admin CRUD (tasks 4.1-4.4)

## Goal

Management endpoints for org tree, departments, users, roles (+role-menu assignment) and
menus - all under `/api/v1/auth/**`, all token-guarded (no new anonymous paths), each
proven against the test server per the batch-3 test style.

## Task text (tasks.md 4.1-4.4)

- **4.1** Unit-tree endpoints: unit CRUD + tree query (`UnitTree` assembly). Verify:
  two-root scenario test (multi-root capability) + tree-response assertion green.
- **4.2** Department endpoints: department CRUD (FK unit). Verify: three seeded departments
  listed (green assertion).
- **4.3** User endpoints: list / create / change-password / enable-disable; duplicate
  username rejected (`A`-class). Verify: create + duplicate-rejection tests green.
- **4.4** Role & menu endpoints: role CRUD, role-menu assignment, menu list (tree).
  Verify: after assignment, that user's `/auth/me` perms set contains the configured
  button string (including the `radiology:report:audit` example) - test green.

## Standing context (from batches 1-3)

- `AuthIntegrationTestBase` + TestRestTemplate test style; shared test DB (CONTAINS-style
  assertions, unique test data names); admin login via seeded `admin`/`admin123`
  (`Authorization: Bearer <jwt>`).
- Batch 3 already ships: `POST /api/v1/auth/users` minimal create (BCrypt, audit fill
  proven), `AuthService.login/me`, `UserHeaderBridgeFilter`, `MenuTreeService`,
  `UnitTreeService` (tree assembly from Batch 2), `UserDetailsServiceImpl`.
- NEVER edit framework/common; new code in `org.birdbro.lumen.auth.{controller,service,
  dto}`; `@author birdbro`; English comments. JAVA_HOME jdk-11; build from `lumen-sev/`.

## Binding design

- All endpoints live under `/api/v1/auth/**` (Gateway Path Convention) and return DTOs
  (advice wraps to `"200"`); all are token-guarded (no WebSecurityCustomizer additions).
- Controllers thin; services own logic; mappers own SQL. Reuse Batch 2/3 services.
- **Unit (4.1)**: `UnitController` `/api/v1/auth/units`: `POST` create
  `{upId, level, type, name}` (upId 0 = root - multi-root is just another upId-0 row),
  `PUT /{id}` rename/re-parent, `DELETE /{id}` logical (delegate to MyBatis-Plus
  `deleteById` - @TableLogic handles it), `GET /tree` → `UnitTreeService` tree.
  Test: create a second root (unique name `第二根_<ts>`) → GET /tree contains TWO roots
  (hospital root named 示例医院 + the new one); create a child under the hospital root →
  nested in its children.
- **Department (4.2)**: `DepartmentController` `/api/v1/auth/departments`: `POST`
  `{unitId, code, name}` (FK must exist - let the DB FK or a service check reject;
  service-level with `ERROR_C0353`/`ERROR_A0400`-family... pick `ERROR_A0410`-style
  validation or `BusinessException(ERROR_B0030)` - decide and note), `PUT /{id}`,
  `DELETE /{id}` logical, `GET /list?unitId=` (all when absent). Test: list CONTAINS
  radiology/us/endo; create one for the hospital root → appears in list for unitId=1.
- **User (4.3)**: extend Batch 3's minimal create into the full endpoint set on
  `/api/v1/auth/users`: `GET /list` → `UserItemDto{uid, username, name, enable,
  mustChangePassword, createTime}` - NEVER serialize the password hash (DTO, no entity
  leakage per lumen-sev/CLAUDE.md coding standards); `POST` create - ADD the duplicate
  check missing from Batch 3: existing username (deleted=0) →
  `BusinessException(ERROR_A0111)`; `PUT /{id}/password` `{newPassword}` - admin reset:
  BCrypt-encode + `must_change_password=1`; `PUT /{id}/enable` `{enable: 0|1}`.
  Test: create unique user → 200; create the SAME username again → `code:"A0111"`.
- **Role & menu (4.4)**: `RoleController` `/api/v1/auth/roles`: `POST {name, type,
  remark}`, `PUT /{id}`, `DELETE /{id}` logical (also delete its user_role/role_menu rows?
  - logical role delete leaves bindings inert; simplest: leave bindings, note the choice),
  `PUT /{id}/menus` `{menuIds: []}` - delete-then-insert the role's role_menu rows
  (assignment is full-replace semantics; note it). `MenuController`
  `/api/v1/auth/menus/tree` → `MenuTreeService` tree.
  Test (the 4.4 verify): create role `报告审核员_<ts>` → create user `audit_u_<ts>` with
  that role (insert user_role via mapper or extend CreateUserRequest with optional
  roleIds - choose and note) → assign menu 6 (`radiology:report:audit`) to the role →
  login AS THAT USER → `/auth/me` perms CONTAINS `radiology:report:audit` and menus
  contain 放射科.

## TDD sequence

Per endpoint group: write the test first → RED (404) → implement → GREEN. Keep every
RED/GREEN excerpt in the report. Run `cd lumen-sev && ./mvnw -pl lumen-auth -am test -q`.

## Commit

`✨ feat(sev): Batch 4 - admin CRUD: units/departments/users/roles/menus (auth-rbac-foundation 4.1-4.4)`

## Report

Write to `openspec/sdd/reports/auth-b4-implementer.md`: what you implemented, TDD
evidence, files changed, self-review, concerns (record: FK-rejection choice, duplicate
code A0111, role-delete binding choice, full-replace assignment semantics, user-role
creation path).
