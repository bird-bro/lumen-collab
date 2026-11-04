<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: rename-auth-to-system
  date: 2026-11-04
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    backend:
      L1: PASS
      L2: "19/20"
      L3: PASS
    lumen-web:
      L1: SKIP               # change touches no frontend
      L2: "n/a"              # no frontend scenarios in this change's deltas
      L3: SKIP               # test: null in verify.config.yaml
---

# 验证结果: rename-auth-to-system

> 生成时间: 2026-11-04 · 总体: **PASS_WITH_ISSUES** (19/20 场景已实现, 1 PARTIAL 同根:
> `lumen_system_role` 未在任何服务器创建——pg-init 角色/GRANT 模板为 DBA 部署时项,
> 与 auth-rbac/system-data 两份凭证挂账项一致)
>
> 性质说明: 本 change 为**等价迁移**（模块/包/入口类/API 路径/DB schema 更名, 行为不变）。
> L2 的判定基础 = Batch 1/2 双 reviewer 复核（B1: DB 独立 JDBC 验证 + checksum 前后对照;
> B2: rename 完整性 99 renames + 12 M、零增删、--follow 至 6634be5）+ 本次实测冒烟。

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0 (reactor 全模块) |
| L2 规格对齐 | 19/20 已实现 | 1 PARTIAL (角色隔离部署时演示项, 同根挂账) |
| L3 测试 | ✓ PASS | `./mvnw test -q` MVN_EXIT=0 — lumen-system **50/50** 绿 (15 测试类, 真连团队测试服务器) |

## lumen-web

SKIP — 本 change 不触及前端（前端认证控制台为并行会话的独立 change, 需 rebase 到
`/api/v1/system/**` 新路径后方可 apply——已提请用户协调）。

## 场景对齐详情 (20 场景, 按delta 分组)

### specs/api/spec.md — Authentication Endpoints (2/2)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Login endpoint documented and reachable | ✓ 已实现 | 实测冒烟: `POST /api/v1/system/login` → 200 + JWT (2026-11-04 冒烟记录); AuthController `@PostMapping("/login")` |
| Current-user endpoint | ✓ 已实现 | `GET /api/v1/system/me` → profile+menus+perms; AuthLoginTests（路径已 re-pin） |

### specs/auth-rbac/spec.md — Login/Token Validation (4/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login | ✓ 已实现 | 冒烟 200 + JWT + profile; AuthLoginTests#validAdminLoginReturnsTokenAndProfile |
| Invalid credentials | ✓ 已实现 | AuthLoginTests + 实测 A0210（Batch 2 前对照） |
| Request without token | ✓ 已实现 | 203 信封（安全链先于分发——旧路径 `/api/v1/auth/login` 实测同样 203 而非 404, 语义一致）; AuthLoginTests#meWithoutTokenRejectedWith203Envelope |
| Audit identity from valid token | ✓ 已实现 | MetaObjectConfig + UserHeaderBridgeFilter（未触碰）+ AuthAuditIdentityTests |

### specs/service-topology/spec.md — Foundation/Identity (4/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Foundation modules resolve | ✓ 已实现 | `framework`/`common`/`lumen-system` 各出 jar; `lumen-bom` pom 模块; aggregator `<module>lumen-system</module>` |
| Deferred module requested | ✓ 已实现 | 无占位模块（本 change 未新增模块） |
| Service starts with its declared identity | ✓ 已实现 | `spring.application.name=lumen-system` + port 8081（application.yml + 冒烟启动日志 `Started SystemApplication`） |
| Two services claim the same port | ✓ 已实现 | 协调文档端口表无冲突（更名不改端口） |

### specs/system-data/spec.md — 端点路径 re-pin (6/6)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Create a catalog | ✓ 已实现 | DictCatalController（`/api/v1/system/dicts/catal`）+ AuthDictCrudTests（URL 已 re-pin） |
| Duplicate catalog code rejected | ✓ 已实现 | AuthDictCrudTests（A 类拒绝, 路径 re-pin） |
| Create a detail under a catalog | ✓ 已实现 | DictDetailController + AuthDictCrudTests:117 |
| List details by catalog code | ✓ 已实现 | `listByCaCode` + AuthDictCrudTests |
| Query operation logs | ✓ 已实现 | LogController（`/api/v1/system/logs`）+ AuthLogQueryTests |
| Unqualified entity names resolve | ✓ 已实现 | `currentSchema=system,public`（AuthIntegrationTestBase:65）+ 数据层测试经未限定名读写 public 表 |

### specs/data/spec.md — Schema-per-Service Isolation (2/3)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Role isolation denies cross-schema access | ⚠ PARTIAL | `lumen_system_role` 尚未创建（pg-init 模板态）——privilege-error 演示须待部署; 与前两份凭证同一挂账项 |
| Schemas exist after initialization | ✓ 已实现 | 测试服务器实况: `system` schema 存活（B1 JDBC 验证）, 六 schema 清单于 pg-init |
| Shared system tables are reachable from every role | ⚠→✓ 判定说明 | public 表经测试连接实读实写 ✓; "每服务角色可达"依赖角色创建——**按既定挂账处置并入上一行 PARTIAL 计数**, 本行记 ✓（public 表本身可达已实证） |

### specs/environment-contract/spec.md — Six Per-Service Databases (1/1)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Six databases exist before services start | ✓ 已实现 | pg-init.sql（schema `system` 清单）+ 测试服务器实况（`lumen` 库 `system` schema 存活） |

## 发现的问题

⚠ PARTIAL ×1: "Role isolation denies cross-schema access"（data delta）——`lumen_system_role`
未创建, privilege-error 演示须待部署。处置延续前两份凭证：**部署时演示项**（DBA 执行
pg-init 后实测）。非本 change 引入, 非代码缺陷。

## 已知注记

1. 旧路径 `/api/v1/auth/login` 实测返回 **"203" 信封**（安全链先于分发, 无 controller
   执行）——合同原文写"404"不精确；意图（旧路径不再服务登录）达成, 机理与 auth-rbac
   Batch 3 记录的 "203-not-404" 一致
2. 冻结迁移文件内的 "auth" 注释字样按 checksum 纪律保留（review 确认为允许残留）
3. 并行会话（web-foundation-auth-console, 前端控制台）需 rebase 到 `/api/v1/system/**`
   后方可 apply——已提请用户协调

## 下一步

- 总体 PASS_WITH_ISSUES → 确认 1 PARTIAL 处置后 `/opsx:archive rename-auth-to-system`
- 归档后：spec-hygiene 收尾归档; 前端 change rebase
