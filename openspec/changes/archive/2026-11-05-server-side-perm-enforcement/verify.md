<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: server-side-perm-enforcement
  date: 2026-11-05
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "8/8"
      L3: PASS
    lumen-web:
      L1: SKIP
      L2: "-"
      L3: SKIP
---

# 验证结果: server-side-perm-enforcement

> 生成时间: 2026-11-05 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw -pl lumen-system -am test` 内含全模块编译（JDK 11） |
| L2 规格对齐 | 8/8 已实现 | auth-rbac + errors delta 的 8 个场景全部 IMPLEMENTED（见下） |
| L3 测试 | ✓ PASS | lumen-system 全套件 **71/71**（65 既有 + 6 新矩阵 `AuthPermEnforcementTests`），BUILD SUCCESS |

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1/L2/L3 | SKIP | 本 change 零前端改动（前端早已按同一 perms 串门控） |

## 场景对齐详情

| # | delta 场景（契约批准行为） | 证据 | 结论 |
|:--|:--|:--|:--|
| 1 | Session carries the permission set | 登录流既有事实（3 参构造器传 `permissionSet(userId)`）；矩阵登录路径隐式验证 | IMPLEMENTED |
| 2 | Authorities derive from the session permission set（空集→空表，永非 null） | `authoritiesDeriveFromPermissionSetAndAreNeverNull`（RED→GREEN） | IMPLEMENTED |
| 3 | Endpoint rejects a caller without the family permission（A0301、控制器不执行） | `permLessUserIsDeniedOnEveryFamily`（六族 GET + roles POST，且断言被拒创建未落库） | IMPLEMENTED |
| 4 | Administrator passes through | `adminPassesReadFamiliesAndCity` + 既有 admin 流测试全绿 | IMPLEMENTED |
| 5 | Denied request envelope（A0301、data null、HTTP 200 传输） | 矩阵断言信封 code；`AuthDenyAdvice` → `R.ofFail(ERROR_A0301)` | IMPLEMENTED |
| 6 | Unauthenticated still answers "203" | `tokenlessStillAnswers203` | IMPLEMENTED |
| 7 | Stale session（无 permissions 字段）→ 空集 → 拒绝 | POJO 级（null → 空 authorities，RED→GREEN）+ 无权用户集成等价态 | IMPLEMENTED |
| 8 | City reference endpoints stay authenticated-only | `cityStaysAuthenticatedOnly`（无权用户 GET city tree = 200） | IMPLEMENTED |

## 实现修正记录（apply 期间，均可在 design/tasks 追溯）

1. **会话填充本就存在**（proposal 修正）：`AuthServiceImpl` 登录时已传 `permissionSet`，
   唯一缺口是 `getAuthorities()` 返回 null —— 已映射（协调文档适配 (11)，唯一复制品改动）。
2. **方法安全早已启用**：复制的 SecurityConfig 自带 `@EnableGlobalMethodSecurity`；
   草案的 MethodSecurityConfig 造成 `metaDataSourceAdvisor` 双重注册（上下文启动失败），
   已删除 —— 注解即全部接线。
3. **拒绝路径用 advice**：`AuthDenyAdvice`（HIGHEST_PRECEDENCE）在 DispatcherServlet
   层捕获方法安全抛出的 `AccessDeniedException`，避免复制 SecurityConfig 注册
   handler bean，并压制 GlobalExceptionHandler 的 catch-all。

## 层级说明

- **L1**：构建随套件内含编译通过（PASS）。
- **L2**：8 个批准行为逐条映射到新矩阵 + 既有测试 + 实现修正记录。
- **L3**：lumen-system 71/71 全绿（2026-11-05 运行）。
