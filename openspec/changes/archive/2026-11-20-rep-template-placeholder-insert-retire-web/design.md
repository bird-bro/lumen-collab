# Design — rep-template-placeholder-insert-retire-web

## D1 — 全退场而非再删一个

三次修剪（卡片→表现→意见）指向同一终态：占位工作流 = 手打 token（预览弹框的提示
行「amber 高亮为待编辑占位（【左|右】）」承担教学）。剩余两钮一并退场，死代码
（PlaceholderExtra/insertPlaceholderToken/refs/imports）清零。

## D2 — spec 场景弃用

「插入占位 splices at the caret」划线保留 body（house 惯例：CLI 按 scenario 名
计数，弃用块进 delta 随归档同步为主规格终态）。
