# Batch 1 Brief — rename-auth-to-system（tasks 1.1, 1.2）

## 环境铁律
- 每条 mvn/psql 命令前：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`
- 显式 `cd /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev`（绝对路径）
- 测试服务器凭据在 gitignored `lumen-sev/.env-test.local`（AUTH_TEST_*）；本批的
  ALTER SCHEMA 用一次性 JDBC 工具执行（参照既有做法：写 /tmp 下的小 Java + 本地
  ~/.m2 的 postgresql-42.2.18.jar，凭据经 env 传递不落日志）
- 模块已由 Batch 0 快照（9839652）清树——工作区应只剩 loop-state.yaml 一处未提交

## 任务 1.1 — 测试服务器 schema 更名 + 配置基座

1. **幂等 ALTER**：一次性 JDBC 连接（连 `lumen` 库）执行：
   `ALTER SCHEMA IF NOT EXISTS auth RENAME TO system` — 注意 PG 的 ALTER SCHEMA 无
   IF EXISTS 支持：先查 pg_namespace 确认 `auth` 存在再 ALTER（存在→改名；不存在→
   验证 `system` 已存在即幂等通过，打印判定结果）。
   改名后校验：`system.flyway_schema_history` 存在（历史随迁）且 checksum 未变
   （SELECT version, checksum FROM system.flyway_schema_history → 与改动前一致）。
2. `lumen-auth/src/test/java/.../AuthTestServerBootstrap.java`：`CREATE SCHEMA IF NOT
   EXISTS auth` → `system`（含 AUTH_SCHEMA 常量与全部 `auth` schema 字样）。
3. `AuthIntegrationTestBase.java`：动态 URL `currentSchema=auth,public` →
   `currentSchema=system,public`（含 javadoc 提及）。
4. `application-dev.yml` + `application-dev.yml.example`：`currentSchema=...` 中的
   `auth` → `system`（注意：dev 文件当前顺序为 `public,auth`——只改 schema 名，
   **顺序保持并行会话写入的现状**，报告记录该事实）。
5. `application.yml`：并行会话新增的 `spring.flyway.schemas: auth` /
   `default-schema: auth` → `system`（注释里的裁定文字保留）。
6. 测试 SQL 全扫：`rg -n "auth\." lumen-auth/src/test/java`——所有 schema 限定前缀
   `auth.` → `system.`（AuthSeedMigrationTests/AuthSchemaMigrationTests/AuthRoleMenuTests/
   AuthOperationLogTests 等）；`table_schema = 'auth'` / `'auth'` schema 断言 →
   `'system'`。**不要动** `auth-rbac`/`Auth*` 等类名与 `lumen-auth` 模块字样（那是
   Batch 2 的 git mv 范围）；也不要动 `public.` 前缀。

**RED/GREEN**：先改测试断言（table_schema='system'）跑
`./mvnw test -q -pl lumen-auth -Dtest='AuthSchemaMigrationTests' -DfailIfNoTests=false`
确认 RED（schema 还是 auth）→ 执行 ALTER → 同命令 GREEN。随后全量
`./mvnw test -q`（MVN_EXIT=0，51+ 全绿）。

## 任务 1.2 — `docs/deployment/pg-init.sql` 全文更名

- schema：`CREATE SCHEMA IF NOT EXISTS auth` → `system`（六 schema 清单其余不动）
- 角色：`lumen_auth_role` → `lumen_system_role`（角色块 + GRANT 段 + 第六节
  public 系统表授权段，共约 8 处）
- Verify: `rg -c "lumen_auth_role|CREATE SCHEMA IF NOT EXISTS auth"` = 0（文件内）

## 完成标准与提交

1. RED/GREEN 证据 + 全量绿（报告 surefire 计数）
2. `rg -n "\bauth\b" lumen-sev/lumen-auth/src` 复查——允许残留：类名 Auth*、auth-rbac
   字样、`/api/v1/auth/` 路径（Batch 2/3 范围）、模块路径 lumen-auth（Batch 2 git mv）；
   **schema 语义的 auth 必须清零**
3. commit：`🔧 chore(sev): system-data schema rename auth->system + config base (rename-auth-to-system 1.1-1.2)`
4. 报告：`openspec/sdd/reports/rename-b1-report.md`（RED/GREEN、ALTER 幂等证据、
   schema 前缀清扫清单、偏差）

## 不要做
- 不做 git mv 模块（Batch 2）；不改控制器路径（Batch 3）；不动 framework；不碰
  openspec/specs/；`docs/proposals/` 不动
