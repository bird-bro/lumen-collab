# Batch 2 Review - Flyway migrations + data layer (tasks 2.1-2.3)

Reviewer: code-reviewer agent | Reviewed range: `8498e1e..c94d004` | Date: 2026-11-02
Inputs: implementer report `openspec/sdd/reports/auth-b2-implementer.md`, batch brief
`openspec/changes/auth-rbac-foundation/batches/batch-2.md`, spec deltas
`specs/auth-rbac/spec.md` + `specs/data/spec.md`.

### Spec Compliance

- **Compliant** on all seven review axes. Every binding DDL column/type/default was
  compared 1:1 against the brief; every seed tuple was compared 1:1; the full suite was
  independently re-run by the reviewer against the team test server (MVN_EXIT=0, 14/14
  green: 1 context + 4 schema + 5 seed + 4 data-layer; only the known benign
  "Flyway upgrade recommended ... 14.11 newer than 13" warning, 0 ERROR lines).

1. **DDL vs binding design** - exact. Seven tables
   (`V1__auth_schema.sql:18,34,50,66,80,96,116`); serial PKs on all; audit block as the
   LAST five columns in exact order on all seven (asserted in the test via
   ordinal_position tail); all five partial-unique indexes `WHERE deleted = 0`
   (`uk_department_code`:46, `uk_user_info_username`:63, `uk_role_name`:77,
   `uk_user_role_user_role`:91, `uk_role_menu_role_menu`:127); department.unit_id FK
   (:36), user_role/role_menu both FKs each (:82-83, :118-119); unit_info deliberately
   NO self-FK (FK-count test pins exactly 0 - encodes the rule as a regression guard);
   menu_info trimmed to up_id/type/title/perms/path/component/icon/sort/visible - none
   of query/redirect/app/breadcrumb/action/is_cache/is_frame; plain indexes exactly the
   binding set (idx_unit_info_up_id, idx_department_unit_id, idx_user_role_user_id,
   idx_menu_info_up_id, idx_role_menu_role_id), no extras. Column types/nullability
   match the brief on every table (incl. nullable user_info.name / role.remark).
2. **Seed vs binding design** - exact. Explicit ids; unit 1 示例医院 up 0/level 1/type 0
   (`V2__seed.sql:9-10`); departments 1-3 radiology/放射科, us/超声科, endo/内镜科 under
   unit 1 (:14-17); admin id 1 with the brief's exact BCrypt hash
   `$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m`,
   must_change_password=1, enable=1, 系统管理员 (:22-23); role id 1 系统管理员 type 10
   with the brief's remark (:26-27); user_role 1 (1→1) (:29-30); menus 1-6 exactly per
   brief incl. menu 6 (up 3, F, perms `radiology:report:audit`) (:35-41); role_menu
   role 1 → menus 1-6 (:44-50).
   **setval block verified correct** (:55-61): it runs AFTER all seed inserts,
   `setval(pg_get_serial_sequence(<table>,'id'), MAX(id))` with is_called=true means the
   next nextval returns MAX+1, so the first application-side IdType.AUTO insert cannot
   collide with the seeded ids (pinned by the smoke test asserting `user.getId() > 1`).
   On the shared DB it is self-correcting (MAX(id) covers any pre-existing rows).
   Future-migration caveat recorded under Minor 3.
3. **Entities/mappers** - compliant. `@TableName` explicit on all seven entities
   (guards against the `userinfo` auto-map trap); `@TableId(value = "id",
   type = IdType.AUTO) Integer id` on all seven; all extend
   `org.birdbro.common.core.base.EntityBase` with zero redeclared audit fields (checked
   each file); `@Data` + `@EqualsAndHashCode(callSuper = true)`; camelCase properties.
   Mappers `BaseMapper<X>` with `@Mapper`, matching the named framework precedent
   (`framework/.../LogInfoMapper.java:18` declares `@Mapper`) - the brief's two clauses
   ("@Mapper-free style" vs "match LogInfoMapper") conflicted; the implementer matched
   the named precedent and recorded the decision; `@MapperScan("org.birdbro.**.mapper")`
   covers it either way. Entities stay pure - no DTO leakage; `UnitInfo.children` is
   `@TableField(exist = false)` per brief.
