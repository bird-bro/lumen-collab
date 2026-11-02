# Batch 2 Implementer Report - Flyway migrations + data layer (tasks 2.1-2.3)

Status: **DONE** - all three tasks red-green proven against the team test server
(PostgreSQL 14.11, `auth` schema, Flyway 7.7.3), final `./mvnw -pl lumen-auth -am test -q`
exit 0 with 14/14 tests, committed as `c94d004`.

## What was implemented

### 2.1 - `V1__auth_schema.sql` (seven tables, binding DDL design)

`lumen-sev/lumen-auth/src/main/resources/db/migration/V1__auth_schema.sql`, exactly per
the binding DDL design: `unit_info` (up_id DEFAULT 0 / level DEFAULT 1 / type DEFAULT 0 /
name, index on up_id, deliberately NO self-FK), `department` (unit_id FK unit_info, code
varchar(32) with partial-unique `WHERE deleted = 0`, name, index on unit_id), `user_info`
(username partial-unique, password varchar(100) BCrypt, must_change_password DEFAULT 0,
enable DEFAULT 1, name), `role` (name partial-unique, type DEFAULT 11, remark),
`user_role` (FKs user_info/role, partial-unique pair, index on user_id), `menu_info`
(up_id DEFAULT 0, type varchar(1) DEFAULT 'C', title, perms, path, component, icon, sort
DEFAULT 0, visible DEFAULT 1, index on up_id), `role_menu` (FKs role/menu_info,
partial-unique pair, index on role_id). Every table: `id serial PRIMARY KEY` + the
EntityBase audit block as the LAST five columns in exact order (`deleted NOT NULL DEFAULT
0`, `create_by`, `create_time NOT NULL DEFAULT CURRENT_TIMESTAMP`, `update_by`,
`update_time NOT NULL DEFAULT CURRENT_TIMESTAMP`).

### 2.2 - `V2__seed.sql` (binding seed design, explicit ids)

`V2__seed.sql`: unit id 1 `示例医院` (up 0/level 1/type 0); departments 1-3
(radiology/放射科, us/超声科, endo/内镜科, unit_id 1); user id 1 `admin` with the
coordinator's BCrypt hash / must_change_password 1 / enable 1 / `系统管理员`; role id 1
`系统管理员` type 10 remark `seed default administrator role`; user_role 1 (1→1); menus
1-6 exactly as specified (系统管理 M /system icon setting; 用户管理 C perms
`system:user:list`; 放射科/超声科/内镜科 C pages; 报告审核 F up 3 perms
`radiology:report:audit`); role_menu role 1 → menus 1-6. **Plus `setval` for all seven
serial sequences** past the explicit seed ids - without it the first application-side
insert (MyBatis-Plus IdType.AUTO = nextval) collides with the seeded id-1 rows; the
smoke test asserts the sequence runs past id 1.

### 2.3 - Entities + mappers + tree-assembly services

- 7 entities in `org.birdbro.lumen.auth.entity` (UnitInfo, Department, UserInfo, Role,
  UserRole, RoleMenu, MenuInfo): each `extends EntityBase` (audit fields inherited, not
  redeclared), explicit `@TableName`, `@TableId(value = "id", type = IdType.AUTO)
  Integer id`, Lombok `@Data` + `@EqualsAndHashCode(callSuper = true)`, camelCase
  properties. UnitInfo carries `@TableField(exist = false) List<UnitInfo> children` -
  the entity IS the tree node per the brief.
