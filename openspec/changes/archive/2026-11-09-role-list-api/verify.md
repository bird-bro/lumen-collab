<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: role-list-api
  date: 2026-11-09
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "7/7"
      L3: PASS
---

# 验证结果: role-list-api

> 生成时间: 2026-11-09 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q` 成功（lumen-sev reactor, JDK 11） |
| L2 规格对齐 | 7/7 已实现 | api delta 5 场景 + web-system-console delta 2 场景全部 IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0 — lumen-system 127 tests / 0 failures / 0 errors（含新增 AuthRoleListTests 2 例；framework/common 本季无测试类，未产报告） |

## 场景对齐详情

### api delta（backend 证据 — 跨域规则：仅引 backend 代码）

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Role list returns paged rows with grant echo | ✓ 已实现 | RoleController.java:93-94（`GET /list`）+ RoleServiceImpl.java:144-176（like/eq/orderByAsc + 单次 IN 批量回显 + 升序排序）+ RoleItemDto.java（行内 menuIds，未授权为空数组）；AuthRoleListTests.roleListReturnsPagedRowsWithGrantEcho 红→绿实证（红 = 原生 405，绿 = 信封/IPage 形状/回显 [2,7] 升序/空数组/id 升序） |
| Role list name and type filters | ✓ 已实现 | RoleServiceImpl.java:146-148（LIKE name / EQ type / orderByAsc(id)，随访 getPage 语义）+ PageSizeConfig 默认 1/20；AuthRoleListTests.roleListFiltersByNameAndType（name 子串/type=11 相等/组合/无参默认） |
| Role list (gap to implement) 关闭 | ✓ 已实现 | 端点落地即本场景的 THEN（后端实现 + mock 退役）；红灯阶段日志 `URI: /api/v1/system/roles/list, Method: GET, 支持: [PUT, DELETE]` 坐实原 405，绿灯后消失 |
| Duplicate role name rejected | ✓ 已实现 | RoleServiceImpl.java:213（rejectDuplicateName → A0714）；AuthRoleMenuTests:193-198 断言 |
| Delete cascades unbind | ✓ 已实现 | RoleServiceImpl.java:103-111（user_role + role_menu + role 同事务逻辑删）；AuthRoleMenuTests:212-236 DB 判定 |
| Role-menu full-replace assignment | ✓ 已实现 | RoleServiceImpl.java:115-136（先删后插全量替换）；AuthRoleMenuTests:148-179 断言 |

### web-system-console delta（frontend 证据 — 页面代码既有，本 change 后端供数）

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Role CRUD round-trip | ✓ 已实现 | lumen-web/src/api/roles.ts:16-44（typed client，listRoles 即本 change 实现的 GET /list）+ pages/system/roles/index.tsx 的 create/update/delete + reload 流 |
| Menu assignment is full-replace（重开弹窗精确显示已存授权） | ✓ 已实现 | roles.ts:42-44（PUT /{id}/menus）+ index.tsx:254（`setChecked(new Set(role.menuIds ?? []))` —— 回显数据源由 mock 换成本 change 的真实端点）+ index.tsx:308（授权数列） |

## 发现的问题

（无 — 全场景 IMPLEMENTED）

## 备注

- 夹具卫生：`AuthRoleListTests` 前缀（回显角色 / 名单过滤_）已回填基类 `ROLE_NAME_PREFIXES` 清扫清单（2026-11-07 裁决）；本轮清扫钩子确认无残留。
- 用户侧可视化冒烟（浏览器打开 `/system/roles`）留待用户在本地 `pnpm dev` 环境确认；端点级行为已由真实 servlet 上下文测试覆盖。
