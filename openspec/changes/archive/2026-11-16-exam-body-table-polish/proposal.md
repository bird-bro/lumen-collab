# Proposal — exam-body-table-polish

## Why

User request 2026-11-16 on `/setting/exam-bodies` (检查部位 page), four concrete
defects on the RIGHT bodies table:

1. **刷新icon缺失** — the bodies table builds its 操作 column by hand inside
   `bodyColumns`, so it never passes `onRefresh`/`actions` to `ConsoleTable` —
   and the refresh glyph only renders from the shell's auto action column
   header. The LEFT group table already carries it; the right table is the
   outlier.
2. **操作icon错误** — the row 删除 verb omits `icon`, so `ConsoleAction`'s
   default (`edit`) renders a pencil for delete. Every other console page passes
   `icon="remove"` for deletes (the group table on this very page does).
3. **表头「部位编码」** — should read 编码 (the drawer label 部位编码 stays).
4. **居中** — 编码 / 排序 / 创建时间 columns render left-aligned; the user wants
   them centered (header + cells).

## What Changes

- **lumen-web only** (`src/pages/setting/exam-body/index.tsx` + its test):
  - The bodies table drops the hand-rolled 操作 column and composes the
    `ConsoleTable` `actions` prop + `onRefresh` (same shape as the group table
    on the same page) — the shell renders the centered 操作 header with the
    refresh glyph pinned to its right edge; clicking re-fetches the selected
    group's bodies.
  - The row 删除 verb passes `icon="remove"` (delete glyph, not the edit
    default).
  - Column header 部位编码 → 编码; `align: 'center'` on 编码 / 排序 / 创建时间.
- Existing-page edit → no UI prototype (per workflow); user confirms visuals on
  the real backend.

## Affected Stacks

- single-stack: lumen-web

## Depends-On

- none - standalone

## Scope

- In scope: the exam-body page's right table chrome (refresh affordance, action
  glyphs, one header rename, three column alignments), tests.
- Out of scope: the drawer form labels (部位编码 stays), the left group table
  (already conformant), any API/schema change, other setting pages.

## Success Criteria

- The bodies table's 操作 header carries the refresh glyph at its right edge;
  clicking re-fetches `listExamBodies` for the selected group.
- Row verbs render 编辑 with the edit glyph and 删除 with the delete glyph.
- The table header reads 编码, and 编码 / 排序 / 创建时间 render centered
  (header and cells); 部位 / 上级部位 keep their alignment.
- Existing tests stay green (updated anchor: 部位编码 → 编码).

## Constraints

- No new API contract, no schema change (bypass tier: single lightweight
  proposal, lumen-web only).
- Spec `web-system-console` § Exam Body Page with In-Page Group Management
  delta: column list restated (部位 / 编码 / 上级部位 / 排序 / 创建时间) + the
  refresh affordance documented on this page's requirement.

## Risks

- `ConsoleTable`'s auto action column centers its cells (the manual column was
  left-aligned) — an intentional conformance gain, matches every other console
  page.
  → Mitigation: none needed; covered by the alignment test.

## Follow-up — 2026-11-16 tree display (user confirmed same day)

User ruling: the RIGHT bodies list should render as a **tree**, not a flat
paged list. The server already returns the `up_id` forest with `children`
(`BodyTreeNode`); flattening it and re-expressing hierarchy through a 上级部位
text column is a lossy round-trip, and per-group body counts (anatomical
regions, 2–3 levels) never justify a pager. House precedent: the menus page
renders its tree through the same `ConsoleTable` (AntD children tree rows).

- The table feeds the forest directly; the 上级部位 column is dropped (the tree
  IS the parent read). Every level starts expanded (controlled keys, re-opened
  on refetch — datasets are small); users can still collapse.
- The 部位 fuzzy filter becomes prune-to-match: a hit keeps its ancestor chain
  (肺 under 胸部 still shows 胸部), non-matching branches drop entirely; a
  no-hit keyword empties the tree.
- The drawer's 上级 select keeps the flattened list (options are flat).
- `ConsoleTable` gains an additive `expandable` passthrough prop (undefined =
  today's behavior; all other pages unaffected).
- No API/schema change; single-stack; folded into this change per the
  menu-root-parent-option task-1.3 follow-up precedent.
