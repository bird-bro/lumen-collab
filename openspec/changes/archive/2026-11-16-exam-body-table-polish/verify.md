---
verify:
  change: exam-body-table-polish
  date: 2026-11-16
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "6/6"
      L3: PASS
    backend:
      L1: SKIP
      L2: "n/a"
      L3: SKIP
---

# Verification — exam-body-table-polish

Single-stack change (lumen-web only; no API/schema change). Backend runs nothing
to verify — recorded SKIP, not FAIL.

## Stack results

### lumen-web

| Layer | Status | Detail |
|:---|:---|:---|
| L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| L2 spec | 6/6 | all scenarios IMPLEMENTED |
| L3 tests | PASS | `pnpm test`; exit=0; 32 files / 373 tests; logs/test-lumen-web.log |

## Scenario alignment

| Scenario | Status | Evidence |
|:---|:---|:---|
| Left column manages groups without leaving the page | IMPLEMENTED | `lumen-web/src/pages/setting/exam-body/index.tsx:297` (saveGroup → `loadGroups`, list refreshes in place; the selected-row highlight IS the context) |
| Blocked group delete shows the reason | IMPLEMENTED | `lumen-web/src/pages/setting/exam-body/index.tsx:315-318` (refusal `error.msg` surfaces verbatim; group state only reloads on success) |
| 操作 header carries the refresh affordance | IMPLEMENTED | page wires `onRefresh` → `loadBodies` (`index.tsx:533`); shell pins the reload glyph right of the centered 操作 label (`lumen-web/src/pages/system/console-shell.tsx:311-322`) |
| Centered columns | IMPLEMENTED | 编码 `align: 'center'` (`index.tsx:413-415`), 排序/创建时间 centered (`index.tsx:423-424`); 部位 carries no align (default) |
| Forest renders as an expanded tree | IMPLEMENTED | forest → antd-canonical rows via `toTreeRows` (`index.tsx:59-63`); controlled expand-all keys (parents only, `index.tsx:213-221`, `526-527`); 上级部位 column absent from `bodyColumns` |
| Keyword filter prunes to hits plus ancestors | IMPLEMENTED | prune-to-match memo keeps hits + non-empty pruned branches, no-hit keyword empties the table (`index.tsx:244-253`) |

Test-side guards for the above live in `lumen-web/src/test/exam-body-page.test.tsx`
(13 cases: refresh re-fetch, glyph classes, textAlign assertions, tree/expanded
rows, prune semantics, drawer flat-options guard).

## Issues

None.
