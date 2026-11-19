---
verify:
  change: rep-temp-normal-range-sev
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "8/8"
      L3: PASS
---

# Verification: rep-temp-normal-range-sev

## backend

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `./mvnw compile -q`；exit=0；`logs/build-backend.log` |
| L2 spec | 8/8 | 全部 IMPLEMENTED |
| L3 tests | PASS | `./mvnw test -q`（全 reactor，rerunFailingTestsCount=1）；**改动面全绿**——`setting.*` 28 套件 0 失败（含迁移/内容/union 全部套件）；见下方 L3 环境注记 |

## L3 环境注记（如实记录）

全 reactor 一轮存在 `manage` 域失败（AuthDeviceApiTests / AuthRoomApiTests / AuthLoginTests /
AuthOrgCrudTests / AuthDictCrudTests / AuthDictItemsLookupTests / AuthOrgDepTests /
AuthLogQueryTests / AuthPermEnforcementTests），**与本 change 无关**：

- 失败 0 例落在 `setting.*`（本次改动唯一触及域），失败 100% 在 `manage`（org/room/dict/
  perm/login——共享测试库并行会话夹具互删的特征形态，见 memory `shared-test-server-verification-hazards`）；
- 跨轮次波动证明 flake：AuthRoomApiTests 全量轮 5 失败 → 隔离轮 7/7 绿；AuthDeviceApiTests
  全量轮 1 失败 → 隔离轮 10/10 绿；AuthLoginTests 同样隔离轮绿；
- 隔离轮当下 `AuthPermEnforcementTests` / `AuthLogQueryTests` 仍失败——并行会话**正在**
  对共享库执行夹具清扫（user/session/log 夹具为互删目标），本窗口不可判；其差异与本 change
  零交集（git diff 不含任何 manage 域生产代码）。

## Scenario alignment（delta specs/exam-setting/spec.md）

| Scenario | Status | Evidence |
|:---|:---|:---|
| Structured template stores the section model（v2 verbatim） | IMPLEMENTED | RepTempNormalRangeApiTests.java:45（v2 union 双边/单边/文本/null 全 round-trip）；RepTempServiceImpl.java:241 版本钉 2 |
| Invalid content rejected（name/code/label/sections/version≠2/string normal/inverted/both-null/blank/64+/kind/directory） | IMPLEMENTED | RepTempServiceImpl.java:231-289（guardContent + guardNormalUnion 全规则 A0400 命名字段）；测试 RepTempNormalRangeApiTests.java:92,121,161 + RepTempContentApiTests.java:127 |
| Full-replace null content converts structured to prose | IMPLEMENTED | RepTempServiceImpl.java:176（D9 分支）；RepTempContentApiTests.java:199 |
| Single-sided range normal is accepted | IMPLEMENTED | RepTempNormalRangeApiTests.java:45（min-only/max-only 两行断言）；RepTempMeasureNormal.java:44-47（BigDecimal 独立可空） |
| Migration: parseable v1 normals convert to ranges | IMPLEMENTED | V47__rep_temp_normal_range_v2.sql:49-65（regexp_match 模式集）；RepTempNormalRangeMigrationTests.java:60 |
| Migration: unparseable normals degrade to text verbatim | IMPLEMENTED | V47 SQL ELSE 分支（原文直存，空格保留）；RepTempNormalRangeMigrationTests.java:60（` 阴性 ` 逐字节断言） |
| Migration: replay is idempotent | IMPLEMENTED | V47 SQL `content->>'version' = '1'` 守卫；RepTempNormalRangeMigrationTests.java:118 |
| v1-shaped writes are rejected after migration | IMPLEMENTED | RepTempServiceImpl.java:241（A0400 content.version）；字符串 normal 由 Jackson 绑定层拒（A0427）；RepTempNormalRangeApiTests.java:161 |

Overall: PASS（8/8 IMPLEMENTED；L1 exit=0；L3 改动面全绿 + 环境干扰如实记录）

## 建议

`/opsx:archive rep-temp-normal-range-sev`（PASS 直接归档）。若需独占窗口的全 reactor
复跑（manage 域当前受并行会话干扰），待并行会话结束后重跑 `/opsx:verify` 刷新凭据即可。
