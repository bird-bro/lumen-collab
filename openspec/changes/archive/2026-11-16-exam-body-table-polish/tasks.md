# Tasks — exam-body-table-polish

## 1. Frontend (lumen-web — single-stack; no backend change)

- [x] 1.1 TDD red: `exam-body-page.test.tsx` — (a) the bodies table's 操作
      header carries a 刷新列表 button and clicking it re-fetches
      `listExamBodies('CT', 1)`; (b) the row 删除 verb renders the delete glyph
      (`.anticon-delete`) and 编辑 the edit glyph; (c) 编码 / 排序 / 创建时间
      headers + cells render `textAlign: center`. Anchor rename 部位编码 → 编码
      applied everywhere. Run: 4 failed / 7 passed (the 3 new tests + the
      renamed-anchor query test; log `logs/test-exam-body-red.log`).
- [x] 1.2 Green: `src/pages/setting/exam-body/index.tsx` — bodies table drops
      the hand-rolled 操作 column for the `ConsoleTable` `actions` prop +
      `onRefresh` (delete verb gains `icon="remove"`); header 部位编码 → 编码;
      `align: 'center'` on 编码 / 排序 / 创建时间. Verify: full `pnpm test`
      exit 0 (32 files / 371 tests; log `logs/test-lumen-web.log`).
- [x] 1.3 `pnpm build` exit 0 (log `logs/build-lumen-web.log`).

### Follow-up — tree display (user confirmed 2026-11-16)

- [x] 1.4 TDD red: fixture grows a second root (腹, leaf); `exam-body-page.test.tsx`
      — (a) 胸部 row renders an expand control opened by default, leaf 肺 renders
      no toggleable control, no 上级部位 header; (b) keyword 肺 keeps the 胸部→肺
      chain (XB visible) and drops 腹; a no-hit keyword empties the table; (c)
      name-query test rewritten to prune semantics; (d) guard: the drawer's 上级
      dropdown still lists the flattened bodies. Run: 2 failed / 11 passed
      (prune test + tree test; log `logs/test-exam-body-tree-red.log`).
- [x] 1.5 Green: page feeds the forest to `ConsoleTable` (tree rows via AntD
      children), drops the 上级部位 column + the parentName flatten for the table
      (drawer keeps a flat options list), controlled expand-all keys re-opened on
      refetch, prune-to-match filter; `console-shell.tsx` gains an additive
      `expandable` passthrough. Two rc-table catches fixed in place: leaves
      forced into expandedRowKeys get a dead expand button (collect parent keys
      only), and the API's `children: []` reads as "has children" to rc-table —
      leaves are stripped to the antd-canonical no-children shape via
      `toTreeRows` (the leaf's residual `-spaced` icon is antd's invisible
      alignment placeholder). Verify: full `pnpm test` exit 0 (32 files / 373
      tests; log `logs/test-lumen-web.log`).
- [x] 1.6 `pnpm build` exit 0 (log `logs/build-lumen-web.log`).

## 2. Close-out

- [x] 2.1 User confirms visuals on the real backend (`/setting/exam-bodies`) —
      confirmed 2026-11-16 (「确认可以归档」).
- [x] 2.2 `/opsx:verify` (L1 build / L2 WHEN-THEN / L3 tests) → verify.md:
      **PASS** (L1 exit=0; L2 6/6 IMPLEMENTED; L3 32 files / 373 tests exit=0).
