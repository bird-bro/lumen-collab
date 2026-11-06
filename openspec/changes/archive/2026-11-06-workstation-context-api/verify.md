<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: workstation-context-api
  date: 2026-11-06
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "9/9"
      L3: PASS
---

# 验证结果: workstation-context-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（lumen-sev/，JDK 11）exit=0 |
| L2 规格对齐 | 9/9 已实现 | api delta（4 场景）+ auth-rbac delta（4 场景 + 种子条款）全 IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit=0；surefire **100 tests / 0 failures / 0 errors / 0 skipped（25 类）** |

## 场景对齐详情

### api delta — ADDED "Workstation Context Endpoints"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 载荷携带工作站上下文 | ✓ 已实现 | `AuthServiceImpl.java:131/175`（login/me 装配 `workstationsOf`）+ `:194`（profile.primaryWorkstation）；测试 `AuthWorkstationTests.payloadsCarryGrantAndDefaultAfterAssignment` |
| Bootstrap 用户回落 admin | ✓ 已实现 | `WorkstationService.java:53-64`（零行 → `["admin"]`）+ `:66-73`（primary 回落）；测试 `bootstrapUserFallsBackToAdminOnly` |
| 全量替换授权 | ✓ 已实现 | `WorkstationService.java:83-103`（事务：软删旧行 + 插入新集；双主台 A0400）；测试 `payloadsCarry…`（liveRows=1 + value=us 重开断言） |
| 授权需按钮权限 | ✓ 已实现 | `UserInfoController.java:113-114`（方法级钉 `system:user:assign-ws`，additive DI）；测试 `assignmentRequiresTheButtonPerm`（A0301） |
| 树节点 L1 携带台标记 | ✓ 已实现 | V21 种子 L1 标记；测试 `l1MenusCarryTheStationMarkers`（admin/radiology/us/endo 逐条恰 1） |

### auth-rbac delta — ADDED "Workstation Authorization"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 主台唯一强制 | ✓ 已实现 | 偏唯一索引（`userWorkstationTableHasPartialUniqueDefaultIndex`）+ 服务端双主台 A0400（`twoDefaultsRejectedWithA0400`，且拒绝后零落库） |
| 授权流向下次登录 | ✓ 已实现 | `payloadsCarry…`（重新登录断言 workstations=2 + primary=radiology） |
| 软删用户的行不再贡献 | ✓ 已实现 | `deletedUsersRowsStopContributing`（软删 → 登录 A0201——users 家族无删除端点，HTTP 层可观察等价） |
| 台根/标记一棵树可见 | ✓ 已实现 | V21 四个 L1 标记 + `assignWsButtonSeededAndAdminGranted`；分组渲染为前端 change 范畴 |

## 发现的问题

（无 — 9/9 IMPLEMENTED）

## 备注

- V21 编号：V19 起草时被并行会话的 V20（menu_info remark）占用序位，Flyway 拒乱序 → 顺延 V21（工件全局同步）。
- **L3 首跑 1 失败已修**：`AuthRoleMenuTests` 断言硬编码种子标题「用户权限」——用户经菜单管理页正当改名为「用户」后测试脆弱性暴露；断言已改为按 mid（标题可由运营编辑是本特性的直接后果），重跑全绿。用户确认保留改名。
- apply 中工件前提修正：users 家族无删除端点，"user-delete cascade" 不存在（design Risks 已记录）。

## 下一步

- PASS → `/opsx:archive workstation-context-api`；前端 `workstation-context-web`（追加式侧栏 + chip + 授权抽屉）随后开工。
