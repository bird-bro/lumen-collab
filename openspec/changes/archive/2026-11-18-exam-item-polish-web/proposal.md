# Proposal — exam-item-polish-web

## Why

The 检查项目 page needs three user-ruled fixes (2026-11-16, coordination doc
`exam-item-polish.md`): (1) the filter-bar 常用项目 checkbox dies — the backend half
is the sibling change `exam-item-common-filter-drop`; (2) the drawer's 常用项目
switch sits alone at the bottom and the numeric fields hide behind placeholder
labels in a compact Input.Group with dead 25% trailing space — relayout with real
labels in banded rows, the switch paired with 排序; (3) the table has no 备注 column
despite the field existing end-to-end.

## What Changes

- Filter bar: remove the 常用项目 checkbox, the `commonOnly` state, and the
  `isCommon` entry in every `load()` call; `pageExamItems` stops sending the param
  (client + `ExamItemPageQuery` type) — **BREAKING** in lockstep with the backend
  change.
- Drawer relayout (kept fields unchanged, no contract touch):
  排序 + 常用项目 switch share one banded row; 单价（元）/ 折扣 / 诊断计费数量
  become real-labeled `Form.Item`s in one banded row (replacing the compact
  Input.Group); 诊断得分 / 审核得分 likewise (labels carry the 留空 = 不计 tooltip);
  备注 TextArea closes the form. The switch is no longer the last form item.
- Table: new 备注 column between 审核得分 and 创建时间 (antd `ellipsis`, — for
  null).
- Tests: page test drops the checkbox-era prose, asserts the 备注 column and the
  banded drawer labels; setting-api test stops asserting the isCommon page param.
- Spec delta: web-system-console `Exam Item Page` — filter bar loses the toggle,
  table gains 备注, drawer tail described as banded rows.

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**: `web-system-console` — the `Exam Item Page` requirement
  (filter bar composition, table columns, drawer form description).

## Impact

- `src/pages/setting/exam-item/index.tsx`, `src/api/exam-item.ts`,
  `src/api/types.ts` (`ExamItemPageQuery` only), `src/test/exam-item-page.test.tsx`,
  `src/test/setting-api.test.ts` — 5 files, no new components, no mock layer
  (ruling 2026-11-05: real backend only).
- The drawer keeps every `ExamItem`/create/update contract field including
  `isCommon` — the entity capability survives per coordination D1.
