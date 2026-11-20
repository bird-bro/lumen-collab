# rep-temp-normal-kind-select-web — 测量项正常范围类型选择器 Segmented → Select

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-20：分节模板测量行的 区间/文本 切换器改成下拉框——「只是觉得
下拉更顺手」。此前 AI 顾问意见为保留 Segmented（两选项单点击、模式可见），用户
复核后仍以下拉为终裁（使用者手感优先）。该形态被 rep-temp-normal-range 规格钉死
为「per-row Segmented」，故走形态变更提案同步规格。

## What Changes

报告模板页（`/setting/rep-templates`）分节模板测量行，仅控件形态换：

- 正常范围类型 Segmented（size small）→ Select（width 76，aria-label
  「正常范围类型」，options 区间/文本）；
- 联动行为零变更：区间 = min/max InputNumber（空端=单侧开区间），文本 = ≤64
  字符输入，空形态序列化 null，min>max 客户端拦截——全沿 v2 union 语义；
- 页顶 JSDoc 同步；公共/个人 scope 的 Segmented 不动（那是它的本命场景）。

**spec delta**：`web-system-console` § Report Template Page（MODIFIED，全 12 场景
超集）——prose 「per-row Segmented」改「per-row Select」，Backfill 场景措辞随改。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx` +
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` § Report Template Page

## Impact

- 无 API 契约变更（content jsonb 序列化不变）、无库变更、无后端文件、
  无 coordination doc（单栈轻量）。
- 归档无顺序依赖。
