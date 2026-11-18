# Tasks — exam-item-table-polish-web

## 1. Tests first (red)

- [x] 1.1 `exam-item-page.test.tsx`: rewrite the 对照编码 column test → merged 项目
      cell (code rides the name cell, no standalone 项目编码 column), 对照码 header,
      new header-polish test (renames + 计费数量/诊断得分/审核得分 centered +
      no 创建时间 column)
- [x] 1.2 `exam-body-page.test.tsx`: the right-table polish test drops the
      创建时间 centered assertion → column-absent assertion (编码 stays centered)
- [x] 1.3 `exam-method-page.test.tsx`: new no-创建时间-column assertion
- [x] 1.4 targeted vitest run confirms RED (header assertions fail against the
      unedited pages)

## 2. Pages (green)

- [x] 2.1 `exam-item/index.tsx`: merge 项目编码 into the 项目 cell (tertiary span,
      ellipsis kept), rename 对照编码→对照码 / 诊断计费数量→计费数量, center the
      three count/score columns, drop the 创建时间 column, refresh the doc comment
- [x] 2.2 `exam-body/index.tsx`: drop the 创建时间 column + comment touch-up
- [x] 2.3 `exam-method/index.tsx`: drop the 创建时间 column

## 3. Verification

- [x] 3.1 targeted vitest green (4 files / 27 tests, exit=0, log
      `/tmp/exam-item-table-polish-test.log`)
- [x] 3.2 full web suite green (32 files / 373 tests, exit=0, same log) +
      `pnpm build` exit=0 (`/tmp/exam-item-table-polish-build.log`)

## 4. Review round 2 (2026-11-17 drawer + ops chrome)

- [x] 4.1 tests: drawer layout rewrite (shortened labels, 编码 required blocks an
      empty save, 名称 maxLength 60, new order via combobox roles for the Selects,
      flag row space-between), ops-chrome tests for exam-item + exam-method (header
      refresh, delete glyph), 编码 filled in the create/external round-trip tests
- [x] 4.2 `exam-item/index.tsx`: drawer reorder (编码 required, 名称 60, 对照码 below
      检查方法, 排序 own row, 3-up 单价/折扣/计费数量 band, 常用项目 closes below
      备注 label-left/switch-right) + ops chrome (delete glyph on 删除, refresh in
      the 操作 header via actions/onRefresh)
- [x] 4.3 `exam-method/index.tsx`: ops chrome (delete glyph on 删除, refresh in the
      操作 header) — same convention, flagged to the user
- [x] 4.4 three-page vitest green (38 tests) + full suite 32 files / 383 tests green +
      `pnpm build` exit=0 (logs `/tmp/exam-item-table-polish-{test,build}.log`;
      two unrelated per-run flakes — device-page then menus-page — both green
      isolated and in the final full run)

## 5. Review round 3 (2026-11-17 widths + flag row)

- [x] 5.1 column widths: 单价（元） 110, 折扣/计费数量/诊断得分/审核得分 unified at 100
- [x] 5.2 drawer: 常用项目 option line moves ABOVE 备注 (label-left/switch-right
      kept) - 备注 closes the form again; test order assertion flipped
- [x] 5.3 spec delta (drawer-order scenario + requirement text), design D2/D6,
      proposal round-3 note synced; exam-item tests + full suite green

## 6. Review round 4 (2026-11-17 flag-row revert)

- [x] 6.1 drawer: 常用项目 option line reverts to BELOW 备注 (closes the form;
      label-left/switch-right kept) - user review: the above-备注 spot read worse;
      column widths stay (单价 110 / rest 100)
- [x] 6.2 spec delta + design D6 + proposal round-4 note synced; exam-item tests +
      full suite green