- 7 mappers in `org.birdbro.lumen.auth.mapper`: `BaseMapper<X>` with `@Mapper`,
  matching the referenced framework `LogInfoMapper` precedent (see self-review note on
  the brief's "@Mapper-free" wording).
- `UnitTreeService` - flat `unit_info` → tree via `TreeUtils.buildTree`
  (rootParentValue 0), `unitTree()` + `build(List<UnitInfo>)` seam.
- `MenuTreeService` - flat `menu_info` → `List<MenuTree>` (FIRST use of the copied
  POJO): id→mid, title, upId, type, perms, path, component, icon, sort, visible
  Integer→Boolean; trimmed lumen columns stay null on the POJO. Plus
  `permsSet(Collection<Integer> menuIds)` (blank-skipping, empty-in→empty-out) for the
  Batch 3 login/me perm set.

### Tests (all extend the Batch 1 `AuthIntegrationTestBase`, `@Import(AuthTestStubConfig.class)`)

- `AuthSchemaMigrationTests` - information_schema existence of the 7 tables; per-table
  column sets (id first, business columns, audit block asserted AS THE TAIL in exact
  order via ordinal_position); the 5 partial-unique indexes (unique + indexed columns +
  `deleted = 0` clause in indexdef); FK counts EXACTLY per binding design (unit_info
  deliberately 0 - encodes the no-self-FK rule).
- `AuthSeedMigrationTests` - root unit row, 3 department codes anchored to unit 1,
  admin with must_change_password=1/enable=1 AND live `BCryptPasswordEncoder.matches(
  "admin123", seededHash)`, role 1 (type 10) + user_role binding, menus 1-6 with types
  and parent links, role 1 owning all six menus incl. the audit perms string
  (CONTAINS-style lower bounds; shared-DB safe).
- `AuthDataLayerTests` - mapper smoke round trip (insert timestamp-suffixed user →
  select back → assert AUTO id backfill > 1, DB-default deleted=0/create_time → logical
  delete hides the select while the physical row shows deleted=1); unit-tree round trip
  (temp child under root 1 nests, then logical cleanup); menu-tree round trip from seed
  rows (roots 1/3/4/5, menu 2 under 1, menu 6 under 3, perms collected);
  `permsSet([2,6])` + empty-in→empty-out.

## TDD evidence

All runs: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
then `cd lumen-sev && ./mvnw -pl lumen-auth -am test -q` (reactor-only, from the
aggregator).

### 2.1 RED (before any migration existed)

Surefire (exit non-zero - see self-review note 5 on the exit capture):
```
[ERROR] Tests run: 5, Failures: 4, Errors: 0, Skipped: 0  <<< FAILURE! - in org.birdbro.lumen.auth.AuthSchemaMigrationTests
org.opentest4j.AssertionFailedError: table auth.unit_info must exist ==> expected: <1> but was: <0>
org.opentest4j.AssertionFailedError: auth.unit_info must carry [id, up_id, level, type, name, deleted, create_by, create_time, update_by, update_time] but had []
org.opentest4j.AssertionFailedError: partial-unique index uk_department_code must exist on auth schema ==> expected: <1> but was: <0>
org.opentest4j.AssertionFailedError: auth.department must carry exactly 1 foreign keys (unit_info deliberately has NO self-FK) ==> expected: <1> but was: <0>
```
(Flyway validated 0 migrations - `No migrations found` WARN - and the context loaded,
proving the failures are the missing schema, not wiring.)

### 2.1 GREEN (V1 added)

```
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "auth", now at version v1 (execution time 00:01.454s)
surefire: AuthApplicationTests Tests run: 1, Failures: 0 ; AuthSchemaMigrationTests Tests run: 4, Failures: 0, Errors: 0
MVN_EXIT=0
```

### 2.2 RED (seed assertions before V2 existed; V1 already applied)

```
MVN_EXIT=1
[ERROR] Tests run: 10, Failures: 0, Errors: 5, Skipped: 0  <<< FAILURE! - in org.birdbro.lumen.auth.AuthSeedMigrationTests
org.springframework.dao.EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0
  (all five seed tests: hospitalRootUnitSeeded / threeDepartmentsSeededUnderTheRoot /
   defaultAdministratorSeeded / defaultRoleAndUserBindingSeeded / menuSetAndRoleMenuSeeded)
```
Schema tests stayed green in the same run (tables exist; rows missing - the designed
delta).

### 2.2 GREEN (V2 added; hash pre-verified first)

Before V2 ever touched the shared DB, the coordinator's hash was re-verified locally
against the exact train artifact (spring-security-crypto 5.5.8, source-file launch from
/tmp):
```
matches(admin123, seedHash) = true
```
Then:
```
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "auth", now at version v2 (execution time 00:01.245s)
surefire: 1 + 4 + 5 tests, 0 failures, 0 errors
MVN_EXIT=0
```
(The live `BCryptPasswordEncoder.matches("admin123", ...)` assertion inside
`defaultAdministratorSeeded` passed against the row Flyway actually inserted.)

### 2.3 RED (data-layer test written first, classes missing)

```
MVN_EXIT=1
[ERROR] COMPILATION ERROR :
[ERROR] .../AuthDataLayerTests.java:[9,37] 程序包org.birdbro.lumen.auth.entity不存在
[ERROR] .../AuthDataLayerTests.java:[11,37] 程序包org.birdbro.lumen.auth.mapper不存在
[ERROR] .../AuthDataLayerTests.java:[13,38] 程序包org.birdbro.lumen.auth.service不存在
[ERROR] .../AuthDataLayerTests.java:[49,13] 找不到符号 ...
```
(Typed-language RED: the test names the API before it exists.)

### 2.3 GREEN (entities + mappers + services implemented)

```
surefire: AuthDataLayerTests Tests run: 4, Failures: 0, Errors: 0
MVN_EXIT=0
```

### Final full run (after cosmetic import tidy; re-run also proves shared-DB re-runnability)

```
MVN_EXIT=0    (0 ERROR lines in the log)
Tests run: 1 (AuthApplicationTests) + 4 (AuthSchemaMigrationTests)
         + 5 (AuthSeedMigrationTests) + 4 (AuthDataLayerTests) = 14, all green
```
Flyway misbehavior check: the ONLY warning is the known benign
`Flyway upgrade recommended: ... 14.11 is newer than this version of Flyway ... latest
supported version ... is 13` - no other WARN/ERROR, nothing beyond the known signal, so
no version-pin escalation was needed.

## Files changed (commit c94d004, 22 files, +1227)

- `lumen-sev/lumen-auth/src/main/resources/db/migration/V1__auth_schema.sql` (new)
- `lumen-sev/lumen-auth/src/main/resources/db/migration/V2__seed.sql` (new)
- `lumen-sev/lumen-auth/src/main/resources/db/migration/.gitignore` (new - see concerns 1)
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/entity/{UnitInfo,Department,UserInfo,Role,UserRole,MenuInfo,RoleMenu}.java` (new, 7)
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/mapper/{UnitInfoMapper,DepartmentMapper,UserInfoMapper,RoleMapper,UserRoleMapper,MenuInfoMapper,RoleMenuMapper}.java` (new, 7)
- `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/service/{UnitTreeService,MenuTreeService}.java` (new, 2)
- `lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/{AuthSchemaMigrationTests,AuthSeedMigrationTests,AuthDataLayerTests}.java` (new, 3)

Nothing outside `lumen-sev/lumen-auth` (framework/common verbatim guarantee intact;
`git diff HEAD` empty besides the new files; `@author birdbro` verified in every new
file; comments English, Chinese only as product display data).

## Self-review findings

1. **V2 sequence advance is load-bearing, not gold-plating**: seed inserts use explicit
   ids while ids are `serial` (IdType.AUTO = nextval on the application side). Without
   `setval(...)` past `MAX(id)`, the first application insert per table would hit a
   duplicate-key on id 1. The smoke test asserts `user.getId() > 1` to pin this.
2. **Mapper declaration style**: the brief said "`@Mapper`-free style ... check how
   framework's LogInfoMapper declares itself and match" - the two clauses conflict;
   LogInfoMapper (the named precedent) declares `@Mapper`, so the mappers carry `@Mapper`
   (harmless under `@MapperScan("org.birdbro.**.mapper")`, which covers them either
   way). Decision recorded here for review.
3. **Logical delete chosen for the smoke test cleanup** (and the unit-tree child):
   `deleteById` with EntityBase's `@TableLogic` issues `UPDATE ... SET deleted = 1`;
   asserted both directions (select-back returns null AND the physical row holds
   deleted=1). Partial-unique indexes make the leftover rows non-blocking for
   re-creation - consistent with the binding DDL rationale.
4. **Shared-DB discipline**: all assertions keyed (id/username/code) or lower-bound
   (`>=`), never exact counts; inserted rows use timestamp-suffixed unique names;
   tests re-run green against the already-migrated shared DB (proven by the final run).
5. **Exit-code capture on the 2.1 RED run**: the `... | tail` pipe swallowed the mvn
   exit status (echoed the pipe's exit). The failure evidence stands on the surefire
   summary + `[ERROR]` lines quoted above; every subsequent run redirected to a file
   and echoed `MVN_EXIT` correctly.
6. **UserInfo.password has no `@JsonIgnore`** (kept exactly to the brief's entity
   design): any JSON exposure happens in Batch 3's login/me, which should map to a
   profile DTO, not the entity. Watch-item for Batch 3 review.
7. MetaObjectConfig audit filling is a no-op in tests without a request/security
   context (`SecurityUtils.getUserHeader()` swallows and returns null), so mapper
   inserts relied on DB defaults for the audit block - asserted via
   `deleted=0`/`create_time` non-null on read-back.

## Issues or concerns

1. **Global gitignore hides `.sql` (fixed repo-locally; one pre-existing casualty
   flagged)**: the developer machine's global gitignore (`~/.gitignore_global:17:
   *.sql`) silently excluded the Flyway migrations from `git status` - an uncommitted
   migration would have broken every fresh clone (schema changes only via Flyway).
   Fixed with a directory-scoped repo negation
   (`db/migration/.gitignore` containing `!*.sql`, the highest-precedence level;
   verified via `git check-ignore -v` now resolving to the negating rule). The same
   global rule has ALREADY been hiding `docs/deployment/pg-init.sql` - that file exists
   on disk but is untracked, while the archived data spec references it as the
   environment contract. Out of my batch scope: coordinator should re-include/commit it
   (e.g. extend the negation or `git add -f`).
2. **Recorded evidence note - department.code rationale (task 2.1)**: `code` is the
   stable identifier (radiology/us/endo) that `lumen-integration` routes DICOM/HL7 by;
   it is therefore NOT NULL and partially-unique (`WHERE deleted = 0`) so a logically
   deleted department never blocks re-creating its code, while the in-service duplicate
   rejection remains the A-class path.
3. **Recorded evidence note - menu-column trims (design D5 resolution, task 2.1)**:
   `menu_info` is trimmed to lumen needs - NO query/redirect/app/breadcrumb/action/
   is_cache/is_frame columns. The copied `MenuTree` POJO keeps those fields (verbatim,
   untouched); `MenuTreeService` leaves them null. If the frontend later needs
   redirect/cache/frame behavior, that is a new proposal, not silent column creep.
4. Seed hash shipped as-is (coordinator-verified; re-verified pre-apply and asserted
   live in the seed test) - no regeneration was needed.
5. Shared test DB accumulates the smoke rows (`mp.smoke.*` users, `ut.smoke.*` units,
   all deleted=1) - intended per design D3's risk note (test data lives in the test
   DB); nothing blocks future batches.
