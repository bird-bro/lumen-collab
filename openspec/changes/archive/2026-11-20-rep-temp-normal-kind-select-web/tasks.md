# Tasks — rep-temp-normal-kind-select-web

## 1. Tests first (red)

- [x] 1.1 `rep-temp-page.test.tsx`: `pickNormalKind` helper + 3 处 radio 点击改
      下拉交互（文本×2、区间×1）
- [x] 1.2 targeted vitest confirms RED（exit=1, 2 failed | 34 passed，
      log /tmp/kind-select-red.log）

## 2. Page (green)

- [x] 2.1 `rep-template/index.tsx`: 测量行 Segmented → Select（width 76，
      aria-label 正常范围类型），value/onChange/options 原样；JSDoc 同步
- [x] 2.2 targeted vitest green（36/36，/tmp/kind-select-green.log）

## 3. Verification

- [x] 3.1 full web suite green（33 files / 434 tests，exit=0，
      /tmp/kind-select-full.log）
- [x] 3.2 `pnpm build` exit=0（/tmp/kind-select-build.log）

## 4. Spec sync readiness

- [x] 4.1 delta superset check：MODIFIED 块含全部 12 既有场景（含 tree-dirs-only
      新场景），prose 与 Backfill 场景随形态改写
