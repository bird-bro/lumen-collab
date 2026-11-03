# Design

## Context

`lumen-auth` 实质是系统管理服务（用户/角色/菜单/机构/科室/字典/日志 + 登录），"auth"
命名脱节。用户裁定（2026-11-03，思路 2）：更名 lumen-system，四处子决策按建议落定：
路径全改 `/api/v1/system/**`、PG schema `auth`→`system`、角色模板 `lumen_system_role`、
dict 数据层迁入服务而 log 写路径留守 framework（切面约束）。

## Goals / Non-Goals

- Goals: 模块/包/入口类/应用名/路径/schema/角色一次性更名；dict 数据层归位服务五层；
  全部规格 re-pin；零消费者窗口期内完成
- Non-Goals: 不改端口（8081）；不改六 schema 模型结构；不动 log 写路径的框架归属；
  不修 AsyncFactory 非 UA NPE（Minor 队列，独立适配提案）；不改 DictMenu/DictCity 范围

## Decisions

### D1 - 改名映射表（一次性完成，避免长期两套标签）
| 维度 | 旧 | 新 |
|:--|:--|:--|
| 模块目录/artifactId | `lumen-auth` | `lumen-system` |
| 包根 | `org.birdbro.lumen.auth` | `org.birdbro.lumen.system` |
| 入口类 | `AuthApplication` | `SystemApplication` |
| `spring.application.name` | lumen-auth | lumen-system |
| API 路径 | `/api/v1/auth/**` | `/api/v1/system/**` |
| PG schema | `auth` | `system`（`ALTER SCHEMA auth RENAME TO system`——元数据操作，flyway 历史随迁 checksum 不动） |
| 角色模板 | `lumen_auth_role` | `lumen_system_role` |
| 安全豁免 | `/api/v1/auth/login` | `/api/v1/system/login` |
git mv 保留历史；`git log --follow` 可追溯。

### D2 - dict 归位服务五层，log 写路径留守（切面约束）
`framework/dict/**`（16 文件）→ `lumen-system` 标准五层：entity→`entity/`、mapper→
`mapper/`、service/impl→`service/`(+impl)、pojo→`dto/`、DictLabelHelper/DictReverseHelper→
`service/`（服务内工具）。理由：dict 是业务数据（OPS 同款 manage 模块），split-brain
消除；`@MapperScan("org.birdbro.**.mapper")` 自动覆盖。log 写路径（LogAspect/
AsyncFactory/ILogInfoService/entities/mappers/XMLs/RequestContextConfig）留守 framework
——任何 `@Log` 服务都需要；`LogController` 查询门面在 lumen-system。

### D3 - DB 更名机制：ALTER SCHEMA + 测试基座同步
`ALTER SCHEMA auth RENAME TO system` 对共享测试服务器一次性执行（元数据操作，事务内
合法，历史表/表/checksum 全随迁）。配套：`AuthTestServerBootstrap` 建 `system` schema、
`AuthSystemDataMigrationTests`/`AuthSchemaMigrationTests` 的 `table_schema` 断言、
`AuthIntegrationTestBase` 动态 URL、dev 文件 + 模板、pg-init.sql（schema/角色/GRANT 段）。

### D4 - 规格与文档 re-pin 清单
delta 六份：api（Authentication Endpoints）、auth-rbac（Login/Token Validation 两需求）、
service-topology（Service Identity/Foundation Module Set）、system-data（Dict Catalog/
Detail/Log Query/Shared Placement 四需求）、data（Schema-per-Service Isolation）、
environment-contract（Six Per-Service Databases）。文档：naming.md 包根示例、
lumen-sev/CLAUDE.md 模块清单、verify.config.yaml 注释、协调文档端口表与角色字样。
已知遗留（不在本 change）：auth-rbac 主规格 "unit node" 措辞残留（spec-hygiene 类）。

## Risks / Trade-offs

- **路径变更影响已归档规格** → delta 全量 re-pin（本 change 即为此而来）；归档顺序：
  system-data 先 verify+archive，本 change 随后（其 system-data delta 以归档后主规格
  为基线）
- **`ALTER SCHEMA` 在共享测试服务器** → 元数据操作、原子、可重复（已改名则跳过）；
  并行会话工作区改动不涉 DB
- **重构面积大（全包移动）** → git mv + 编译器驱动；提交前 L1+L3 全绿为门

## Open Questions

无——四个子决策均经用户圈定（2026-11-03）。
