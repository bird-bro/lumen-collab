<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: menu-management-api
  date: 2026-11-06
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "8/8"
      L3: PASS
---

# 验证结果: menu-management-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（lumen-sev/，JDK 11）exit=0 |
| L2 规格对齐 | 8/8 已实现 | 5 api + 3 auth-rbac 场景全 IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit=0；surefire 82 tests / 0 failures / 0 errors / 0 skipped（22 类） |

## 场景对齐详情

### api delta — ADDED "Menu Management Endpoints"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 按钮节点创建往返（树可见 + perms 回读 + 超管授权） | ✓ 已实现 | `MenuController.java:77` → `MenuManageService.java:77-95`；测试 `AuthMenuManageTests.java:63-97`（id 回读、树含 type=F + perms、role_menu(1,id)=1） |
| 创建拒绝按钮父节点 | ✓ 已实现 | `MenuManageService.java:169-174`（rejectButtonParent，A0400）；测试 `AuthMenuManageTests.java:99-122` |
| 移动拒绝环（self + 自身子树） | ✓ 已实现 | `MenuManageService.java:180-191`（isInsideSubtree 走上链）+ `101-110`（update 守卫）；测试 `AuthMenuManageTests.java:124-163`（self/子孙拒绝且树完好） |
| 删除清理 role_menu 绑定 | ✓ 已实现 | `MenuManageService.java:131-143`（deleteById + `roleMenuMapper.delete` :140，一事务）；测试 `AuthMenuManageTests.java:165-210`（菜单 deleted=1、绑定 deleted=1、树不再渲染） |
| 删除拒绝有下级节点 | ✓ 已实现 | `MenuManageService.java:193-201`（hasLiveChildren）+ `135-138`；测试 `AuthMenuManageTests.java:168-179`（拒绝后父节点仍 live） |

需求文本附加条款：类级 `system:menu:list` 门控覆盖全部新端点（`MenuController.java:43`，无方法级注解）；结构性拒绝走 `BusinessException(ERROR_A0400, <描述性 msg>)`；perms 逐字存储无校验（服务层无格式检查，测试断言 `ut.menu:delete:<ts>` 原样回读）；Bean Validation（空标题/非法 type）→ A0400 信封（`AuthMenuManageTests.java:245-257`）。

### auth-rbac delta — ADDED "Menu Node Lifecycle Administration"

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 新节点即时超管可见（CREATE 授权） | ✓ 已实现 | `MenuManageService.java:207-221`（autoGrantAdmin，NOT-EXISTS 守卫插 role 1）；测试 `AuthMenuManageTests.java:88-93` |
| 更新不触碰授权 | ✓ 已实现 | `MenuManageService.java:101-127`（update 全程无 roleMenuMapper 调用）；测试 `AuthMenuManageTests.java:212-243`（改名+移动后 grant 计数恒为 1） |
| 删除按钮停发 perms | ✓ 已实现 | `MenuManageService.java:140`（绑定软清）+ @TableLogic 软删；测试 `AuthMenuManageTests.java:186-198` |
| 写端点页面级门控（拒绝先于 controller） | ✓ 已实现 | 测试 `AuthPermEnforcementTests.java:196-250`（无权用户 POST/PUT/DELETE 全 A0301，无行落库、探针节点无恙） |

## 发现的问题

（无 — 8/8 IMPLEMENTED，无 PARTIAL，无 NOT_IMPLEMENTED）

## 备注

- 验证期间工作树内有并行会话的 lumen-web 布局改动（BasicLayout 等），不属于本变更范围，backend reactor 不受影响。
- `openspec validate`：valid（2 条需求文本超长的文体 warning，与库内既有 spec 密度一致，不阻塞）。

## 下一步

- PASS → `/opsx:archive menu-management-api`（归档时同步 `openspec/specs/api/spec.md` + `auth-rbac/spec.md`；中文简报 `docs/proposals/menu-management.zh.md` 已就位）
- 前端 `menu-management-web` 可随后端落地开始联调（backend-first 已满足）
