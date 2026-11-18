<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-item-external-code-web
  date: 2026-11-18
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "6/6"
      L3: PASS
---

# Verification: exam-item-external-code-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build`, exit=0) | 6/6 | PASS (`pnpm test`, 32 files / 383 tests, exit=0) | logs/build-lumen-web.log · logs/test-lumen-web.log |

## Batch-layer convention

Middle layer of the exam-item batch: positional details this delta fixed (对照编码
drawer field right after 项目编码; 对照编码 table column right after 项目编码) were
superseded by `exam-item-external-code-web`-later layers — the 2026-11-17 header merge
+ drawer reorder in `exam-item-table-polish-web` (archives LAST, lands the final Exam
Item Page text). Scenarios scored on DURABLE semantics; supersessions annotated.

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Group selection cascades the body options | IMPLEMENTED | exam-item/index.tsx:238-244 (reload + stale-body clear) |
| Create posts to the real backend | IMPLEMENTED | exam-item/index.tsx:247-311 |
| Filter bar omits the 常用项目 toggle | IMPLEMENTED | exam-item/index.tsx:433-441; query carries no isCommon (:168) |
| Table renders the remark column | IMPLEMENTED | exam-item/index.tsx:417-423 |
| Drawer tail lays out the option line and banded numbers | IMPLEMENTED | durable parts present (flag option line :631-640; real-labeled bands :585-629) — positions superseded by table-polish-web |
| External mapping code round-trips | IMPLEMENTED | drawer field 对照码 maxLength 32 (exam-item/index.tsx:566-571, renamed/repositioned per table-polish-web), payload carries externalCode (api/exam-item.ts create/update + types.ts:812,836), table column 对照码 with — for null (exam-item/index.tsx:362-369); round-trip covered by page tests (external-code create/edit-echo cases) |

## Issues

- None blocking. Positional supersessions annotated (see Batch-layer convention).

## Next

- PASS → archive (batch order: common-filter-drop → external-code-sev →
  external-code-web → polish-web → table-polish-web).
