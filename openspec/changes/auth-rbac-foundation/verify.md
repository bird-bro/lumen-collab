<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: auth-rbac-foundation
  date: 2026-11-03
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    backend:
      L1: PASS
      L2: "24/25"
      L3: PASS
    lumen-web:
      L1: SKIP               # change touches no frontend (all tasks backend; frontend login is a separate queued change)
      L2: "n/a"              # no frontend scenarios exist in this change's deltas
      L3: SKIP               # test: null in verify.config.yaml
---

# 验证结果: auth-rbac-foundation

> 生成时间: 2026-11-03 · 总体: **PASS_WITH_ISSUES** (24/25 场景已实现, 1 PARTIAL)
>
> 重跑记录: 2026-11-03 表命名修正（用户决策: `unit_info`→`org_info`、`department`→`org_dep`、
> `role`→`role_info`、FK `unit_id`→`org_id`，URL 跟随 `/orgs`、`/org-deps`）后全套重跑 ——
> 测试服务器 `auth` schema 重置重放（V1/V2 未归档未出货，原地修正），L1/L3 全绿，凭证刷新。
>
> 重跑记录 3: 2026-11-03 安全装配平台化（用户裁定: framework/common 可在 OPS 血统基础上
> 演进, 加法式）——`UserHeaderBridgeFilter` 上移 `framework/security/filter/`（适配 (6)），
> 删除 `AuthWebSecurityConfig`，匿名路径配置化（framework AnonymousPathCustomizer +
> `security.permit-all`），共享 BCrypt bean 上移 SecurityBeanConfig；规则文档同步
> （naming.md / lumen-sev CLAUDE.md / 协调文档 / design D9）。L1/L3 重验全绿，凭证刷新。
>
> 重跑记录 2: 2026-11-03 配置本地化（用户决策: 取消 Nacos —— 移除 nacos-discovery/
> nacos-config/spring-cloud-starter-bootstrap 三个依赖与 `bootstrap.yml`；配置 =
> 提交的 `application.yml`（含 framework 所需 `token.*`/`xss.*`/`dispose.*`）+ gitignored
> `application-dev.yml`（凭据，模板 `.example`），随访 fu-launch 同形，design D8）后全套
> 重跑，L1/L3 全绿，凭证刷新；service-topology delta 新增 "Sensitive Configuration
> Exclusion" MODIFIED 块对齐主规格。

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` 成功 (reactor 全模块, MVN_EXIT=0, JDK 11) |
| L2 规格对齐 | 24/25 已实现 | 1 PARTIAL (角色隔离为部署时演示项) |
| L3 测试 | ✓ PASS | `./mvnw test -q` MVN_EXIT=0 — lumen-auth 34/34 绿 (10 个测试类, 重命名后的全新 schema 上 Flyway 重放); `framework`/`common` 当前无测试类, 零依赖姿态不变 (无可运行项, 无失败) |

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | — SKIP | 本 change 不触及前端 (tasks.md 全部为 backend; 前端登录为排队的独立 cross-stack change) |
| L3 测试 | — SKIP | verify.config.yaml `test: null` |

## 场景对齐详情

### specs/auth-rbac/spec.md (13/13)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Successful login | ✓ 已实现 | AuthController.java:44-47 + AuthServiceImpl.java:70-100 (BCrypt → TokenService.createToken → token+profile); AuthLoginTests.java:52-61 (code "200" + uid 1 断言) |
| Invalid credentials | ✓ 已实现 | AuthServiceImpl.java:80-82 (ERROR_A0210); AuthLoginTests.java:72; L3 日志实测 A0210 用户密码错误 |
| Request without token | ✓ 已实现 | AuthenticationEntryPointImpl.java:30-34 (code "203" 信封, HTTP 200); 拷贝件 SecurityConfig permitAll 保持 verbatim + framework AnonymousPathCustomizer.java:36 (`security.permit-all` 配置化, 适配 (6), 仅 login 免检 — application.yml:47); AuthLoginTests.java:96 (meWithoutTokenRejectedWith203Envelope) |
| Audit identity from valid token | ✓ 已实现 | MetaObjectConfig.java:19-26 (createBy 填充) + framework/security/filter/UserHeaderBridgeFilter.java:62-76 (JWT 主桥梁化 USER-HEADER, 适配 (6), 组件扫描自动注册所有服务); AuthAuditIdentityTests.java:48 (create_by = admin id 真库断言) |
| Tree response | ✓ 已实现 | OrgTreeService.java:36-51 (TreeUtils 按 upId 装配) + OrgController.java:65 (GET /api/v1/auth/orgs/tree); AuthDataLayerTests.java:99 (orgTreeAssemblesFromSeedRoot) |
| Multi-root capable | ✓ 已实现 | OrgTreeService.java:12-17 (无自 FK, upId=0 根约定); AuthOrgCrudTests.java:52 (secondRootAppearsAsIndependentTreeRoot) |
| Seeded departments | ✓ 已实现 | V2__seed.sql:14-17 (radiology/us/endo 三科室 FK org_id=1, `org_dep` 表); AuthSeedMigrationTests.java:55 + AuthOrgDepTests.java:53 |
| Department CRUD | ✓ 已实现 | OrgDepController.java:49-67 (POST/PUT/DELETE/GET list, `/api/v1/auth/org-deps`); AuthOrgDepTests.java:80 (建后列出) + :113 (未知 org A0400) |
| Administrator creates a user | ✓ 已实现 | UserInfoServiceImpl.java:60-93 (BCrypt 散列 :81, user_role 同事务绑定); AuthUserManagementTests.java:47 (DTO 不含散列) + AuthAuditIdentityTests.java:48 (create_by) |
| Duplicate username rejected | ✓ 已实现 | UserInfoServiceImpl.java:130-136 (A0111 服务级预检); AuthUserManagementTests.java:82; L3 日志实测 A0111 用户名已存在 |
| Role-menu assignment | ✓ 已实现 | RoleServiceImpl.java:102-125 (assignMenus 全量替换, @Transactional, 角色/菜单存在性校验); AuthRoleMenuTests.java:81 (roleMenuAssignmentFlowsToUserPermsOnMe) |
| Button permission in the perm set | ✓ 已实现 | V2__seed.sql:41 (menu 6 F 按钮 `radiology:report:audit`) + RoleMenuMapper.java:31-36 (user_role→role_menu→menu_info perms 走查, 全链 deleted=0); AuthLoginTests.java:150 + AuthDataLayerTests.java:157-165 |
| Fresh deployment can log in | ✓ 已实现 | V2__seed.sql:22-23 (admin/admin123 BCrypt, must_change_password=1) + :26-50 (role_info 角色/绑定/菜单); AuthSeedMigrationTests.java:72,87,102; 登录测试即用种子 admin |

### specs/api/spec.md (4/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Resource retrieval | ✓ 已实现 | GlobalResponseAdvice.java (框架, R.ofSuccess 包装 code:"200") + 全部控制器统一信封; 测试普遍断言 `code` "200" (如 AuthLoginTests.java:57) |
| ID in JSON payload | ✓ 已实现 | 七实体 `private Integer id` + `@TableId(IdType.AUTO)` (UserInfo.java:24-25、OrgInfo.java:32-33、OrgDep.java、RoleInfo.java 等, DDL 为 SERIAL); 无任何 ID 字符串化序列化配置; AuthLoginTests.java:60-61 断言 `profile.uid` 整数 1 |
| Login endpoint documented and reachable | ✓ 已实现 | AuthController.java:44 (POST /api/v1/auth/login) + AuthWebSecurityConfig.java:34-36 (免 token); AuthLoginTests.java:52 |
| Current-user endpoint | ✓ 已实现 | AuthController.java:50-53 (GET /api/v1/auth/me) + AuthServiceImpl.java:102-116 (DB 现读 profile+menus+perms); AuthLoginTests.java:111-125 |

### specs/data/spec.md (3/4)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Standard entity fields | ✓ 已实现 | EntityBase.java:20-48 (deleted/create_by/create_time/update_by/update_time; FieldFill 仅在 create_by/update_by; @JsonFormat `yyyy-MM-dd HH:mm:ss` GMT+8 :35,:46); AuthSchemaMigrationTests.java:98 (每表 id+审计块真库断言) |
| Database-per-Service is retired | ✓ 已实现 | 本 delta REMOVED 块以 Schema-per-Service Isolation 替换六库要求; docs/deployment/pg-init.sql:5-13 (单库 `lumen` + 六 schema 交付物存在) |
| Role isolation denies cross-schema access | ⚠ PARTIAL | 机制设计在案: pg-init.sql:15-24 角色创建/GRANT 块为**注释模板** (口令由 DBA 部署时设定); PostgreSQL 默认权限模型下隔离成立, 但无实际连接的 privilege-error 演示 — 协调文档 2026-11-02 已记录为部署时验证项 (execution-contract.md:167 同结论) |
| Schemas exist after initialization | ✓ 已实现 | pg-init.sql:8-13 (六 schema 幂等创建); auth schema 另由 AuthTestServerBootstrap 在测试服务器实测存活 (本次重命名后 schema 重置 + Flyway 全新重放即证明) |

### specs/environment-contract/spec.md (1/1)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Six databases exist before services start | ✓ 已实现 | pg-init.sql:5-13 (幂等建 `lumen` 库 + 六 schema); L3 实测: lumen-auth 上下文对测试服务器 `lumen` 库 `auth` schema 启动成功 (34/34, 重命名后重放) |

### specs/service-topology/spec.md (3/3)

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Context load test with no services running | ✓ 已实现 | 本次 L3 即证明: `./mvnw test -q` MVN_EXIT=0, Flyway 迁移真库应用 + mapper 真库演练 (AuthApplicationTests.java:38 contextLoads + 33 个数据层/端点测试) |
| Nacos unreachable during verification | ✓ 已实现 | 栈内本就无注册/配置中心: pom 已无 nacos/bootstrap starter, `bootstrap.yml` 已删; 提交的 application.yml 本地承载 framework 所需 `token.*`/`xss.*`/`dispose.*` (application-test.yml 不再需要 nacos-off 旗标与 dummy); 本次 L3 全绿即上下文从本地配置加载 |
| Test-server connection unconfigured | ✓ 已实现 | AuthTestEnv.java (`require` 抛 IllegalStateException 列出全部缺失变量与文件路径, 绝不静默跳过) + AuthTestServerBootstrap.java:66-71 (bootstrap 失败显式抛出); .env-test.local gitignore 命中 (本次 L3 为已配置绿, 未配置红语义为任务 1.3 已录证的机制行为) |

## 发现的问题

⚠ PARTIAL: "Role isolation denies cross-schema access" (data delta)
- 期望: `lumen_auth_role` 连接读其他服务 schema 时 PostgreSQL 拒绝 (privilege error)
- 实际: `docs/deployment/pg-init.sql:15-24` 的角色创建 + GRANT 块是注释模板 (口令由 DBA 部署时设定, 无法在仓库中固化); PostgreSQL 默认权限模型 + 模板保证隔离语义, 但缺少一次真实连接的跨 schema 拒绝演示
- 既有记录: 协调文档 2026-11-02 与 execution-contract.md:167 均已将其列为部署时验证项 — 与本审计一致, 非新发现
- 建议: 归档时确认接受"部署时演示"处置 (DBA 执行 pg-init 后以 `lumen_auth_role` 实测一次跨 schema SELECT 被拒), 或部署环境就绪后补录演示证据; 此项非代码缺陷, `/opsx:apply` 无法修复

## 下一步

- 总体 PASS_WITH_ISSUES → 确认 PARTIAL 处置后归档; 或部署演示就绪后重跑 `/opsx:verify auth-rbac-foundation` 刷新凭证 → `/opsx:archive auth-rbac-foundation`
