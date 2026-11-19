# Batch 2 Brief: API 契约面测试补全(tasks 2.1–2.2)

Change: rep-template-structured-content · Stack: **仅 lumen-sev 测试代码**(生产代码零改动;除非测试发现真 bug——见下)

## 背景
批次 1 已落地(rep_temp content jsonb 全链路,commit a62da9a)并经 reviewer APPROVED,遗留三条 Minor 全部并入本批:
- **M1**:update-with-content(jsonb 写的 wrapper typeHandler-mapping 路线)是唯一没有测试的 jsonb 写路径 → 补「structured update→page 往返」测试
- **M3**:全部拒绝用例只走 POST → 补 update 路径拒绝用例 + 显式 `content.impression=null` 的往返
- **M2**(登记,不在本批):SettingSchemaSeedTests.TEMP_COLUMNS 补 "content" → Batch 4 协调项,勿动该文件

## 必读
1. `openspec/sdd/reports/rep-template-structured-content-batch1.md` + 同名 `-review.md`(M1/M3 原文)
2. `openspec/changes/rep-template-structured-content/specs/exam-setting/spec.md`(delta 场景)
3. 测试现状:`lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/setting/` 下批次 1 新增/修改的 content 测试类(先 git show a62da9a --stat 找到它们)

## 任务

**2.1 api 层测试补全**(跟随批次 1 的测试 idiom 与文件):
- structured 行 **update→page 往返**(M1:update 带新 content → page 返回新 content,两列仍 NULL)
- update 路径拒绝用例(M3):update body 带无效 content(空节名/空 measure code/label/version=2/目录节点)→ envelope A0400 且 message 命名字段
- 显式 `content.impression=null` 往返(M3):create/update content 且 impression 为 null → page 返回 impression null(非缺省)
- tree 不含 content(若批次 1 已有则确认并引用;缺则补)
- sections 内 measures 为**空数组**的合法往返(内镜节形态,delta 边缘用例)

**2.2 错误码位登记**:核对本批所有拒绝用例的错误码(应全部复用既有 A0400)——若确无新增码位,在报告写明「errors spec 无 delta 需要」;若发现新码位需求,报 NEEDS_CONTEXT 勿自行扩 errors spec。

## TDD 说明(本批特殊)
本批是**覆盖补全**:生产代码已实现且经审。新增测试预期**立即绿**;若有测试 RED = 发现批次 1 真 bug → 报告 DONE_WITH_CONCERNS,列出 RED 证据与最小修复建议,**勿自行改生产代码**(改则越权)。

## 命令
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw -pl lumen-system -am test -q
```
共享测试库:断连/夹具互删重试一次,再失败报 BLOCKED。日志重定向,报告引 exit+计数。

## 提交
单提交,风格 `✅ test(sev): ...`(仅测试文件入提交)。

## 报告
`openspec/sdd/reports/rep-template-structured-content-batch2.md`(<60 行):测试清单+RED/GREEN 状态、2.2 结论、改动文件、concerns。回报 ≤12 行:Status + 一行摘要 + commit sha + 报告路径。
