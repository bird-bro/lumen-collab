# Proposal: rename-auth-to-system

## Why

`lumen-auth` 已经不是"认证服务"：它承载 用户/角色/菜单/机构/科室/字典/日志 的全部管理
端点（登录只是其中一小块），"auth"的命名与其系统管理服务的实质脱节。用户裁定
（2026-11-03，架构讨论"思路 2"）：更名为 **lumen-system**。此刻是零消费者窗口期
（前端未启动、无任何 API 消费方），是改名成本最低的时刻。

同时完成两件配套（用户裁定）：
1. **dict 数据层迁入服务**——Batch 1 曾按"与 log 对称"拷入 `framework/dict`，架构讨论
   认定其属业务模块（OPS 同款：dict 在 `birdbro-manage`），且 split-brain（数据层在
   framework、控制器在服务）已名实不符；**log 写路径留守 framework**（`LogAspect` 切面
   注入 `ILogInfoService`，任何用 `@Log` 的服务都需要，搬出即框架依赖业务模块），
   `LogController` 查询门面在 lumen-system。
2. **PG schema 与角色同步更名**——schema-per-service 的"服务↔schema"映射不留旧标签：
   `auth` schema → `system`（`ALTER SCHEMA` 元数据操作，flyway 历史随迁、checksum
   不动），角色模板 `lumen_auth_role` → `lumen_system_role`。

## What Changes

- **模块**：`lumen-auth/` → `lumen-system/`（git mv）；artifactId/name；aggregator
  module 条目；`AuthApplication` → `SystemApplication`；包根
  `org.birdbro.lumen.auth` → `org.birdbro.lumen.system`
- **API 路径**：`/api/v1/auth/**` → `/api/v1/system/**`（login/me/users/roles/menus/
  orgs/org-deps/dicts/logs 全量；`security.permit-all` 同步）
- **连接与配置**：`currentSchema=auth,public` → `system,public`（dev 文件 + 模板 + 测试
  基类动态属性）；`spring.application.name` = lumen-system；`AuthTestServerBootstrap`
  建 `system` schema；`AuthSchemaMigrationTests`/`AuthSystemDataMigrationTests` 的
  `table_schema` 断言改 `system`
- **测试服务器 DB**：`ALTER SCHEMA auth RENAME TO system`（一次性元数据操作）
- **dict 迁移**：`framework/dict/**`（16 文件）→ `lumen-system` 标准五层
  （entity/mapper/service/dto + 助手）；framework/log 写路径不动
- **文档**：naming.md 包根示例、lumen-sev/CLAUDE.md 模块清单与端口表、协调文档、
  verify.config.yaml 注释
- 规格同步（delta）：api（Authentication Endpoints 路径）、auth-rbac（主规格路径
  re-pin）、service-topology（Service Identity/Foundation Module Set）、system-data
  （dict/log 端点路径）、data（schema 清单与角色名）、environment-contract（schema 清单）

## Capabilities

### Modified Capabilities

- `api`: Authentication Endpoints 路径 re-pin 到 `/api/v1/system/**`
- `auth-rbac`: Login/Token Validation 等需求的路径 re-pin
- `service-topology`: Service Identity（lumen-system:8081）与 Foundation Module Set
  （lumen-system 服务）
- `system-data`: dict/log 管理端点路径 re-pin；dict 数据层归属服务
- `data`: schema 清单 `auth` → `system`；角色名 `lumen_system_role`
- `environment-contract`: schema 清单同步

## Non-Goals

- 不改端口（8081 不变）；不建新模块；不改六 schema 模型本身（仅更名其一）
- 不动 `lumen-patient` 等未来服务的任何预留
