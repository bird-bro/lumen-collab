<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: org-status-toggle-api
  date: 2026-11-06
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# 验证结果: org-status-toggle-api

> 生成时间: 2026-11-06 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw compile -q`（JDK 11, lumen-sev/）exit 0 |
| L2 规格对齐 | 2/2 已实现 | 0 PARTIAL · 0 NOT_IMPLEMENTED |
| L3 测试 | ✓ PASS | `./mvnw test -q` exit 0 · lumen-system 26 个测试类全绿（含 AuthOrgCrudTests 5/5、AuthPermEnforcementTests 8/8） |

## 场景对齐详情

| 场景 | 状态 | 证据 |
|:---|:---|:---|
| Valid status toggle | ✓ 已实现 | `OrgController.java:80-84`（`@PutMapping("/{id}/status")` + `system:org:update` 钉扎 + `@Valid`）；`OrgServiceImpl.java:139-150`（requireLiveOrg + 仅 status 列的聚焦 patch，返回 TRUE → 统一 200 信封 `data:true`）；集成测试 `AuthOrgCrudTests.java:124-156`（`{"status":0}` → code 200/data true/回读 status=0/profile 字段不变 → `{"status":1}` 复启） |
| Invalid status body | ✓ 已实现 | `SetOrgStatusRequest.java:20-22`（`@NotNull @Min(0) @Max(1)` Bean Validation）；`OrgServiceImpl.java:215-223`（validateStatus 服务端兜底，非 0/1 抛 A0400）；集成测试 `AuthOrgCrudTests.java:159-179`（`{}` → A0400、`{"status":2}` → A0400，且回读确认存储状态未被改动） |

## 备注

- 首次验证（22:07）被并行会话 `org-logo-upload` 的中间态污染（`upload.avatar` 占位符半落地 + 共享测试服务器态漂移），当时的 L3 结论作废；本次（23:03）在工作区稳定后重跑，全绿。曾被波及的 `AuthPermEnforcementTests` 本次 8/8 通过，证实此前失败与 org-status-toggle 无关。

## 下一步

- PASS → `/opsx:archive org-status-toggle-api`
