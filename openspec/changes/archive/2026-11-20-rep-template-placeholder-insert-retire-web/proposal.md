# rep-template-placeholder-insert-retire-web — 插入占位全退场 + 影像所见加高

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-20 连续修剪：节所见（→影像所见）行的按钮先删，本笔把 表现/意见
两个 extra 槽按钮一并退场——手打 【左|右】 + 预览弹框（内含语法提示行）已完整覆盖
占位工作流，按钮是冗余 affordance。顺带影像所见 textarea rows 3→6（用户：高度
需要大一些）。

## What Changes

- 表现/意见 Form.Item 的 `extra` 槽（PlaceholderExtra）删除；组件与
  `insertPlaceholderToken`、两个 caret ref、相关 import 一并清退（死代码零残留）；
- 影像所见 textarea rows 3→6；
- 页测删插入占位用例（41/41）；spec delta：MODIFIED Template Placeholder
  Convention and Live Preview（prose 去 插入占位 句 + 场景划线弃用，超集）。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx`、
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` § Template Placeholder Convention and Live Preview

## Impact

- 无契约变更；util 的 renderTemplateText/normalizeLines 仍在用（预览/单元格/归一化）；
  PLACEHOLDER_TOKEN 导出保留（约定文档位）。
