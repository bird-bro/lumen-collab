# Tasks — rep-temp-section-title-echo-web

## 1. Tests first (red)

- [x] 1.1 `rep-temp-page.test.tsx`: new card-title-echo test（未填：节名未填 +
      节 1 + 新 placeholder；输入后回显 + 尾标保留 + 未填消失）
- [x] 1.2 targeted vitest confirms RED（exit=1, 1 failed | 36 passed，
      /tmp/title-echo-red.log）

## 2. Page (green)

- [x] 2.1 `rep-template/index.tsx`: TemplateSectionCard title 回显 + 节名未填
      灰位（token 取色）+ 节 N 尾标；节名 placeholder 加示例；aria-label 不动
- [x] 2.2 targeted vitest green（37/37，/tmp/title-echo-green.log）

## 3. Verification

- [x] 3.1 full web suite green（33 files / 435 tests，exit=0，
      /tmp/title-echo-full.log）
- [x] 3.2 `pnpm build` exit=0（/tmp/title-echo-build.log）

## 4. Spec sync readiness

- [x] 4.1 delta superset check：MODIFIED 块含全部 12 既有场景 + 新场景
      Card title echoes the typed 节名（插在 个人 family 与 区间 之间）