4. **Tree services** - compliant. Both services use `TreeUtils.buildTree` with
   rootParentValue 0 (`UnitTreeService.java:48-50`, `MenuTreeService.java:62-64`);
   javadoc documents the actual TreeUtils semantics (roots = upId 0, orphans dropped,
   leaf children null) - accurate against
   `common/src/main/java/org/birdbro/common/utils/TreeUtils.java:45-73`. MenuTree POJO
   mapping complete: id→mid, upId, type, title, perms, path, component, icon, sort, and
   visible Integer→Boolean (`Integer.valueOf(1).equals(...)`, `MenuTreeService.java:99`);
   trimmed POJO fields stay null. `permsSet(menuIds)` is scoped per the brief ("Also
   `permsSet(menuIds)` or equivalent"): blank-skipping (:81-83), dedup via HashSet,
   empty-in→empty-out (:76-78); `deleted = 0` on menu_info is enforced by EntityBase's
   `@TableLogic` auto-appended to `selectBatchIds`. The user_role→role_menu collection
   half of the perm chain is Batch 3 scope by design (javadoc states the contract) -
   Batch 3 must apply `deleted = 0` on BOTH the user_role and role_menu joins when
   collecting menu ids (review pointer, not a defect).
5. **Tests** - compliant and re-proven. Real information_schema/pg_indexes/pg_constraint
   assertions, not smoke; shared-DB safe (keyed or lower-bound row assertions; the only
   exact counts are schema-shape - FK counts, index existence - which Flyway owns);
   unique timestamp-suffixed inserted data; logical-delete cleanup asserted in both
   directions (select-back null AND physical row deleted=1). Seed test runs a live
   `BCryptPasswordEncoder.matches("admin123", seededHash)` against the row Flyway
   actually inserted. RED→GREEN evidence quoted verbatim in the report for all three
   tasks, including the honest self-disclosure of the 2.1 RED exit-code pipe swallow
   (evidence stands on the surefire summary + [ERROR] lines).
6. **Verbatim discipline** - intact. `git diff --name-only 8498e1e..c94d004` contains
   zero framework/ or common/ paths; all 22 files live under `lumen-sev/lumen-auth`;
   `@author birdbro` present in every new file; comments English with Chinese only as
   product display data.
7. **Migration hygiene** - compliant. V1/V2 plain SQL, idempotency-by-Flyway (plain
   single-statement INSERTs, no ON CONFLICT / IF NOT EXISTS, per the brief's hard
   context); no destructive DDL anywhere. Charset safe: V2 is UTF-8 without BOM (`file`
   verified), Flyway 7.7.3 default encoding is UTF-8, and pgjdbc always negotiates
   UTF-8 client_encoding - Chinese stores correctly, proven live by the seed test's
   equality assertions on 示例医院/放射科/超声科/内镜科 on the team server (and by the
   reviewer's re-run). The global-gitignore `*.sql` issue is accepted per coordinator
   (the `db/migration/.gitignore` negation verified working: `git check-ignore` exits
   non-ignore for both migrations; pg-init.sql separately resolved by `d00fd96`).

### Strengths

- The schema test is a genuine contract pin, not theatre: audit-block column ORDER is
  asserted as the tail, the partial-unique clause is asserted inside `pg_indexes.indexdef`,
  and the unit_info FK-count-0 assertion converts the "no self-FK" design decision into a
  regression guard.
- The seed test proves the spec's "Fresh deployment can log in" scenario at the data layer
  before Batch 3 exists (live BCrypt matches against the Flyway-inserted row), and the
  hash was re-verified against the exact train artifact (spring-security-crypto 5.5.8)
  BEFORE V2 ever touched the shared DB - correct ordering for a one-shot migration.
- The setval addition is load-bearing and correctly justified (explicit-id inserts vs
  serial/nextval), with the smoke test pinning `id > 1` so a regression would fail loudly.
- Honest self-review: the RED-run exit-code capture flaw and the @JsonIgnore watch-item
  are volunteered, not discovered.
- Shared-DB discipline is consistent end-to-end: keyed/lower-bound assertions,
  timestamp-suffix data, logical cleanup asserted both directions, and re-runnability
  demonstrated (the reviewer's re-run also ran against the already-migrated shared DB).

### Issues

#### Critical (Must Fix)

None.

#### Important (Should Fix)

None.

#### Minor (Nice to Have)

1. **`UserInfo.password` has no `@JsonIgnore`** (`entity/UserInfo.java:31`). Kept exactly
   to the brief's entity design and already flagged by the implementer as a Batch 3
   watch-item - recording it here so Batch 3 review checks that login/me map to a profile
   DTO and never serialize the entity (a hash leak, not a credential leak, but still).
2. **`permsSet` empty-case mutability inconsistency** (`service/MenuTreeService.java:77`
   vs :79): returns the immutable `Collections.emptySet()` when input is empty but a
   mutable `HashSet` otherwise. If Batch 3 code ever mutates the returned set (e.g. adds a
   wildcard perm), the empty path throws `UnsupportedOperationException` on a rare branch.
   Returning `new HashSet<>()` in both paths would make the contract uniform.
3. **Future seed migrations must repeat the setval tail**: V2 advances each sequence past
   `MAX(id)` at V2-apply time; it cannot anticipate later migrations. Any future
   migration that INSERTs with explicit ids must carry its own setval (rows inserted
   without explicit ids are fine - nextval continues from the advanced sequence).
   Suggested home: one line in the batch-3 brief or the change's coordination notes.
4. **BCryptPasswordEncoder reaches the seed test only transitively** (via
   spring-boot-starter-security pulled by framework/common - `framework/pom.xml:33`,
   `common/pom.xml:30`). Stable because framework is a permanent dependency, but an
   explicit test-scoped `spring-security-crypto` declaration in `lumen-auth/pom.xml`
   would make the test's dependency explicit.
5. **Schema literal duplication in tests**: `AuthSchemaMigrationTests`/
   `AuthSeedMigrationTests`/`AuthDataLayerTests` hardcode `'auth'` in SQL strings while
   `AuthTestServerBootstrap.AUTH_SCHEMA` (package-private, same package) exists - a
   rename of the test schema constant would leave stale literals. Cosmetic.
6. **Environment-contract observation (out of batch scope, coordinator-owned)**:
   `docs/deployment/pg-init.sql:5` creates the `lumen` database without an explicit
   `ENCODING 'UTF8' TEMPLATE template0` - on a cluster initdb'd with a non-UTF8 default
   (possible in some machine-room/legacy installs), V2's Chinese seed data would fail or
   corrupt. The team server is proven UTF-8, so no action for this batch; consider pinning
   the encoding when the DBA handoff is finalized.
7. **Timestamp-suffix uniqueness** (`AuthDataLayerTests.java:63,102`) can theoretically
   collide across two concurrent JVM runs against the shared DB (the partial-unique index
   would then fail the insert). The brief prescribed exactly this pattern, so accepted;
   noted only for completeness.

### Assessment

Task quality: **Approved**

Reasoning: Every binding DDL column and seed tuple was verified 1:1 against the brief and
the implementation is exact, with the implementer's setval addition proven correct and
load-bearing; the reviewer's independent full-suite re-run against the team test server
returned exit 0 with 14/14 green on the shared (already-migrated) database. All findings
are Minor: two Batch 3 watch-items the implementer already recorded, plus polish-level
notes - nothing blocks proceeding to Batch 3.
