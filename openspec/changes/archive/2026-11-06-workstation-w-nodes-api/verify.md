<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: workstation-w-nodes-api
  date: 2026-11-06
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "3/3"
      L3: PASS
---

# 验证结果: workstation-w-nodes-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（lumen-sev/，JDK 11）exit=0 |
| L2 规格对齐 | 3/3 已实现 | api delta（W 节点要求，REPLACES 旧端点要求）+ auth-rbac delta（REPLACES 旧授权要求）全 IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit=0；surefire **100 tests / 0 failures / 0 errors / 0 skipped（25 类）** |

## 场景对齐详情

### api delta — REPLACED "Workstation Context Endpoints" → ADDED "Workstation Menu Nodes"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| W 节点种子 + 超管授权 | ✓ 已实现 | `V22__workstation_w_nodes.sql`（4 处 type='W'：3 内容守卫 INSERT + admin 授权块）；测试 `AuthWorkstationSeedTests.java:59`（三节点逐条恰 1）+ `:73` 等价授权断言（grants=3） |
| 授权走角色流（role_menu → 用户树） | ✓ 已实现 | W 节点走既有 role_menu 机制（V22 授权块）；树端点携带 W 节点（MenuTreeService 零过滤透传）；机制移除实证 `UserInfoController` assign-ws 引用 = 0 |
| 错误机制已移除（404/表/列） | ✓ 已实现 | 端点删除（控制器无 workstations 路由）；测试 `AuthWorkstationSeedTests.java:36-55`（表/列/节点/授权四重断言） |
| 菜单表单接受 W 类型 | ✓ 已实现 | `CreateMenuRequest.java:29`（`@Pattern("M\|C\|F\|W")`，UpdateMenuRequest 同） |

### auth-rbac delta — REPLACED "Workstation Authorization" → ADDED "Workstation Nodes in the Menu Tree"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 角色管理授权树携带 W 节点 | ✓ 已实现 | V21 树机制零过滤透传 W 节点（GrantTree/树端点均为通用树渲染）；种子断言 `AuthWorkstationSeedTests.java:59` |
| 台访问由角色驱动 | ✓ 已实现 | role_menu 是唯一授权通道（user_workstation 表已删除——`wrongMachineryTornDown` 断言表不存在）；无 W 授权的角色不产生台条目（树派生的直接后果） |
| 错误机制移除 | ✓ 已实现 | 同 api delta 场景 3 + `legacyMarkerColumnDataIsGoneButConsoleL1Stays`（V21 占位 L1 删除、控制台根保留） |

## 发现的问题

（无 — 3/3 IMPLEMENTED）

## 备注

- **架构修正的背景**：本变更 REPLACES 已归档 `workstation-context-api` 的 per-user 直授设计（用户裁决 2026-11-06："菜单权限只能给角色，用户绑角色"）；REMOVED+ADDED delta 在归档时替换主规格的两个工作站需求。
- V22 编号：V20（menu_info remark，并行会话）之后顺延；V21 的 workstation 列被本迁移删除（列序恢复审计块尾，schema 测试同步）。
- 前端 `workstation-context-web`（rewound）吸收 W 节点模型：chip 数据源=树上的 W 节点、授权抽屉删除、菜单类型 radio 加工作站。

## 下一步

- PASS → `/opsx:archive workstation-w-nodes-api`（归档时 REPLACES api/auth-rbac 主规格的两个工作站需求）
- 前端 `workstation-context-web` 随后实施
