# Tasks — rep-temp-ops-chrome-web

## 1. Tests first (red)

- [x] 1.1 `rep-temp-page.test.tsx`: new "rides the shell ops chrome" test —
      refresh button inside the 操作 header th, click re-runs `pageRepTemps`,
      row 删除 carries `.anticon-delete` (+ 编辑 `.anticon-edit`)
- [x] 1.2 targeted vitest run confirms RED (no 刷新列表 button against the
      hand-rolled ops column — exit=1, 1 failed | 35 passed,
      log `/tmp/rep-ops-chrome-red.log`)

## 2. Page (green)

- [x] 2.1 `rep-template/index.tsx`: drop the hand-rolled `key:'ops'` column;
      migrate the verbs into `ConsoleTable` `actions={{ width: 130, render }}`
      verbatim (tempPerm gating, Popconfirm, removeNode adapter); 删除 gains
      `icon="remove"`; `onRefresh` re-runs the current page query; doc-comment
      touch-up
- [x] 2.2 targeted vitest green (36/36, exit=0,
      log `/tmp/rep-ops-chrome-green.log`)

## 3. Verification

- [x] 3.1 full web suite green (33 files / 434 tests, exit=0,
      log `/tmp/rep-ops-chrome-full.log`)
- [x] 3.2 `pnpm build` exit=0 (built in 2.02s,
      log `/tmp/rep-ops-chrome-build.log`)

## 4. Spec sync readiness

- [x] 4.1 delta superset check: MODIFIED block carries all 10 existing scenarios
      verbatim (Tab toggle / Directory node / 公共 hidden / 公共 family /
      个人 family / 区间 / 文本 / Empty / Inverted / Backfill) + the new
      ops-chrome scenario
