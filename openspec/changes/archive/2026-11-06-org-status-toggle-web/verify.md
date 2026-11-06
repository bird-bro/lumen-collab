<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-status-toggle-web
  date: 2026-11-06
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "1/1"
      L3: PASS
---

# 验证结果: org-status-toggle-web

> 生成时间: 2026-11-06 · 总体: **PASS**

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `pnpm build` exit 0（✓ built in 3.24s） |
| L2 规格对齐 | 1/1 已实现 | 0 PARTIAL · 0 NOT_IMPLEMENTED |
| L3 测试 | ✓ PASS | `pnpm test` exit 0 · 22 个测试文件 244/244 通过 |

## 场景对齐详情

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Campus status toggle | ✓ 已实现 | `orgs.ts:31-33`（`setOrgStatus(id, status: 0\|1)` → `PUT /system/orgs/{id}/status`）；`orgs/index.tsx:576-584`（翻转 → 调专用状态端点 → `await reload()` 刷新机构树）；`orgs/index.tsx:631-637`（Switch `checked={status === 1}`，无 `system:org:update` 时 `disabled`，切换状态由刷新后的树数据决定）；测试 `orgs-page.test.tsx:432-442`（断言 `setOrgStatus(274, 0)` + 刷新后开关态）与 `api-clients.test.ts:214-221`（端点/方法/`{status:0}` 契约） |

## 下一步

- PASS → `/opsx:archive org-status-toggle-web`
