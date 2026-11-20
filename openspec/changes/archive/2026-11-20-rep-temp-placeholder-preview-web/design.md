# Design — rep-temp-placeholder-preview-web

## D1 — 约定即契约：定界符是唯一的「富」

存储零结构变化；`【[^】]*】` 是全部语法。未配对【字面渲染永不炸（tokenizer 用
exec 循环而非 split 捕获组）。高亮样式由调用方传 token 色（util 不 import theme），
`.rep-ph` class 是测试与读屏锚。

## D2 — 缩进归渲染层（归一化）

`normalizeLines` 剥行首半角空格/tab/全角空格（仅行首，内部空白不动）；保存三处 +
转换处调用；预览渲染归一化后的文本——所见即所印。空行渲染为短占位段（minHeight）。

## D3 — 插入占位的光标缝补

antd `TextAreaRef.resizableTextArea.textArea` 读 selectionStart/End，splice 后
rAF 恢复焦点+光标到 token 尾——医生接续打字落在占位符之后。caretless（无 ref/无
选区）退化为 append。

## D4 — label 嵌 button 的 a11y 陷阱（本轮教训）

按钮放 Form.Item label 内 → HTMLLabelElement.control 指向 button，label 出现两个
关联控件，`getByLabelText('影像学表现')` 双命中（9+3 页测红）。修正：表单字段的
插入占位走 `extra` 槽（label 纯文本、必填星号不动）；卡片节所见用头部行（其标签本
就是普通 span，无 label 元素）。测试断言「插入后接续打字落在 token 尾」锁光标恢复。

## D5 — 预览的 B 形态

分节模式预览 = 拍板点 2（B 测值表式起步）的页面级预演：节名 + 名称/单位/测值(—)/
参考范围 mini 表（formatRange：单侧开区间 `19 ~`/`~ 40`，双空 = —）+ 节所见段落。
简单模式 = 表现段落 + 意见块。测值列恒 —（模板存参考，报告存实测）。
