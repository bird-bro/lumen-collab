# Design — rep-template-ux-polish-web

## D1 — 预览弹框化

`previewOpen` state + footer 左侧 预览 按钮（antd 两字按钮自动插空格 → 页测用
`/预 览|预览/` 惯例正则）；Modal width 880 / footer=null（X 关闭）；内容原样搬运
（useWatch + tplSections 仍随草稿实时）；aria-label 报告预览 唯一锚保留。

## D2 — 节名就地编辑

Card title = `<Input variant="borderless" size="small">`（antd 5.29 支持 variant）
+ 灰色 节 N 尾标；fontWeight 600 贴标题观感；body 节名行删除；aria-label 节名
不变（页测锚稳定）；保存空名拦截仍走 saveTemplate 的 name.trim() 校验。

## D3 — icon 化守则

icon-only Button 必带 aria-label（可读性不丢）；折叠/展开共用一个按钮按状态切
Up/Down 字形与 aria-label；删除类 danger + trash；添加测量项 dashed + plus
（width 28 收成方钮）。

## D4 — code 透传

UI 去输入但 state/类型保留 `code: string | null`；新测量行 code: null；存量回填
原样带走；序列化 `code?.trim() || null`——full-replace PUT 不冲存量码。校验删
「测量项项目不能为空」（sev 放行配对）。

## D5 — 改名面

影像所见（label span/aria/占位/页测）；影像意见（Form label/extra aria/预览标题
/右表列题/页测）；spec 双需求 prose 同步（superset 重写）。
