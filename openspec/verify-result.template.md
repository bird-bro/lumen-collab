<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: CHANGE_NAME
  date: YYYY-MM-DD
  overall: PASS_WITH_ISSUES   # PASS | PASS_WITH_ISSUES | FAIL
  stacks:
    STACK_ID:
      L1: PASS                 # PASS | FAIL | SKIP
      L2: "5/6"                # implemented/total scenarios
      L3: PASS                 # PASS | FAIL | SKIP
---

# 验证结果: CHANGE_NAME

> 生成时间: YYYY-MM-DD · 总体: **PASS_WITH_ISSUES**

## STACK_ID

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | <build-cmd> 成功 |
| L2 规格对齐 | 5/6 已实现 | 1 PARTIAL |
| L3 测试 | ✓ PASS | 4/4 测试通过 |

## 场景对齐详情

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| 提交报修单 | ✓ 已实现 | repair.vue:42-58 |
| 提交后刷新列表 | ⚠ PARTIAL | 缺少刷新逻辑 |

## 发现的问题

⚠ PARTIAL: "提交后刷新列表"
- 期望: 提交报修单后列表自动刷新
- 实际: repair.vue 提交成功后未调用列表刷新
- 建议: 提交回调中 emit 事件或调用 store.refresh()

## 下一步

- PASS → `/opsx:archive CHANGE_NAME`
- PASS_WITH_ISSUES → 确认后归档，或 `/opsx:apply CHANGE_NAME` 修复 PARTIAL
- FAIL → `/opsx:apply CHANGE_NAME` 修复阻断项后重新 `/opsx:verify`
