# Tasks — rep-temp-tree-dirs-only-web

## 1. Tests first (red)

- [x] 1.1 `rep-temp-page.test.tsx`: rewrite the tree test → dirs-only
      assertions (leaf absent from the tree container, verb counts 3/3/3,
      right-table row intact)
- [x] 1.2 targeted vitest run confirms RED (leaf still rendered in the tree —
      exit=1, 1 failed | 35 passed, log `/tmp/rep-tree-red.log`)

## 2. Page (green)

- [x] 2.1 `rep-template/index.tsx`: `pruneToDirectories` helper applied in
      `loadTree` (state = directories only; re-anchor on pruned rows);
      renderTreeNode drops the flag guards (icon constant ▰, comment
      rewritten); 新建目录/添加模板 onClicks simplified; openTemplateAdd
      comment + page/test doc comments updated
- [x] 2.2 targeted vitest green (36/36, exit=0, log `/tmp/rep-tree-green.log`)

## 3. Verification

- [x] 3.1 full web suite green (33 files / 434 tests, exit=0,
      log `/tmp/rep-tree-full.log`)
- [x] 3.2 `pnpm build` exit=0 (built in 2.04s, log `/tmp/rep-tree-build.log`)

## 4. Spec sync readiness

- [x] 4.1 delta superset check: MODIFIED block carries all 11 current scenarios
      (incl. rep-temp-ops-chrome-web's ops-chrome one) verbatim + the new
      「Tree renders directories only」 scenario
