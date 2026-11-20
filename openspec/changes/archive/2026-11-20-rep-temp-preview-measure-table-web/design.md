# Design — rep-temp-preview-measure-table-web

## D1 — 两列布局与线形

measures 两两成组（chunk2），行内两组 [项目|结果|参考范围|单位]；中线 = 前半最后
一列（单位）的 borderRight（表头同步），表格外框画在 table 元素上；其余纵线
不渲染（borderCollapse 下单元格只带 borderBottom）。奇数尾行右半空白占位。

## D2 — 单位列回归的缘由

中途按「单位进表头括号」实现并写了测试，用户复核后推翻：英文单位 g/l、mmol/L
复杂化后表头括号装不下——单位独立列、列序固定、表头居中。spec 未曾归档中间态，
delta 一步到位描述终态（无痕）。

## D3 — 项目改名面

编辑器输入（aria/占位）名称→项目；校验消息 测量项名称不能为空 → 测量项项目不能
为空；页测 card 作用域同步（drawer 级模板名称不动）。
