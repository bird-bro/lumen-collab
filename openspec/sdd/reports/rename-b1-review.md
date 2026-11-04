# Batch 1 Review — rename-auth-to-system（tasks 1.1, 1.2）

> Verdict: **Approved** · Reviewer: code-reviewer agent · 2026-11-03 · Commit `8d73970` (base `bc1eae8`)
> Severity: Critical 0 · Important 0 · Minor 3

## 1. 独立 DB 状态核查（一次性 JDBC 工具，凭据经 `.env-test.local` env 注入，未打印）

对团队测试服务器 `lumen` 库实测（只读）：

- `pg_namespace`：`system_exists=true`，`auth_exists=false` — schema 已改名且无残留。
- `system.flyway_schema_history`：3 行，全部 `success=true`，checksum 与报告记录逐一相等
  （V1 `-1198603292` / V2 `15131651` / V3 `-1617003236`）→ ALTER 随迁 + checksum 冻结独立证实。
- 七张业务表 `org_info/org_dep/user_info/role_info/user_role/menu_info/role_menu` 全部
  在 `system` schema（7/7）；四张 public 共享表 `dict_catal/dict_detail/log_info/log_sign`
  全部在 `public`（4/4）。
- `currentSchema=system,public` 连接下 `current_schema()=system`，未限定名 `user_info`、
  `dict_catal` 均可解析（contract 场景 6 的 DB 侧基础成立）。

## 2. 残留清扫核查（rg `\bauth\b` lumen-auth/src，含 targeted schema 语义模式）

schema 语义残留 **为零**。现存命中全部落在允许类别：包根 `org.birdbro.lumen.auth`
（Batch 2）、`lumen-auth` 模块字样与 `spring.application.name: lumen-auth` 及其断言
（Batch 2）、`Auth*` 类名/标识符（含 `AUTH_SCHEMA` 常量名、`[auth-test-bootstrap]` 日志
标签）、`/api/v1/auth`、`/auth/me` 路径字样（Batch 3）、`auth-rbac` 字样、冻结迁移
`V3__system_data.sql:6` 注释（checksum 纪律）。无未申报类别残留。

## 3. 配置面核查（HEAD 工作树）

| 面 | 状态 |
|:--|:--|
| `AuthTestServerBootstrap` | `AUTH_SCHEMA="system"` + `CREATE SCHEMA IF NOT EXISTS system` ✔ |
| `AuthIntegrationTestBase` | `currentSchema=system,public` + flyway 属性随 `AUTH_SCHEMA` ✔ |
| `application.yml` | `flyway.schemas: system` / `default-schema: system`，裁定注释保留 ✔ |
| `application-dev.yml.example` | `currentSchema=system,public` ✔ |
| `application-dev.yml`（gitignored） | `currentSchema=public,system` — 顺序保持并行会话现状，仅改 schema 名，与报告声明一致 ✔ |
| `docs/deployment/pg-init.sql` | `CREATE SCHEMA IF NOT EXISTS system`；六 schema 清单其余原样；`lumen_auth_role`/`CREATE SCHEMA IF NOT EXISTS auth` = 0；`lumen_system_role` 覆盖角色块+GRANT+public 授权段 ✔ |

## 4. 测试独立复跑

`export JAVA_HOME=… && cd lumen-sev && ./mvnw clean test -q` → **MVN_EXIT=0**；
surefire 汇总（15 个测试类，均在 lumen-auth）：**Tests run: 50, Failures: 0, Errors: 0,
Skipped: 0** — 与报告一致。报告对 51→50 的解释（旧 target 陈旧 `TempFilterProbeTests`
报告）与 clean 后真值相符。

**RED 证据合理性**：`AuthSchemaMigrationTests` 恰 4 个 `@Test` → "Tests run: 4,
Failures: 4" 结构一致；失败消息形态（`table system.org_info must exist`）与断言改动
吻合；上下文日志 `Current version of schema "auth": 3` 说明 RED 跑时 flyway 配置仍指向
auth（仅先翻断言）——这也是后续 `ALTER SCHEMA` 能成功的唯一顺序（若 RED 时已切
`schemas=system`，Flyway 会自动建 system schema 并迁移，ALTER 将因同名冲突失败）。
内部自洽，判定 plausible。

## 5. 范围纪律（nothing extra）

- 批提交 `8d73970` 恰为 17 文件（+78/-76），全部在 docs/deployment + lumen-auth；
  `bc1eae8..8d73970` 区间另两提交（`4b16ddd` openspec 归档、`9839652` 并行会话快照）
  为外部既有工作，非本批产物。
- 无 git mv（模块仍 `lumen-auth`）；无控制器/`/api/` 改动；无 framework/pom/aggregator
  改动；冻结迁移 V1/V2/V3 零改动（checksum 经全绿复跑 + DB 行双重证实）。
- `.env-test.local`、`application-dev.yml` 均 check-ignore 通过且未入库；lumen-sev
  工作树干净（未提交改动仅 openspec 并行会话文档/报告）。

## Findings

- **Minor**（`lumen-sev/lumen-auth/src/test/java/org/birdbro/lumen/auth/AuthTestServerBootstrap.java:32-34`）：
  常量名 `AUTH_SCHEMA` 与 javadoc "the fixed lumen-auth service schema" 仍是旧标识
  （值为 `system`，语义已正确）。属 Batch 2 `Auth*`/模块更名自然覆盖范围，本批不必处理；
  建议 Batch 2 执行时顺手对齐并同步 `[auth-test-bootstrap]` 日志标签。
- **Minor**（既有事实，非本批引入）：文件名/类名不一致 —
  `AuthOrgCrudTests.java` 声明 `class AuthUnitCrudTests`（:37）、`AuthOrgDepTests.java`
  声明 `class AuthDepartmentTests`（:38）。报告已如实披露；surefire 按类名运行，无正确性
  影响；建议随 Batch 2 git mv 一并对齐（文件名或类名）。
- **Minor**（冻结件事实性陈旧，接受）：`lumen-sev/lumen-auth/src/main/resources/db/migration/V3__system_data.sql:6`
  注释 "auth schema (spring.flyway.schemas = auth)" 改名后不再为真，但 V3 checksum
  冻结（-1617003236）禁止改动；留作 spec-hygiene/未来迁移批次说明项即可。

## 结论

Batch 1（DB schema 更名 + 配置/测试基座 + pg-init.sql）实现与 contract 场景 5/6/8 的
DB 侧要求一致，幂等 ALTER、checksum 冻结、并行会话零接触均经独立复核证实。
**Approved** — 可进入 Batch 2（模块与包更名）。
