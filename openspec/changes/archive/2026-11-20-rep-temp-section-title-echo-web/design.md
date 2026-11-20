# Design — rep-temp-section-title-echo-web

## D1 — 标题回显 = 展示层变化，语义零变更

`TemplateSectionCard`（模块级组件）新增 `theme.useToken()` 取 token
（colorTextTertiary / fontSizeSM），title 从纯字符串 `节 ${index+1}` 换成
ReactNode：`section.name.trim() || 节名未填(灰)` + `节 N(灰, 小号)`。
- 编号尾标保留：口头沟通「节 2 那张卡」仍可用；保存校验仍按 name 必填。
- aria-label `节卡片 ${index+1}` 不动——页测 group 锚点与读屏名稳定。
- placeholder 加示例：占位文案即教学（EIS 惯例：placeholder 示例优于 helper text）。

## D2 — 测试

新测（RED→GREEN）：未填卡断言 节名未填 + 节 1 + 新 placeholder；输入「心腔」后
标题回显、节 1 尾标仍在、节名未填消失。`getByText('心腔')` 只命中标题文本节点
（input value 非文本节点，无双 match）。

## D3 — 拍板点其余三项的去向

B 测值表式起步 / 节术语保留 / 排序先不加 → 会话记忆
`rep-template-report-render-decision`；B 排版由未来报告工作流提案兑现，届时引用
原型 `docs/prototypes/rep-template-report-preview.html` 的变体 B 版式。
