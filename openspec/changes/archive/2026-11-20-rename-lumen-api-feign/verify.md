<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: rename-lumen-api-feign
  date: 2026-11-20
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "4/4"
      L3: PASS
---

# Verification: rename-lumen-api-feign

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 4/4 | PASS | /tmp/rename-compile2.log · /tmp/rename-test2.log |

## Scenario Alignment

两个 MODIFIED delta 的场景均为主规格超集（=全等，仅需求文本内模块名/配置键名变化，行为零变更）：

| Delta | Scenario | Status | Evidence |
|:--|:--|:--|:--|
| service-topology / Foundation Module Set | Foundation modules resolve | CARRIED | specs delta 场景与主规格逐字一致 |
| service-topology / Foundation Module Set | Deferred module requested | CARRIED | 同上 |
| number-rule / Internal Service Issuance Contract | Issue endpoint rejects non-internal callers | CARRIED | 同上 |
| number-rule / Internal Service Issuance Contract | Feign failure fails fast | CARRIED | 同上 |

## Evidence

- L1：`./mvnw compile -q`（JDK 11，reactor）exit=0（包目录搬移后复验）。
- L3：`./mvnw test -q` 全 reactor exit=0——**427 tests / 0 failures / 0 errors**（patient 58 + us 50 + system 289 + core 26 + common 4；feign/framework 无测试类）。
- 首轮全量出现 1 例 `AuthWorkstationSeedTests.seedStatementsReExecuteAsARowCountNoOp` 失败（expected 1 but was 3）：共享测试库测试钩子硬清扫（`role_menu=0`）扫掉 V22 种子授权 → 幂等测试撞上部分基线、种子重放补种被计为新增。与本变更零交集（lumen-system 仅一行 javadoc）；隔离复跑绿 + 全量复跑绿（12deed8 先例同型）。
- `@FeignClient` `name` 值（`lumen-system-number` / `lumen-patient-registry`）逐字节不变；url 目标值不变（仅占位符键名 `lumen.api.*.url` → `lumen.feign.*.url`，键与配置供给方同批改）。
- 线格式零变更：端点 URL / payload / Feign 契约类名（`PatientApi` / `NumberApi` / fallback / DTO）全部不动 → 已归档的 us-station 双栈（sev+web）与 login-lockout（活跃，零引用）不受影响。
- 残留核查：`lumen-sev` 源码/配置零残留；主规格 patient-domain 已由并行归档同步为 `lumen-feign`；主规格 service-topology / number-rule 由本变更归档同步。保留项均为历史记录（D4 分界）：sdd/reports + progress Review 行、docs/analysis、docs/proposals、归档协调文档（exam-setting / number-rule-unification）、init-project-skeleton 带日期裁决 bullets。
- 用户本地 dev yml（patient/system/us，gitignored）零 `lumen.api.*` 键（平铺与嵌套形式均无）——**配置键改名零用户手改动作**。

## Next

- PASS -> archive rename-lumen-api-feign（归档同步 service-topology / number-rule 两主规格）。
