<!--
  Written by /opsx:verify. This file is the archive credential - /opsx:archive
  parses the `overall` field below. Do not hand-edit; re-run /opsx:verify to refresh.
-->
---
verify:
  change: dict-model-spec-sync
  date: 2026-11-05
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "6/6"
      L3: PASS
    lumen-web:
      L1: SKIP
      L2: "-"
      L3: SKIP
---

# 验证结果: dict-model-spec-sync

> 生成时间: 2026-11-05 · 总体: **PASS**

## backend

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1 构建 | ✓ PASS | `./mvnw -pl lumen-system -am test` 内含全模块编译（JDK 11，无告警错误） |
| L2 规格对齐 | 6/6 已实现 | delta 6 场景全部 IMPLEMENTED（映射见下；本 change 为规格同步，行为由 V6-V9 已落库实现承载） |
| L3 测试 | ✓ PASS | lumen-system 全套件 65/65（作为本 change 指定的 L2/L3 证据运行；change diff 本身零代码） |

## lumen-web

| 层级 | 状态 | 详情 |
|:---|:---|:---|
| L1/L2/L3 | SKIP | 本 change diff 仅触及 openspec/ 与 docs/proposals/，前端零改动（其字典页适配随 V9 已落地且测试绿） |

## 场景对齐详情

| # | delta 场景 | 证据 | 结论 |
|:--|:--|:--|:--|
| 1 | Create a catalog（create_by 审计桥填充） | `AuthDictCrudTests.catalCreateLandsRowWithOperatorAuditAndRejectsDuplicateCode` | IMPLEMENTED |
| 2 | Duplicate catalog code rejected（A 类码） | 同上（场景 2，A0714） | IMPLEMENTED |
| 3 | Create a detail under a catalog（caId 单锚定、value 为文本） | `AuthDictCrudTests.detailCreateAnchorsCaIdAndListsByCaCodeSorted` | IMPLEMENTED |
| 4 | Duplicate detail value under a catalog rejected（A0714） | 同上 | IMPLEMENTED |
| 5 | List details by catalog code（活行、sort 序、?caCode= 契约） | 同上 + `GET /list?caCode=gender` 实测 200（男/女/未知） | IMPLEMENTED |
| 6 | Fresh database carries the starter dictionary（7 目录/30 明细、common_status id 1000、启用停用在 value） | `AuthSystemDataMigrationTests`（tag/flag 列、uk_dict_detail_ca_id_value、join-by-code）+ `AuthDataLayerTests`（catalog 1000、value 启用/停用）+ psql 实测计数 7/30/id=1000/明细 100001-100350 | IMPLEMENTED |

## 观察项（非阻断）

- 共享库中 common_status 两条明细的内部 id 为 10001/10002（用户手工重锚目录时的连带操作），
  全新库为 1/2 —— 纯内部代理键分歧，零行为差异、无外部引用。规格场景按 code 关联、
  id 无关，不受影响。可选的后续对齐迁移不属本 change（契约明确排除迁移改动）。

## 层级说明

- **L1**：本 change 零源码改动，构建检查以后端套件内含编译为准（PASS）。
- **L2**：delta 场景逐条映射到既有测试 + 实测接口/数据，全部 IMPLEMENTED。
- **L3**：lumen-system 65/65 全绿（2026-11-05 运行），即场景映射声明的测试证据本体。
