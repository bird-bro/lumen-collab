# Tasks — exam-item-polish-web

## 1. Tests first

- [x] 1.1 `exam-item-page.test.tsx`: assert the filter bar has NO 常用项目 checkbox
      and `pageExamItems` is never called with `isCommon`; assert the 备注 column
      renders a remark (and — for null); assert the drawer exposes labeled
      排序/常用项目/单价（元）/折扣/诊断计费数量/诊断得分/审核得分/备注 fields
- [x] 1.2 `setting-api.test.ts`: `pageExamItems` test drops the isCommon query
      entry and its URL assertion (create/update payload assertions keep isCommon)

## 2. Implementation

- [x] 2.1 `api/exam-item.ts` + `types.ts` (`ExamItemPageQuery`): drop `isCommon`
- [x] 2.2 Page: remove the checkbox + `commonOnly` state + `isCommon` in every
      `load()` call; drawer tail → banded rows (排序+常用项目, 计费 triple, 得分
      pair with tooltip, 备注 closing); table gains the 备注 column; header comment
      updated
- [x] 2.3 Drawer tail amendment (user review 2026-11-17): the 排序+常用项目 band
      left a 28px switch floating in a half column — 常用项目 becomes a menus
      是否显示-style option line (`label htmlFor` + `Form.Item noStyle` + `Switch
      id`, one line after 检查方法, before the numeric bands); 排序 joins the
      numeric band as a 4-up row (span 6); 备注 still closes. Page test scenario
      rewritten (option-line row + ordering assertions) and the spec delta
      scenario updated; coordination doc D2 amended.

## 3. Verification

- [x] 3.1 `pnpm test` green (full suite; ledger in the apply log)
- [ ] 3.2 User confirms the visuals (drawer bands + 备注 column) — UI gate before
      archive
