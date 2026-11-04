# Batch 1 Report — rename-auth-to-system（tasks 1.1, 1.2）

> Status: DONE · Commit: `8d73970` · 2026-11-03 · Executor: backend implementer agent

## 摘要

测试服务器 schema `auth` → `system` 已幂等执行并验证（flyway 历史随迁、checksum 不变）；
`lumen-auth` 全部测试基座（bootstrap / 动态 URL / 测试 SQL 断言）与配置基座
（application.yml flyway.schemas、dev 文件 + 模板 currentSchema）同步改到 `system`；
`docs/deployment/pg-init.sql` 全文更名（schema + 角色模板 `lumen_system_role` + GRANT 段）。
全量测试绿。

## 1. RED / GREEN 证据（AuthSchemaMigrationTests）

- **RED（ALTER 前，断言已指向 `system`，DB 仍是 `auth`）**：
  `./mvnw test -q -pl lumen-auth -Dtest='AuthSchemaMigrationTests' -DfailIfNoTests=false`
  → `Tests run: 4, Failures: 4, Errors: 0`，MVN_EXIT=1。失败样本：
  `table system.org_info must exist ==> expected: <1> but was: <0>`；
  同一上下文日志 `Current version of schema "auth": 3` 证明当时数据仍在 auth。
- **GREEN（ALTER 后，同命令）**：MVN_EXIT=0，4/4 绿；
  日志 `Successfully validated 3 migrations` +
  `Current version of schema "system": 3` +
  `Schema "system" is up to date`。

## 2. ALTER SCHEMA 幂等证据（一次性 JDBC 工具，/tmp/rename-auth-schema）

- 首跑：`[pre] authExists=true systemExists=false` → `ALTER SCHEMA auth RENAME TO system`
  执行 → `[post] systemExists=true authExists=false`。
- **checksum 冻结验证**：Flyway 校验即权威比对——ALTER 前（RED run，对 auth 历史）与
  ALTER 后（GREEN run，对 system 历史）对同一批迁移文件（V1/V2/V3，`git diff` 零改动）
  均 `Successfully validated 3 migrations` → 历史 checksum 未变。
  `system.flyway_schema_history` 3 行全部 `success=true`：
  V1 `auth schema` checksum=-1198603292 / V2 `seed` 15131651 / V3 `system data` -1617003236。
- **幂等重跑**（工具二次执行）：`schema 'auth' not found - already renamed, idempotent pass`
  → `seven RBAC tables present in system schema: 7/7`，EXIT=0。
- 凭据经 `AUTH_TEST_*` env 注入（source gitignored `.env-test.local`），全程未打印。

## 3. schema 前缀清扫清单（`auth.` / `'auth'` → `system.` / `'system'`）

| 文件 | 处数（约） |
|:--|:--|
| AuthApplicationTests | current_schema()=="system"、system.flyway_schema_history + javadoc |
| AuthSchemaMigrationTests | table_schema/schemaname/nspname 四处 + 断言消息 + javadoc |
| AuthSeedMigrationTests | 9 条 SQL 前缀 + javadoc |
| AuthDataLayerTests | table_schema='system'、currentSchema 注释、user_info 查询、`inAuth`→`inSystem` |
| AuthAuditIdentityTests | 4 条 user_info SQL |
| AuthRoleMenuTests | 8 条 SQL（SELECT/UPDATE role_info/user_role/role_menu/user_info） |
| AuthOrgCrudTests / AuthOrgDepTests | org_info/org_dep UPDATE |
| AuthSignLogTests / AuthOperationLogTests / AuthUserManagementTests | user_info / org_info UPDATE |
| AuthIntegrationTestBase | currentSchema=system,public + flyway 属性注释 |
| AuthTestServerBootstrap | AUTH_SCHEMA="system"（含 CREATE SCHEMA IF NOT EXISTS） |
| application.yml / application-dev.yml.example | flyway schemas/default-schema、currentSchema |
| application-dev.yml（gitignored，未提交） | currentSchema=public,system（**顺序保持并行会话写入的 public,auth 现状**，仅改 schema 名） |
| docs/deployment/pg-init.sql | CREATE SCHEMA system + 角色块/GRANT 段 `lumen_system_role`（8 处）+ ON SCHEMA system |

未动：`public.` 前缀、`Auth*` 类名、`auth-rbac` 字样、`lumen-auth` 模块名、
`/api/v1/auth/**` 路径（Batch 2/3 范围）。
复查 `rg -n "\bauth\b" lumen-sev/lumen-auth/src`：schema 语义残留为零。

## 4. 偏差与说明

1. **全量计数 50（非 brief 预估 51+）**：仓库全部测试均在 lumen-auth（15 个测试类，
   common/framework 的 src/test 为空占位）。16:33 `./mvnw clean test` →
   **Tests run: 50, Failures: 0, Errors: 0, Skipped: 0，MVN_EXIT=0**。
   "51" 来自旧 target 里已被 Batch 0 快照删除的 `TempFilterProbeTests` 陈旧报告（1 例）；
   clean 后真值 50。另有 16:36 `-pl lumen-auth clean test` 复跑亦 MVN_EXIT=0。
2. **包私有类名 ≠ 文件名（既有事实，未改动）**：`AuthOrgDepTests.java` 声明
   `class AuthDepartmentTests`、`AuthOrgCrudTests.java` 声明 `class AuthUnitCrudTests`
   （cdc58d2 时代只改了文件名）。surefire 按类名发现并运行——不影响本批正确性，
   Batch 2 模块更名时建议顺手对齐。
3. **冻结件刻意保留 "auth" 字样**：`V1__auth_schema.sql`/`V3__system_data.sql` 内注释
   （checksum 冻结纪律，改动即 Flyway 校验失败）；`[auth-test-bootstrap]` 日志标签
   （类名派生，非 schema 语义）。
4. **并行会话零接触**：仅提交本批 17 个文件；loop-state.yaml / progress.md /
   web-foundation-auth-console / b2 brief 均未触碰。gitignored 的
   `.env-test.local`、`application-dev.yml` 确认未入库（check-ignore 验证）。
5. 首版一次性 JDBC 工具的 checksum 相等判断有 bug（`Map<Integer,long[]>` 按数组身份比较）
   ——已在 ALTER 之后修复并二次运行验证；ALTER 本身一次成功，无副作用。

## 5. 完成标准对照

- [x] RED（4/4 fail）→ ALTER（幂等，历史随迁 checksum 不变）→ GREEN（4/4 pass）
- [x] 全量 `./mvnw clean test` MVN_EXIT=0（50/50 绿）
- [x] `rg "\bauth\b" lumen-auth/src`：schema 语义清零（仅 Batch 2/3 范围残留）
- [x] `rg -c "lumen_auth_role|CREATE SCHEMA IF NOT EXISTS auth" docs/deployment/pg-init.sql` = 0
- [x] commit `8d73970`（17 files, +78/-76）；gitignored 文件未入库
