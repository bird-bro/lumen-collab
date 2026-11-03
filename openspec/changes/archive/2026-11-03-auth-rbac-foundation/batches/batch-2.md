# Batch 2 Brief: Flyway migrations + data layer (tasks 2.1-2.3)

## Goal

Seven-table auth schema + seed data as Flyway migrations, then the entity/mapper layer and
tree assembly services - all proven against the team test server via the Batch 1 base class.

## Task text (tasks.md 2.1-2.3)

- **2.1** `V1__auth_schema.sql`: seven tables `unit_info` (upId/level/type), `department`
  (FK unit), `user_info` (username unique, password hash, must_change_password), `role`,
  `user_role`, `role_menu`, `menu_info` (upId/type/perms/path/component/sort).
  Verify: assert the seven tables exist (information_schema) in a test.
- **2.2** `V2__seed.sql`: hospital root unit, three departments (radiology/us/endo FK unit),
  default administrator (BCrypt password + must-change-password), base roles, minimal menu
  set (login/me + user management + three department page placeholders + audit button
  example). Verify: post-migration query asserts seed rows exist.
- **2.3** Entities + Mappers (`org.birdbro.lumen.auth.entity/mapper`): seven entities extend
  `EntityBase` (id as each entity's `@TableId Integer AUTO`), Mapper interfaces; `MenuTree`
  assembly service. Verify: mapper smoke test (insert/select round trip) green.

## Hard context

- Batch 1 delivered `org.birdbro.lumen.auth.AuthIntegrationTestBase` (team test server,
  bootstrap, `currentSchema=auth`, Flyway enabled). Extend it; do NOT rewire.
- NEVER edit `lumen-sev/framework/` or `lumen-sev/common/` (verbatim). New code only in
  `lumen-auth` (`org.birdbro.lumen.auth.{entity,mapper,service}`). `@author birdbro`
  headers, English comments, kebab-case file names. `@author` header format: match
  existing files (e.g. `@author birdbro` javadoc).
- JAVA_HOME: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
  before every `./mvnw` run. Build from `lumen-sev/` only.
- **Shared DB**: the test server's `lumen` DB is long-lived. Tests must tolerate
  pre-existing rows (assert CONTAINS the seeded rows, never exact counts) and use unique
  usernames (e.g. timestamp suffix) for anything they insert.
- Flyway applies each versioned migration once (schema history lives in
  `auth.flyway_schema_history`). Seed inserts must be plain INSERTs with explicit ids -
  they run exactly once by Flyway semantics.

## Binding DDL design (coordinator-decided from design D1/D2/D5)

All seven tables carry the EntityBase audit block (exact columns, same order):
`deleted integer NOT NULL DEFAULT 0`, `create_by integer`, `create_time timestamp NOT NULL
DEFAULT CURRENT_TIMESTAMP`, `update_by integer`, `update_time timestamp NOT NULL DEFAULT
CURRENT_TIMESTAMP`. Ids are `serial PRIMARY KEY` (Integer AUTO, design D1).

- `unit_info`: `up_id integer NOT NULL DEFAULT 0` (0 = root - the TreeUtils
  rootParentValue convention), `level integer NOT NULL DEFAULT 1`, `type integer NOT NULL
  DEFAULT 0` (lumen-owned; 0 = hospital root), `name varchar(128) NOT NULL`. Index on
  `up_id`. NO self-FK (root rows use up_id=0; tree integrity is service-level).
- `department`: `unit_id integer NOT NULL REFERENCES unit_info(id)`, `code varchar(32)
  NOT NULL` with partial-unique `WHERE deleted = 0` (stable identifier radiology/us/endo -
  `lumen-integration` routes DICOM/HL7 by it; recorded in task evidence), `name
  varchar(128) NOT NULL`. Index on `unit_id`.
- `user_info`: `username varchar(64) NOT NULL` with partial-unique `WHERE deleted = 0`
  (logical delete must not block re-creating a username; the service-level duplicate
  rejection is the A-class path, the index is the backstop), `password varchar(100) NOT
  NULL` (BCrypt hash), `must_change_password integer NOT NULL DEFAULT 0`, `enable integer
  NOT NULL DEFAULT 1`, `name varchar(64)`.
- `role`: `name varchar(64) NOT NULL` (partial-unique WHERE deleted = 0), `type integer
  NOT NULL DEFAULT 11` (RoleConstants lineage: 10 system / 11 business), `remark
  varchar(255)`.
- `user_role`: `user_id integer NOT NULL REFERENCES user_info(id)`, `role_id integer NOT
  NULL REFERENCES role(id)`; partial-unique `(user_id, role_id) WHERE deleted = 0`; index
  on user_id.
- `menu_info`: `up_id integer NOT NULL DEFAULT 0`, `type varchar(1) NOT NULL DEFAULT 'C'`
  (M directory / C menu / F button - the MenuTree POJO lineage), `title varchar(64) NOT
  NULL`, `perms varchar(128)`, `path varchar(128)`, `component varchar(128)`, `icon
  varchar(64)`, `sort integer NOT NULL DEFAULT 0`, `visible integer NOT NULL DEFAULT 1`;
  index on `up_id`. (Design D5 open question resolution: trimmed to lumen needs - no
  query/redirect/app/breadcrumb/action/is_cache/is_frame columns; recorded in task
  evidence.)
- `role_menu`: `role_id integer NOT NULL REFERENCES role(id)`, `menu_id integer NOT NULL
  REFERENCES menu_info(id)`; partial-unique `(role_id, menu_id) WHERE deleted = 0`; index
  on role_id.

## Binding seed design (V2, explicit ids for stable FK references)

- unit id 1: hospital root - name `示例医院` (Chinese display strings are product data;
  SQL comments stay English), up_id 0, level 1, type 0.
- departments 1-3: (`radiology`,`放射科`) (`us`,`超声科`) (`endo`,`内镜科`), unit_id 1.
- user id 1: `admin` / BCrypt hash below / must_change_password 1 / enable 1 / name
  `系统管理员`. Hash (spring-security-crypto 5.5.8, verified `matches("admin123", hash)`
  = true by the coordinator):
  `$2a$10$/WMgAykBpAYjFFYxZkttUuOkA7HOeSpkUtPTosdRPvIE.uKFYeK2m`
  (initial credentials `admin`/`admin123`; you may regenerate with a quick
  BCryptPasswordEncoder run if preferred - then update the hash here and in the report.)
- role id 1: name `系统管理员`, type 10, remark `seed default administrator role`.
- user_role id 1: user 1 → role 1.
- menu ids 1-6:
  (1, up 0, M, `系统管理`, path `/system`, icon `setting`, sort 1),
  (2, up 1, C, `用户管理`, perms `system:user:list`, path `user`, component
  `system/user/index`, sort 1),
  (3, up 0, C, `放射科`, path `/radiology`, component `radiology/index`, sort 2),
  (4, up 0, C, `超声科`, path `/us`, component `us/index`, sort 3),
  (5, up 0, C, `内镜科`, path `/endo`, component `endo/index`, sort 4),
  (6, up 3, F, `报告审核`, perms `radiology:report:audit`, sort 1).
- role_menu: role 1 → menus 1-6.

## Entity/Mapper design (2.3)

- Seven entities in `org.birdbro.lumen.auth.entity`: `UnitInfo`, `Department`, `UserInfo`,
  `Role`, `UserRole`, `RoleMenu`, `MenuInfo` - each `extends EntityBase`
  (`org.birdbro.common.core.base.EntityBase`), `@TableName("<snake table>")` EXPLICIT on
  every entity (UserInfo would auto-map to `userinfo` - wrong), `@TableId(value = "id",
  type = IdType.AUTO) private Integer id`, Lombok `@Data`, properties camelCase
  (upId/mustChangePassword/...). EntityBase brings deleted/createBy/createTime/updateBy/
  updateTime - do not redeclare.
- Seven mappers in `org.birdbro.lumen.auth.mapper`: `interface XMapper extends
  BaseMapper<X>` with `@Mapper`-free style (AuthApplication's
  `@MapperScan("org.birdbro.**.mapper")` covers them) - check how framework's
  LogInfoMapper declares itself and match.
- Assembly services in `org.birdbro.lumen.auth.service`:
  - `UnitTreeService`: flat `unit_info` list → tree via
    `org.birdbro.common.utils.TreeUtils.buildTree` (rootParentValue 0). Node type: the
    entity itself (add `@TableField(exist = false) private List<UnitInfo> children;`) -
    EntityBase subclasses are the tree nodes.
  - `MenuTreeService`: flat `menu_info` list → `List<MenuTree>`
    (`org.birdbro.common.core.pojo.user.MenuTree` - FIRST USE of the copied POJO) via
    TreeUtils.buildTree; map id→mid, title, upId, type, perms, path, component, icon,
    sort, visible (Integer 1/0 → Boolean). Also `permsSet(menuIds)` or equivalent so the
    login/me endpoints can produce the user's perms Set<String>.

## TDD sequence (red first, per task)

1. Write the schema test (seven tables via information_schema) → run → RED (tables
   missing). Then add `V1__auth_schema.sql` → GREEN.
2. Add seed assertions (root unit, 3 department codes, admin with
   must_change_password=1, role binding, 6 menus, role 1 owns `radiology:report:audit`)
   → RED → add `V2__seed.sql` → GREEN.
3. Mapper smoke test: insert a UserInfo with a timestamp-suffixed username via
   UserInfoMapper, select it back (assert round trip), then delete it (logical or
   physical - your call; note it) → GREEN. Also one round trip through UnitTreeService /
   MenuTreeService (tree assembles from seed rows; MenuTree contains the audit perms
   string).

Run: `cd lumen-sev && ./mvnw -pl lumen-auth -am test -q`.

## Commit

`✨ feat(sev): Batch 2 - auth schema + seed migrations, entities + mappers + tree assembly (auth-rbac-foundation 2.1-2.3)`

## Report

Write to `openspec/sdd/reports/auth-b2-implementer.md`: what you implemented, TDD
evidence (RED + GREEN per task), files changed, self-review, concerns (include the
recorded evidence notes the tasks demand: code-column rationale, menu-column trims).
