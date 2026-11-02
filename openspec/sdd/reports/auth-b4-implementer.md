# Batch 4 Implementer Report - Admin CRUD: units/departments/users/roles/menus (tasks 4.1-4.4)

Status: **DONE** - all four endpoint groups red/green-captured, final
`./mvnw -pl lumen-auth -am test -q` MVN_EXIT=0 with **34/34** tests (Batches 1-3's 20 +
Batch 4's 14 new), committed as `b72d4de` (30 files, +2216/-26). Framework/common
untouched (`git diff --stat -- lumen-sev/framework lumen-sev/common` = empty); no mapper
file changed at all (every query expressed through MyBatis-Plus wrappers); no new
anonymous path (`AuthWebSecurityConfig`'s `web.ignoring()` list still names
`/api/v1/auth/login` ONLY - all Batch 4 endpoints ride the copied chain's
`anyRequest().authenticated()`).

## What was implemented

### 4.1 - Unit tree (UnitController `/api/v1/auth/units`)
- `POST` create `{upId, level, type, name}` - `upId = 0` is a root (multi-root is just
  another upId-0 row); a NON-ZERO `upId` is verified against a live parent (service-side
  `A0400`, because `unit_info` has no self-FK - tree integrity is service-level per the
  entity contract). An omitted `level` derives from the parent (`parent.level + 1`,
  roots 1). `PUT /{id}` partial update (null fields skipped by the MP NOT-NULL update
  strategy): rename / re-parent (new parent validated to exist + not-itself guard).
  `DELETE /{id}` bare `deleteById` (`@TableLogic` renders `UPDATE deleted = 1`).
  `GET /tree` delegates to the Batch 2 `UnitTreeService` - the tree response IS the
  `UnitInfo` node list with `children` (the 随访 UnitTree pattern; `deleted` is
  `@JsonIgnore` so it never serializes).
- New: `dto/CreateUnitRequest`, `dto/UpdateUnitRequest`, `service/UnitService`,
  `service/impl/UnitServiceImpl`, `controller/UnitController`.

### 4.2 - Departments (DepartmentController `/api/v1/auth/departments`)
- `POST {unitId, code, name}` with service-level FK validation: unit must be live
  (`A0400` detail; the V1 DB FK stays the backstop) and `code` duplicate-checked among
  live rows (`A0712` 组织编码已存在; partial unique index backstop). `PUT /{id}`
  partial (re-anchor validated; code duplicate-checked excluding self; unknown target
  `B0031`). `DELETE /{id}` logical. `GET /list?unitId=` (all when absent, id-ordered).
  Department ENTITY is the response shape (no secret fields - unlike `user_info`).
- New: `dto/CreateDepartmentRequest`, `dto/UpdateDepartmentRequest`,
  `service/DepartmentService`, `service/impl/DepartmentServiceImpl`,
  `controller/DepartmentController`.

### 4.3 - Users (UserInfoController `/api/v1/auth/users`, full set)
- `GET /list` -> `UserItemDto{uid, username, name, enable, mustChangePassword,
  createTime}` - explicit-field DTO, the `password` hash CANNOT serialize (test asserts
  the JSON item has no `password` key at all).
- `POST` create: ADDED the service-level `BusinessException(ERROR_A0111)` check (live
  rows only, `@TableLogic`) while the V1 partial unique index (line 63) is untouched as
  the backstop. Signature and return unchanged from Batch 3.
- `PUT /{id}/password {newPassword}`: admin reset - BCrypt re-encode + `must_change_
  password = 1`; `PUT /{id}/enable {enable:0|1}` (0/1 bean-validated on
  `SetEnableRequest` -> `A0400` on violation); update targets must be live (`B0031`).
- Shared-encoder polish taken (Batch-3 review): ONE `BCryptPasswordEncoder` `@Bean` in
  `AuthWebSecurityConfig` (additive method); `UserInfoServiceImpl` AND `AuthServiceImpl`
  now inject it (their separate `new` fields are gone).
- New: `dto/UserItemDto`, `dto/ResetPasswordRequest`, `dto/SetEnableRequest`. Modified:
  `UserInfoService` (+3 methods), `UserInfoServiceImpl` (rewrite of impl, existing
  create signature intact), `UserInfoController` (+3 endpoints), `AuthWebSecurityConfig`
  (+1 bean), `AuthServiceImpl` (field -> injected bean).

### 4.4 - Roles & menus (RoleController `/api/v1/auth/roles`, MenuController `/api/v1/auth/menus`)
- Role CRUD: `POST {name, type, remark}` (type null -> V1 column default 11; duplicate
  live name `A0714`), `PUT /{id}` partial (duplicate name excluding self; unknown target
  `B0031`), `DELETE /{id}` logical WITH cascade to the role's `user_role`/`role_menu`
  rows in one transaction (decision recorded below - the Batch 3 perms/menu walks do
  NOT join `role`, so live bindings to a deleted role would keep granting; the test
  proves dead bindings + the bound user's perms drop).
- `PUT /{id}/menus {menuIds: []}` FULL-REPLACE assignment: role must exist (`A0400`),
  every menu id must exist (`A0400`), dedupe ids, logically delete the role's live
  `role_menu` rows, insert one per id - an EMPTY list legitimately clears all grants.
- `GET /api/v1/auth/menus/tree` -> the unscoped whole tree via the Batch 2
  `MenuTreeService` (copied `MenuTree` POJO contract).
- `CreateUserRequest` gained optional `roleIds` - the recorded user-role creation path
  (see decisions): validated against live roles (`A0400`), bound in the same
  transaction as the user insert.
- New: `dto/CreateRoleRequest`, `dto/UpdateRoleRequest`, `dto/AssignRoleMenusRequest`,
  `service/RoleService`, `service/impl/RoleServiceImpl`, `controller/RoleController`,
  `controller/MenuController`. Modified: `dto/CreateUserRequest` (+1 field).

## Recorded decisions (the five the brief lists)

1. **FK-rejection choice (4.2)**: service-level `BusinessException(ERROR_A0400,
   "unit does not exist: unit id N")` BEFORE the insert - an impossible FK is a request
   parameter error, and an A-class envelope beats a raw `DataIntegrityViolationException`
   rendering as `code:"500"`. The V1 database FK stays as the backstop (e.g. concurrent
   unit delete). Proven live: `createDepartmentWithUnknownUnitRejectedWithA0400` green,
   handler log `[业务异常] Code: A0400`.
2. **Duplicate code A0111 (4.3)**: `UserInfoServiceImpl.rejectDuplicateUsername` -
   `selectCount(username eq)` > 0 (live rows; `@TableLogic`) -> `A0111` 用户名已存在;
   V1 line 63 partial unique index UNTOUCHED as backstop. The RED run is the evidence
   the check was needed: without it the second create surfaced as
   `{"code":"500","msg":"系统异常，请稍后重试"}`.
3. **Role-delete binding choice (4.4)**: logical role delete CASCADES (logically
   deletes the role's `user_role` + `role_menu` rows in the same `@Transactional`
   method). The brief floated "leave bindings, note the choice" - but leaving them is
   NOT inert: `selectPermsByUserId`/`selectMenusByUserId` walk
   `user_role -> role_menu -> menu_info` WITHOUT joining `role`, so live bindings to a
   dead role would keep granting menus/perms forever. (Patching those queries to join
   `role` was rejected: they are Batch 3 methods - modifying them is off-limits under
   the never-modify rule.) Proven: after `DELETE /roles/{id}`, both binding tables have
   zero live rows for the role and the bound user's `/auth/me` no longer carries the
   audit string.
4. **Full-replace assignment semantics (4.4)**: `PUT /roles/{id}/menus` is
   delete-then-insert - the request's `menuIds` is the role's COMPLETE grant set, an
   empty array clears all grants (why `@NotNull` only), and the partial unique index
   `uk_role_menu_role_menu` never fights the logically deleted rows. Proven:
   `menuAssignmentIsFullReplace` - grant [6] -> perms carry the audit string; re-grant
   [3] -> the string is GONE.
5. **User-role creation path (4.4)**: `CreateUserRequest.roleIds` (optional list,
   validated, transactional) rather than inserting `user_role` from the test. Reasons:
   the brief's 4.3 endpoint set has NO separate user-role assignment endpoint, so a
   create-time binding is the only API-honest way for the 4.4 flow (and the upcoming
   user-management frontend) to attach a role; the alternative would have punched test
   fingers through a mapper, which proves nothing about the API.

Additional recorded decisions:
- **Assignment payload [3, 6] in the verify test**: the copied `TreeUtils.buildTree`
  DROPS orphans, so granting button 6 alone yields the perms string but an EMPTY menu
  tree (parent page 3 absent from the granted set). The verify test grants the
  radiology page + its audit button - as any real admin UI would - and asserts BOTH
  the perms string AND the 放射科 page node. The pure-perms orphan shape is still
  covered by `menuAssignmentIsFullReplace` (grant [6] alone).
- **Mutation endpoints return `Boolean.TRUE`, not void**: the copied
  `GlobalResponseAdvice` renders a null body through its `R.ofSuccess()` branch, whose
  code is "204" - returning a value keeps the `code:"200"` the spec's scenarios use.
  (New machine-semantic fact for the frontend envelope contract: `200` + `data:true`
  on update/delete, `200` + `data:<id>` on create.)
- **Unit `level` derivation**: accepted from the request when given (the brief's body
  contract), derived `parent.level + 1` (roots 1) when omitted.
- **Role name duplicate code**: `A0714` 数据重复 (no role-specific A-code exists in the
  copied enum; department code uses `A0712` 组织编码已存在).

## TDD evidence

All runs: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`,
`cd lumen-sev && ./mvnw -pl lumen-auth -am test -q -Dtest=<Class> -DfailIfNoTests=false`
(the flag is REQUIRED with `-am`: surefire in `common` aborts the reactor with
"No tests were executed!" when the -Dtest pattern matches nothing there - recorded so
the next batch doesn't rediscover it).

### Group 4.1 - RED (test written first; no unit code)
`-Dtest=AuthUnitCrudTests` -> MVN_EXIT=1:
```
WARN ... PageNotFound : No mapping for POST /api/v1/auth/units
[ERROR] Tests run: 3, Failures: 3, Errors: 0, Skipped: 0
unit create must answer over HTTP 200 (envelope transport) ==> expected: <200> but was: <404>
the child create (upId = 1, level omitted) must succeed:
  {"timestamp":...,"status":404,"error":"Not Found","path":"/api/v1/auth/units"} ==> expected: <200> but was: <>
createUnitUnderUnknownParentRejectedWithA0400:129 expected: <200> but was: <404>
```
(The brief's predicted 404 RED shape held - a valid admin token passes the copied chain,
dispatch happens, no handler exists.)

### Group 4.1 - GREEN (UnitController/Service/DTOs added)
Same command -> MVN_EXIT=0, surefire `tests="3" failures="0" errors="0"`. The two-root
scenario is proven against the real DB: top-level roots contain 示例医院 AND
`第二根_<ts>` (`rootNames.size() >= 2`), the renamed child nests under the hospital
root, unknown-parent create renders A0400.

### Group 4.2 - RED -> GREEN
RED (`-Dtest=AuthDepartmentTests`, MVN_EXIT=1): `No mapping for GET
/api/v1/auth/departments/list` + `POST /api/v1/auth/departments`; `Tests run: 3,
Failures: 3`; `expected: <200> but was: <404>` x3.
GREEN -> MVN_EXIT=0, `tests="3" failures="0" errors="0"`; seeded radiology/us/endo
listed with unitId=1 (CONTAINS-style on the shared DB); created department appears under
`list?unitId=1` and NOT under a foreign unit; unknown-unit create rendered live:
```
[业务异常] URI: /api/v1/auth/departments, Code: A0400, Msg: 用户请求参数错误：unit does not exist: unit id 987654321
```

### Group 4.3 - RED -> GREEN
RED (`-Dtest=AuthUserManagementTests`, MVN_EXIT=1): `No mapping for PUT
/api/v1/auth/users/14/enable` / `PUT /api/v1/auth/users/15/password` / `GET
/api/v1/auth/users/list`; `Tests run: 4, Failures: 4`. The decisive excerpt - the
pre-check gap the Batch-3 review predicted:
```
the service-level duplicate check must reject an existing live username (the DB partial
unique index stays the backstop): {"timestamp":"...","code":"500","msg":"系统异常，请稍后重试","data":null}
==> expected: <A0111> but was: <500>
```
GREEN -> MVN_EXIT=0, `tests="4" failures="0" errors="0"`. The end-to-end assertions that
now hold: list DTO without any `password` key; duplicate create -> `A0111` (handler log
`Code: A0111, Msg: 用户名已存在`); admin reset -> OLD password `A0210` at login, NEW
password logs in with `profile.mustChangePassword = 1`; disable -> login `A0202`
(用户账户被冻结), re-enable -> login `200`.

### Group 4.4 - RED -> GREEN
RED (`-Dtest=AuthRoleMenuTests`, MVN_EXIT=1): `No mapping for GET
/api/v1/auth/menus/tree` + `POST /api/v1/auth/roles`; `Tests run: 4, Failures: 4`.
GREEN -> MVN_EXIT=0, `tests="4" failures="0" errors="0"`. THE verify chain proven green:
create role `报告审核员_<ts>` -> create user `audit_u_<ts>` WITH that role (roleIds) ->
`PUT /roles/{id}/menus {"menuIds":[3,6]}` -> login AS THAT USER -> `/auth/me` perms
CONTAINS `radiology:report:audit` AND menus contain 放射科 (with the audit button nested).
Plus: full-replace drops the previous grant; duplicate role name -> `A0714` (handler log
`Code: A0714, Msg: 数据重复：role name already exists: 系统管理员`); logical role
delete leaves ZERO live `user_role`/`role_menu` rows for the role and the bound user's
perms set drops the string.

### Final full reactor run
`./mvnw -pl lumen-auth -am test -q` (no -Dtest) -> MVN_EXIT=0:
```
AuthApplicationTests 1 · AuthAuditIdentityTests 1 · AuthDataLayerTests 4 ·
AuthDepartmentTests 3 · AuthLoginTests 5 · AuthRoleMenuTests 4 ·
AuthSchemaMigrationTests 4 · AuthSeedMigrationTests 5 · AuthUnitCrudTests 3 ·
AuthUserManagementTests 4  = 34 green (failures=0, errors=0 in every class)
```

## Files changed (commit b72d4de, 30 files, +2216/-26; all under `lumen-sev/lumen-auth/`)

New production (20): `controller/{Unit,Department,Role,Menu}Controller.java`;
`dto/{CreateUnitRequest,UpdateUnitRequest,CreateDepartmentRequest,UpdateDepartmentRequest,
UserItemDto,ResetPasswordRequest,SetEnableRequest,CreateRoleRequest,UpdateRoleRequest,
AssignRoleMenusRequest}.java`; `service/{UnitService,DepartmentService,RoleService}.java`;
`service/impl/{UnitServiceImpl,DepartmentServiceImpl,RoleServiceImpl}.java`.
New tests (4): `Auth{UnitCrud,Department,UserManagement,RoleMenu}Tests.java`.
Modified (6): `config/AuthWebSecurityConfig.java` (+shared BCrypt bean, javadoc),
`controller/UserInfoController.java` (+3 endpoints, javadoc),
`dto/CreateUserRequest.java` (+optional roleIds), `service/UserInfoService.java`
(+3 methods), `service/impl/UserInfoServiceImpl.java` (A0111 check, roleIds binding,
DTO list, shared encoder), `service/impl/AuthServiceImpl.java` (shared encoder
injection - 9 lines). Zero changes to mappers, entities, migrations, framework, common.

## Self-review findings

1. **Binding-design fidelity**: every endpoint from the brief's binding design exists
   with the brief's URL/verb/body shape; the only body-shape liberty is the optional
   fields (`level`/`type` on unit create, all partial-update DTOs) - supersets that do
   not break the contract. No new anonymous path; no WebSecurityCustomizer change.
2. **Existing-method discipline**: `UserInfoService.createUser` signature and return
   unchanged (only its body gained the handoff-mandated A0111 check); all other service
   changes are ADDITIVE interface methods. `AuthServiceImpl`'s constructor gained the
   shared-encoder parameter - no external caller exists (Spring wires it), and the full
   suite re-proved the login loop (5/5) after the change. No mapper/entity/migration
   touched; the "never modify existing public methods" rule has no other contact points.
3. **Transactionality**: the three multi-write flows (`createUser` with roleIds,
   `assignMenus` delete-then-insert, `deleteRole` cascade) are `@Transactional`; single
   writes are not (matching the copied machine's habit and Batch 3).
4. **Shared-DB hygiene**: every created row carries a timestamp-suffixed unique name;
   every test logically deletes what it created after the assertions (units, department,
   users, role+bindings); assertions are CONTAINS-style or row-scoped (by unique id) -
   the only COUNT assertions are "zero live rows for MY unique role_id" + "the bindings
   existed", never an exact total on business rows.
5. **Self-review bug caught before commit**: a duplicated id in `roleIds` would have
   passed the existence check (count vs list size) but then violated the `user_role`
   partial unique index on the second insert - the binding loop now iterates a deduped
   `LinkedHashSet` (mirroring `assignMenus`). Full suite re-run green afterwards.
6. `application.yml`/`bootstrap.yml` untouched; no configuration or version changes; the
   test server contract (env file, bootstrap) reused as-is.

## Issues or concerns

1. **Envelope-contract discovery (frontend-relevant)**: void mutations would render
   `code:"204"` through the copied advice's null branch - hence `data:true` returns.
   Also the copied `R.ofFail(code, msg, data)` IGNORES its third argument (data never
   set on failures), so rejection envelopes carry `data: null` only; `msg` includes the
   detail after "：" for `BusinessException(enum, detail)`. The frontend should mock
   from these recorded shapes.
2. **Unit delete does not cascade or guard**: per the binding design, `DELETE /units/
   {id}` is a bare logical deleteById. Deleting a unit with live children makes them
   orphans (dropped from the tree assembly, data intact); deleting one with live
   departments is NOT blocked (the FK stays satisfied since the row is only logically
   dead). If product wants a guard ("unit has sub-units/departments" -> `B0032`), it is
   a small follow-up inside `UnitServiceImpl.delete` - left out of this batch because
   the binding design says "delegate to MyBatis-Plus deleteById".
3. **Re-parent cycle detection is shallow**: `update` rejects self-parenting but not
   deeper cycles (A under B under A). The tree assembly tolerates it (orphans drop), a
   deep guard is a follow-up if the org UI needs it.
4. **`update_time` staleness**: the copied `MetaObjectConfig.updateFill` fills only
   `update_by` (and via `strictInsertFill`), and V1's `update_time` has no ON UPDATE -
   updated rows keep their original `update_time`. Pre-existing copied-machine
   behavior, visible now that update endpoints exist; flagged for the data-spec owners,
   not fixed here (framework is untouchable).
5. **Users list has no pagination**: `GET /users/list` returns every live user
   (id-ordered). Fine at current scale; pagination is a later change if the admin UI
   needs it.
6. **The 4.4 verify payload is [3, 6], not bare [6]** (orphan-drop semantics of the
   copied tree builder) - recorded above; reviewers checking the brief's letter should
   read decision bullet before the concern.
7. `-DfailIfNoTests=false` must ride every filtered reactor run (see TDD evidence
   preamble) - otherwise `common`'s surefire aborts the build before lumen-auth runs.

## Handoff for the reviewer / next batch

- The perms/menu walks still do not join `role` (Batch 3 methods, untouched); the
  cascade in `RoleServiceImpl.delete` is what keeps them truthful - if a future change
  adds a hard FK-style guarantee, joining `role` (as an OVERLOAD, never a modification)
  would make it structural.
- Frontend can now mock: units (CRUD + tree), departments (CRUD + list), users (list /
  create incl. roleIds / password reset / enable), roles (CRUD + full-replace menus),
  menus tree - all `code:"200"` envelopes, rejections A0400/A0712/A0714/A0111/B0031/
  A0202/A0210 as recorded above.
