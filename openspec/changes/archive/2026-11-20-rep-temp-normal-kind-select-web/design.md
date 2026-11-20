# Design — rep-temp-normal-kind-select-web

## D1 — 纯控件形态换，语义零变更

`TemplateSectionCard` 测量行的 `<Segmented size="small" .../>`（区间/文本 两选项）
换成 `<Select style={{width:76}} aria-label="正常范围类型" .../>`，value/onChange/
options 原样搬运（`measure.normal?.kind ?? 'range'` 兜底、text↔range 互斥构造）。
Segmented import 保留——公共/个人 scope 切换仍是 Segmented。

## D2 — 测试交互改写

三处 radio 点击 → `pickNormalKind(user, scope, label)` helper（开 combobox + 点
option，`.at(-1)` 惯例同 pickTemplateType；option title 区间/文本 与抽屉 类型
select 的 简单模板/分节模板 不撞）。Backfill 断言不动（文本输入框存在性即 kind
证据）。

## D3 — 反面意见记录（不改裁决）

顾问意见：两选项场景 Segmented 单点击 + 模式可见性占优。用户终裁下拉（手感）。
若未来形态扩到 4+ 项（公式/引用区间），本 Select 自然容纳，无需再换控件。
