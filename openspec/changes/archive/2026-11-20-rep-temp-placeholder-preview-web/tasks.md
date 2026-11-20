# Tasks — rep-temp-placeholder-preview-web

## 1. Tests first (red)

- [x] 1.1 `src/utils/template-text.test.tsx`：tokenizer 5 测（配对/多 token/多候选/
      未配对降级/样式透传）+ normalizeLines 3 测
- [x] 1.2 `rep-temp-page.test.tsx`：预览 2 测（简单段落缩进+高亮 / 分节 B 形态）+
      插入占位接续 + 单元格内联高亮 + 保存归一化
- [x] 1.3 targeted run RED（util 缺模块 + 5 页测红，exit=1，
      /tmp/ph-preview-red.log）

## 2. Implementation (green)

- [x] 2.1 util 落地 + 页面四件套（预览/插入占位/单元格高亮/归一化）；label 嵌
      button 陷阱修正为 extra 槽（D4）
- [x] 2.2 targeted GREEN（50/50，/tmp/ph-preview-green4.log）

## 3. Verification

- [x] 3.1 full web suite green（34 files / 448 tests，exit=0，
      /tmp/ph-preview-full.log）
- [x] 3.2 `pnpm build` exit=0（2.12s，/tmp/ph-preview-build.log）

## 4. Spec sync readiness

- [x] 4.1 delta = ADDED Requirement（4 场景），Report Template Page 原需求零触碰
