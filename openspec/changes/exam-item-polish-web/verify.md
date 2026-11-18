<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-item-polish-web
  date: 2026-11-18
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "5/5"
      L3: PASS
---

# Verification: exam-item-polish-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build`, exit=0) | 5/5 | PASS (`pnpm test`, 32 files / 383 tests, exit=0) | logs/build-lumen-web.log · logs/test-lumen-web.log |

## Batch-layer convention

This change is a MIDDLE LAYER of the exam-item batch. Positional details its delta
fixed (remark column between 审核得分 and 创建时间; drawer tail with the 常用项目
line between 检查方法 and the numeric bands, 4-up band) were superseded by
`exam-item-table-polish-web` (2026-11-17 drawer review, archives LAST and lands the
final Exam Item Page text). Scenarios are scored on their DURABLE semantics; each
superseded positional detail is annotated below and re-verified in
`exam-item-table-polish-web/verify.md`.

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Group selection cascades the body options | IMPLEMENTED | exam-item/index.tsx:238-244 (reload + stale-body clear) |
| Create posts to the real backend | IMPLEMENTED | exam-item/index.tsx:247-311 (POST → fresh page query, no mock) |
| Filter bar omits the 常用项目 toggle | IMPLEMENTED | exam-item/index.tsx:433-441 (bar has no checkbox); page query carries no isCommon (:168) — backend counterpart `exam-item-common-filter-drop` (ItemPageQuery/impl 0 hits) |
| Table renders the remark column | IMPLEMENTED | exam-item/index.tsx:417-423 (ellipsis + hover title + — for null) — position note: now between 审核得分 and 操作 (创建时间 column retired by table-polish-web) |
| Drawer tail lays out the option line and banded numbers | IMPLEMENTED | durable parts all present: 常用项目 option line label+switch on one line (exam-item/index.tsx:631-640, space-between), real-labeled numeric band, 备注 TextArea — position notes: flag now closes BELOW 备注; band is 3-up with 排序 own row (table-polish-web round 2-4) |

## Issues

- None blocking. Two positional supersessions (see Batch-layer convention) — final
  state verified in exam-item-table-polish-web/verify.md (17/17).

## Next

- PASS → archive (batch order: common-filter-drop → external-code-sev →
  external-code-web → polish-web → table-polish-web).
