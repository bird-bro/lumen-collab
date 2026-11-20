# rep-temp-section-label-web — 节内所见标签改名：影像所见 → 所见

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-20：卡片内的「影像所见」标签要改——**有的节本身就会叫「影像所见」**，
头部节名与正文标签撞车读重。定名「所见」：短、语义准（就是本节的所见文字）、与头部
节名位置/字重双重区分。这是该标签第三次更名（节所见→影像所见→所见），由用户随用随
调的迭代定案。

## What Changes

- 卡片所见 textarea 的 label span / aria-label / placeholder：影像所见 → 所见；
- 页测同步（getByLabelText 6 处）；页顶/卡片 JSDoc 措辞随改；
- spec delta：MODIFIED ×2（Report Template Page + Template Placeholder Convention
  and Live Preview，自主规格机械改名生成，provenance 注释剥离后归档重补）。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx`、
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` ×2 需求块

## Impact

- 无契约变更；所见/影像学表现/影像意见 三层文字命名链现为：
  全局 = 影像学表现 + 影像意见，节内 = 所见。
