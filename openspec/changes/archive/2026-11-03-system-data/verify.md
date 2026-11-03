<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: system-data
  date: 2026-11-03
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    backend:
      L1: PASS
      L2: "14/16"
      L3: PASS
    lumen-web:
      L1: SKIP               # change touches no frontend
      L2: "n/a"
      L3: SKIP               # test: null in verify.config.yaml
---

# 验证结果: system-data

> 生成时间: 2026-11-03 · 总体: **PASS_WITH_ISSUES** (14/16 场景已实现, 2 PARTIAL 同根:
> 六个服务角色尚未在任何服务器创建——pg-init 角色块/GRANT 模板均为 DBA 部署时项)
>
> ⚠ 验证环境注记: 工作区含并行会话的未提交改动（17 文件，实体 Lombok/BeanUtil 重构、
> application.yml 的 flyway schemas 显式配置等）——L1/L3 实测于该工作区状态；其中
> `application-dev.yml` 的 search_path 顺序为 `public,auth`（与 delta 文本
> `<service-schema>, public` 顺序不同，但对本服务表名解析结果等价——public 无同名冲突
> 表；flyway 历史 schema 由并行会话新增的显式 `spring.flyway.schemas/default-schema`
> 配置钉住）。该会话收尾提交后建议重跑本凭证。

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` exit 0 (JDK 11, 零 ERROR) |
| L2 规格对齐 | 14/16 已实现 | 2 PARTIAL (角色隔离 + 共享表授权——同为部署时演示项) |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0 — **51/0/0** (42 既有 + 9 新增；含并行会话新增 1 例) |

## lumen-web

SKIP — 本 change 不触及前端。

## 场景对齐详情

### specs/system-data/spec.md (8/8)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Create a catalog | ✓ 已实现 | DictCatalController.java:41 (`/api/v1/auth/dicts/catal`) + AuthDictCrudTests.java:63 (落行 + create_by=操作者) |
| Duplicate catalog code rejected | ✓ 已实现 | AuthDictCrudTests.java:63 (A 类拒绝断言) + 服务层查重 |
| Create a detail (dual anchor) | ✓ 已实现 | DictDetailController.java:42 + AuthDictCrudTests.java:117 (caId+caCode 双锚点落库) |
| List details by catalog code | ✓ 已实现 | DictDetailServiceImpl `listByCaCode`（加法式新方法, enable=1, sort+id 排序）+ AuthDictCrudTests.java:117 |
| Fresh database carries the starter dictionary | ✓ 已实现 | V3__system_data.sql (common_status 种子 + setval) + AuthSystemDataMigrationTests.java:194,205 |
| Annotated endpoint writes an operation log | ✓ 已实现 | @Log 19/19 写端点 + AuthOperationLogTests.java:54 (轮询断言 log_info 行, uid=操作者) |
| Login records a sign event | ✓ 已实现 | AuthServiceImpl.java:111 (recordForSign 成功/失败分支) + AuthSignLogTests.java:61 (成功+密码错误各一条) |
| Log table exists on fresh database | ✓ 已实现 | AuthSystemDataMigrationTests.java:100 (四表 public 断言) + :138 (log 表无审计块) |
| Query operation logs | ✓ 已实现 | LogController.java:28 (`/api/v1/auth/logs` 分页) + AuthLogQueryTests.java:52 (分页/过滤/倒序) |
| Unqualified entity names resolve | ✓ 已实现 | AuthIntegrationTestBase.java:65 (`currentSchema=auth,public`) + 全部数据层测试经未限定名读写 public 表 |

### specs/data/spec.md (1/3 — 2 PARTIAL 同根)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Role isolation denies cross-schema access | ⚠ PARTIAL | `lumen_system_role` 尚未在任何服务器创建（pg-init 角色块注释态）——privilege-error 演示须待部署；与 auth-rbac 凭证同一挂账项 |
| Schemas exist after initialization | ✓ 已实现 | pg-init.sql + 测试服务器实况（schema 存活, Flyway 迁移应用） |
| Shared system tables are reachable from every role | ⚠ PARTIAL | GRANT 模板已入 pg-init 第六节, public 表经测试连接实读实写 ✓；但"每服务角色可达"须待角色创建后的部署演示 |

### specs/service-topology/spec.md (5/5)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Redis is optional | ✓ 已实现 | RedisConfig 条件装配（auth-rbac 批次验证, 本 change 未触碰） |
| Business baggage is removed | ✓ 已实现 | DingTalkConstants/WxCryptUtil 不在库（rg 零命中, 拷贝时已剥离） |
| Copied POM is governance-aligned | ✓ 已实现 | bcprov 由 lumen-bom 管控（骨架批次验证, 未变） |
| No-gateway audit bridging is platform machinery | ✓ 已实现 | UserHeaderBridgeFilter + AuthAuditIdentityTests（create_by=操作者, 本 change 端点复证） |
| Log entities resolve without the ice schema | ✓ 已实现 | rg `ice\.` 全库零命中（实体+两 mapper XML）+ AuthSignLogTests.java:115（登出经拷贝件链路落行 = 写路径端到端实证） |

## 发现的问题

⚠ PARTIAL ×2（同根, 非新发现）："Role isolation denies cross-schema access" 与 "Shared
system tables are reachable from every role" —— 六个服务角色尚未创建（pg-init 角色/GRANT
块为注释模板, 口令由 DBA 部署时设定）。处置与 auth-rbac-foundation 凭证一致：**部署时
演示项**。届时 DBA 执行 pg-init 后以 `lumen_system_role` 实测：读 `public.dict_catal`
可达 + 读其他服务 schema 被拒。

## 下一步

- 总体 PASS_WITH_ISSUES → 确认 2 PARTIAL 处置后 `/opsx:archive system-data`
- 归档顺序约束：system-data 先于 rename-auth-to-system 归档（其 delta 尚为 auth 路径）
