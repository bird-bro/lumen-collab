<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: rep-template-structured-content
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "11/11"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "6/6"
      L3: PASS
---

# Verification: rep-template-structured-content

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (full reactor compiled within `./mvnw test -q`; exit 0 on final reruns) | 11/11 | PASS (full reactor + targeted reruns green after attribution: SettingSchemaSeedTests 5/5 含 M2 修复, AuthLogQueryTests 2/2, manage 瞬时态恢复; RepTempContentApiTests 7/7, AuthRepTempApiTests 3/3, AuthRepTempPermPinTests 3/3) | logs/test-backend.log + /tmp/b4-rerun*.log |
| lumen-web | PASS (`pnpm build`, exit=0) | 6/6 | PASS (`pnpm test`, 33 files / 411 tests green; `tsc --noEmit` clean) | logs/test-lumen-web.log / logs/build-lumen-web.log |

## Attribution Notes (shared-server + parallel-session hygiene)

- **全量首跑 manage 域登录 500 集群**(device/room/dict/audit/log):共享服务器瞬时全局态(并行会话在场),定向重跑全部恢复 — 非本 change(本 change 只动 rep_temp/content)。
- **SettingSchemaSeedTests 14→15**:本 change 延后的 M2(batch1 review 遗留)炸了精确列数断言 — 已修(`TEMP_COLUMNS` 补 `content`)。该行落在并行会话脏集文件内,未单独提交,随他会话 exam-method 提交合流;若他会话不落地,须由本 change 补提交(1 行,归因见 logs/test-backend.log)。
- **AuthLogQueryTests NoSuchMethodError**:orchestrator 重跑命令 `-pl lumen-system` 漏 `-am`,本地仓库陈旧 framework-0.1.0.jar 缺 8 参 getPage — 命令修正(`-pl lumen-system -am`)即绿;框架源码确认重载存在(ILogInfoService.java:73)。非产品缺陷。
- **批次 3 Minor1**(measures:null 崩溃路径)已修(commit 77a72bc);**Minor2**(索引 key 折叠卡片)定级外观,留档不修(见 progress.md)。

## Scenario Alignment

### specs/exam-setting/spec.md delta (5 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Public and private templates share one tree model | IMPLEMENTED (regression, unchanged) | AuthRepTempApiTests/AuthRepTempPermPinTests 3/3+3/3 全程绿 |
| Content and prose columns are exclusive | IMPLEMENTED | RepTempContentApiTests create/update content → jsonb_typeof='object' + finding/impression NULL;null content → prose |
| Tree returns both scopes independently | IMPLEMENTED (regression) | AuthRepTempApiTests;tree 节点无 content(batch1 :108-111) |
| Template page under a folder | IMPLEMENTED (regression) | AuthRepTempApiTests |
| Template payloads carry the content object | IMPLEMENTED | create(batch1)/update(batch2 :233-290)/prose content:null(batch1 :222-226) |
| Structured template stores the section model | IMPLEMENTED | RepTempContentApiTests 心脏 AO/LAD verbatim + impression + 两列 NULL |
| Invalid content rejected | IMPLEMENTED | create 6 kinds + update 5 kinds(PUT),A0400 message 命名字段(version/节名/code/label/目录) |
| Full-replace null content converts structured to prose | IMPLEMENTED | RepTempContentApiTests(batch1 :190-230) |
| SettingSchemaSeedTests 列数账(M2,验收外回归) | FIXED | TEMP_COLUMNS 补 content;重跑 5/5 |

### specs/web-system-console/spec.md delta (6 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Tab toggle switches scope without cross-leak | IMPLEMENTED (regression) | rep-temp-page.test.tsx 个人 scope 切换用例绿 |
| Directory node creates a child directory | IMPLEMENTED (regression) | 新建子目录 prebind 用例绿(上级 select 语义,见本 change 前 UI 演进) |
| Section mode edits sections and measures | IMPLEMENTED | 双形态用例:模式 Radio/960 宽度/节卡片序列化 content/必填校验命名到字段(11 新用例) |
| Mode switch converts prose to sections | IMPLEMENTED | 转换映射用例:表现→首节所见、意见→content.impression |
| Structured to prose conversion refused | IMPLEMENTED | 拒绝用例:warning + 模式保持分节 |
| Structured row renders summary cells | IMPLEMENTED | 「N 节 · M 项」+ content.impression 用例;prose 行不变(15 存量用例全程绿) |
