<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-item-table-polish-web
  date: 2026-11-18
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "17/17"
      L3: PASS
---

# Verification: exam-item-table-polish-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build`, exit=0) | 17/17 | PASS (`pnpm test`, 32 files / 383 tests, exit=0) | logs/build-lumen-web.log · logs/test-lumen-web.log |

## Scenario Alignment

Exam Item Page (8):

| Scenario | Status | Evidence |
|:--|:--|:--|
| Group selection cascades the body options | IMPLEMENTED | exam-item/index.tsx:238-244,559 (clears bodyId, reloads) |
| Create posts to the real backend | IMPLEMENTED | exam-item/index.tsx:247-311 (createExamItem → fresh page query) |
| Filter bar omits the 常用项目 toggle | IMPLEMENTED | exam-item/index.tsx:433-441 (bar), no isCommon in query call:168 |
| 项目 cell carries the code on a second line | IMPLEMENTED | exam-item/index.tsx:338-360 (flexDirection column stack; tertiary second line) |
| Centered count and score columns, renamed headers, no 创建时间 | IMPLEMENTED | exam-item/index.tsx:362-412 (对照码/计费数量 headers; width 110/100; no 创建时间 column in file) |
| Table renders the remark column | IMPLEMENTED | exam-item/index.tsx:417-423 (ellipsis + — for null, before the shell ops column) |
| 操作 header carries the refresh affordance | IMPLEMENTED | exam-item/index.tsx:471-490 (onRefresh + actions; 删除 icon="remove" delete glyph) |
| Drawer order and the closing 常用项目 line | IMPLEMENTED | exam-item/index.tsx:543-547 (编码 required), 553 (名称 maxLength 60), 555-628 (对照码 below 检查方法 / 排序 own row / 3-up band), 631-640 (flag closes below 备注, space-between) |

Exam Body Page (6):

| Scenario | Status | Evidence |
|:--|:--|:--|
| Left column manages groups without leaving the page | IMPLEMENTED | exam-body/index.tsx:273-309 (group CRUD refreshes in place) |
| Blocked group delete shows the reason | IMPLEMENTED | exam-body/index.tsx:311-320 (refusal message verbatim) |
| 操作 header carries the refresh affordance | IMPLEMENTED | exam-body/index.tsx:536 (onRefresh re-fetches bodies) |
| Centered columns (编码 centered; no 排序/创建时间) | IMPLEMENTED | exam-body/index.tsx:410-427 (编码 align center; only 创建时间 hit is the comment :412) |
| Forest renders as an expanded tree | IMPLEMENTED | exam-body/index.tsx:209-224 (controlled expand-all) + toTreeRows :58-63 |
| Keyword filter prunes to hits plus ancestors | IMPLEMENTED | exam-body/index.tsx:242-257 (prune keeps ancestor chains) |

Exam Method Page (3):

| Scenario | Status | Evidence |
|:--|:--|:--|
| Method CRUD round-trips the real API | IMPLEMENTED | exam-method/index.tsx:120-169 (create/update/delete → fresh page load) |
| No 创建时间 column | IMPLEMENTED | exam-method/index.tsx (0 `创建时间` hits; columns 方法名称/方法编码/检查类型) |
| 操作 header carries the refresh affordance | IMPLEMENTED | exam-method/index.tsx:228 (onRefresh), 241 (删除 delete glyph) |

## Issues

- None. Two-layer supersession note: the Exam Item Page deltas of
  `exam-item-external-code-web` and `exam-item-polish-web` (earlier layers of the same
  batch) describe drawer/column positions this change supersedes — the batch's final
  requirement text is THIS change's delta, which lands as main-spec text at its archive
  (order: external-code-web → polish-web → this change).

## Next

- PASS → archive (batch order: exam-item-common-filter-drop → exam-item-external-code-sev
  → exam-item-external-code-web → exam-item-polish-web → exam-item-table-polish-web).
