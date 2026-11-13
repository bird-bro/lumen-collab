# Tasks

Frontend scope of the coordination feature `exam-setting` (shared contracts in
`openspec/coordination/exam-setting.md`). Gate: starts only after `exam-setting-api`
is user-verified. No mock layer — prototype may use inline static demo data
(never via `src/api/`), real wiring replaces it in the same task. UI prototype first
(new pages), user confirms visuals before wiring.

## 1. Foundations

- [x] 1.1 API modules `src/api/setting/`: `exam-body-group.ts`, `exam-body.ts`,
  `exam-method.ts`, `exam-item.ts`, `rep-temp.ts` — typed per the exam-setting
  endpoint deltas through the existing console request layer (envelope unwrap, error
  passthrough). Verify: `pnpm build` green; types compile.
- [x] 1.2 Component registry entries for the four seeded paths
  (`setting/exam-item/index`, `setting/exam-body/index`, `setting/exam-method/index`,
  `setting/rep-template/index`) so menu-driven routing resolves them once the backend
  seeds land. Verify: registry compiles; unknown-path fallback unchanged.

## 2. UI prototype (static demo data inline only)

- [x] 2.1 Prototype the four pages' layouts (filter bar + table + Drawer; 检查部位
  left group column; 报告模板 tab+tree) with inline hard-coded demo rows. Verify:
  user confirms visuals (text/DOM checks, no automatic screenshots).

## 3. 检查方法 page

- [x] 3.1 Wire `setting/exam-method/index`: dict 1021 检查类型 filter, 名称/编码
  search, `GET /page` table, Drawer create/edit, confirm-delete (backend refusal
  message surfaced verbatim). Verify: RED→GREEN text/DOM checks against the real
  backend; CRUD round-trip.

## 4. 检查部位 page

- [x] 4.1 Wire left group column (list/新建/编辑/删除 on
  `exam-body-groups/list`) + right body table (`exam-bodies/list` tree rendered as
  rows with 上级部位), Drawer with optional parent tree select. Verify: text/DOM
  checks — group CRUD refreshes both panes; blocked group delete shows the backend
  reason.

## 5. 检查项目 page

- [x] 5.1 Wire filters (检查类型/部位分组/常用项目/关键词) + `GET /page` table with
  server-resolved 部位/方法 names + Drawer: 分组→部位 cascade (group change reloads
  body options and clears the stale body), 检查方法 multi-select id array, numeric
  inputs for price/rate/tag/scores, 常用项目 switch. Verify: text/DOM checks — page
  filter by group returns only that group's bodies' items; create/edit round-trip;
  unknown-modality rejection surfaces.

## 6. 报告模板 page

- [x] 6.1 Wire 公共/私有 tabs (type 1/2 threaded into every call), left modality
  select + tree (node 新建/编辑/删除), right paged template table + name search,
  Drawer flag radio 目录/模板 toggling the finding/impression textareas. Verify:
  text/DOM checks — tab switch reloads both panes; private create binds the owner
  server-side; non-owner private rows never render.

## 7. Gating + suite

- [x] 7.1 Perm gating: hide/disable affordances on missing
  `setting:<resource>:<action>` per the Button-Level Affordance Gating requirement;
  verify with the seeded admin role (grants visible) and a stripped test role
  (affordances absent). Verify: text/DOM checks.
- [x] 7.2 Run the configured suite: `pnpm build` exit 0 + the page-level checks green
  (exit codes + counts reported, log to file — no full-log dump).
