# rep-temp-placeholder-preview-web — 占位符约定 + 实时报告预览

**Date**: 2026-11-20 · **Status**: implemented-pending-archive · **Stack**: lumen-web（单栈轻量提案）

## Why

用户三连问（2026-11-20）：报告模板页先优化完；不用富文本能否做段落换行+首行缩进；
模板中 左/右 这类待医生改写的地方能否样式高亮。三问同解——**存储保持纯文本（富文本
否决裁决不破），换行/缩进/高亮全归渲染层**：

- 段落 = textarea 原生 \n；渲染 `text-indent: 2em`（首行缩进是 CSS，永不入库）；
- 占位高亮 = 医生手打的定界符约定 `【左|右】`（| 分候选，整段替换；未配对【原样
  降级）+ 渲染时 tokenizer 包 amber 高亮（warning 语义：待编辑）。

定界语法经 AskUserQuestion 拍板：**【左|右】**（全角——中文 IME 直出、与医学散文中
的英文方括号引用/单位零撞车；用户追问半角 [ ] 后复核维持全角）。同一 tokenizer 将来
直接复用于报告编辑页（未替换占位的终稿守卫留给报告工作流提案）。

## What Changes

- 新 util `src/utils/template-text.tsx`：`renderTemplateText`（tokenizer，
  `.rep-ph` 高亮锚）+ `normalizeLines`（行首空白归一化）+ `PLACEHOLDER_TOKEN`；
- 抽屉底部**实时报告预览**：段落 + 2em 缩进 + amber 占位；分节模式渲染 B 测值表式
  mini 表（名称/单位/测值(—)/参考范围，拍板点 2 的页面级预演）；
- **插入占位**：表现/意见走 Form.Item `extra` 槽（label 保持纯文本——label 内嵌
  button 会被读成第二个关联控件，a11y 名解析双命中，9+3 个页测红即此因，教训入
  代码注释）；节所见走卡片头部行；
- 右表 表现/意见 prose 单元格内联高亮占位段；
- 保存归一化：prose 三处（表现/意见/节所见）+ 简单→分节转换，行首空白剥离。

## Scope

- `lumen-web`: `src/utils/template-text.tsx`(+test)、
  `src/pages/setting/rep-template/index.tsx`、`src/test/rep-temp-page.test.tsx`
- specs delta: `web-system-console` **ADDED** Requirement「Template Placeholder
  Convention and Live Preview」（不动 Report Template Page 原需求，避免持续膨胀）

## Impact

- 无 API 契约变更（存储形态不变）、无库变更、无后端文件。
- 报告工作流提案将来复用本 tokenizer 与预览版式。
