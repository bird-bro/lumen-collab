<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: button-perm-enforcement-api
  date: 2026-11-06
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "4/4"
      L3: PASS
---

# 验证结果: button-perm-enforcement-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（lumen-sev/，JDK 11）exit=0 |
| L2 规格对齐 | 4/4 已实现 | auth-rbac delta 全场景 IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit=0；surefire **87 tests / 0 failures / 0 errors / 0 skipped（23 类）** |

## 场景对齐详情（auth-rbac delta — ADDED "Button-Level Permission Enforcement"）

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 写端点拒绝无按钮权限的调用 | ✓ 已实现 | 22 个方法级钉（如 `UserInfoController.java:64/80/90`）；矩阵 deny 侧 ×22（`AuthPermEnforcementTests.java:273` 起的 `expectDenyThenPass` 全端点，页面链授权 → A0301 + 零落库断言） |
| 仅按钮权限即可写（且 list 仍被页面权限拒） | ✓ 已实现 | 矩阵 pass 侧 ×22 + list-denial 断言（`AuthPermEnforcementTests.java:310`，D2 语义实证） |
| 种子后超管全按钮可见（22 节点 + 全授权） | ✓ 已实现 | `V15__button_perm_nodes.sql`（22 内容守卫插入 + `:118` 超管授权块）；`AuthButtonSeedTests.java:59`（清单逐条恰 1，up+perms 断言）+ `:73`（授权计数按清单 perms 过滤 = 22） |
| 重放种子为 no-op | ✓ 已实现 | `AuthButtonSeedTests.java:88`（逐语句重执行，行数不变） |

## 发现的问题

（无 — 4/4 IMPLEMENTED）

## 备注

- 计数修正：提案/设计/tasks 初稿的 "21" 为笔误，实际 22（3+4+3+3+3+3+3）；工件已同步修正并重戳合同。
- V15 首稿的显式 id + ON CONFLICT 写法在共享库被软删残行占位时静默插 0 行，已改为内容守卫
  （design D1 记录 refined version）；history 行删除重放，未 repair-and-diverge。
- 并行会话 org-management-redesign（V16-V18）在飞行中改动了 org 模型；本变更的 fixture 已顺应
  新现实（org fixture 建院区、dep 锚默认院区）。verify 在 org-redesign 落库后跑，87 全绿。

## 下一步

- PASS → `/opsx:archive button-perm-enforcement-api`（归档时同步 `openspec/specs/auth-rbac/spec.md`）
