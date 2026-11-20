# rep-temp-preview-measure-table-web — 预览测值表定稿：检验单两列布局 + 项目改名 + 弹框加高

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户裁决 2026-11-20 连续迭代预览测值表的最终形态：单位独立成列（复杂英文单位
g/l 等塞不进表头括号，中途的「单位进表头」方案被用户自行推翻）；列序
**项目 / 结果 / 参考范围 / 单位**；测量列名 名称 → **项目**（编辑器输入同步）；
**检验报告式两列布局**（一行两组、中间真表格纵线、其余纵线隐去、外侧框+行线+
中线的线形、表头居中）；报告预览弹框默认高度加大（70vh）；所见 textarea 14 行。

## What Changes

- `PreviewMeasureTable`：8 列两半布局（项目/结果/参考范围/单位 ×2）、外框+行线+
  中线（colorBorder）、其余纵线不渲染、表头居中、奇数尾行右半留白、结果列恒 —；
- 编辑器测量行输入 名称 → 项目（aria/占位），校验消息 测量项名称不能为空 →
  测量项项目不能为空；
- 预览弹框 body 70vh 内滚；
- 页测：预览测重写（列头 ×2/结果 —/单位列/两半填充），41/41 → 全量 447；
- spec delta：MODIFIED ×2（Report Template Page 的 row-editor 措辞 +
  Template Placeholder Convention 的测值表句与 FOURTEEN rows，均超集）。

## Scope

- `lumen-web`: `src/pages/setting/rep-template/index.tsx`、
  `src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` ×2 需求块

## Impact

- 无契约变更；规格里中途出现过的「单位进表头」方案未归档即被用户推翻，无痕。
