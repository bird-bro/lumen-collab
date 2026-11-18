# Design — exam-item-table-polish-web

## D1 — 项目 + 编码 合列单元格（分上下两行）

`exam-item` 列表的 项目名称 / 项目编码 两列合并为一列 项目。用户复核（2026-11-17
第二轮）：**分上下两行**，参照 deps 科室 / roles 创建人 / users 用户列的统一惯例：

- `title: '项目'`，`dataIndex: 'exam'`；**不用列级 ellipsis**（两行布局与
  cell 级 ellipsis 冲突），截断下沉到 span；
- 第一行：名称，`overflow hidden + text-overflow ellipsis + nowrap`；
- 第二行（`row.code` 存在时）：编码，`fontSizeSM + lineHeight 18px +
  colorTextTertiary + tabular-nums`——与全站次行语义完全一致；
- 编码为 null 只渲染名称（无空次行）。

抽屉标签随列表头缩短（见 D6）。

## D2 — 表头改名与对齐

- `对照编码 → 对照码`（仅列表头）；`诊断计费数量 → 计费数量`（仅列表头）。
- 居中集合 = 裁决枚举的三列：**计费数量 / 诊断得分 / 审核得分**（`align: 'center'`）。
  单价 / 折扣 未被点名，保持右对齐（金额/比率列右对齐惯例不受影响）。
- 列宽（round 3 裁决）：单价（元）**110**，折扣 / 计费数量 / 诊断得分 / 审核得分
  **统一 100**（全站短数值列 70–120 惯例带）；其余列弹性分配。

## D3 — 创建时间列下线（三页）

- `exam-item` / `exam-body` / `exam-method` 列表移除 创建时间 列；页测断言
  `columnheader 创建时间` 不存在。
- 查询与 VO 不动（proposal「后端 SQL 分析结论」）：`createTime` 仍是 API 载荷的
  审计面字段，页测夹具保留该字段（夹具镜像契约，不镜像 UI）。

## D4 — spec delta 层叠

Exam Item Page 的 delta 全文 = `exam-item-polish-web` 终态文本（无常用项目筛选、
备注列、抽屉尾带）+ 两轮评审终态。归档顺序：external-code-web → polish-web → 本
change（proposal Impact 节）。Exam Body / Exam Method delta 描述工作区终态
（exam-body 的 备注 列换入 排序 位次的未提交裁决一并由本 change 的文本收编）。

## D5 — 操作列壳层 chrome（评审第二轮 2026-11-17）

exam-item 列表的手写操作列迁入 `ConsoleTable` 的 `actions` 壳：**删除** 换 delete
字形（原先骑 ConsoleAction 的 edit 默认——评审指出的图标错误），**操作** 表头右缘
挂刷新 affordance（重跑当前页查询，`onRefresh`）。同一壳层约定补齐 **检查方法** 页
（此前无壳层，同缺陷）——exam-body 已在 exam-body-table-polish 落地，不动。

## D6 — 抽屉重排（评审第二轮 2026-11-17，取代 polish-web D2 的终态）

- **编码必填**：`rules required`，空存拦截「编码不能为空」（服务端仍允空——纯 UI
  收紧，契约不变）。
- **标签缩短**（与列表头一致）：编码 / 名称 / 对照码 / 计费数量。
- **名称上限 50 → 60**：DB `exam varchar(200)`、DTO 无长度校验，纯前端收紧，无迁移。
- **对照码移到 检查方法 之下**：不再与院内编码结对入口（external-code-web 的并排
  位置退役）。
- **排序独占一行**（全宽 InputNumber，组抽屉先例）；数字带收窄为 单价/折扣/计费数量
  3-up；得分带不变。
- **常用项目收尾于 备注 之下**（round 3 曾试放备注之上，round 4 用户复核：上方
  不好看，还原收尾）：label 居左、控件居右（`space-between`）。
- 终序（round 4 定稿）：检查类型回显 → 编码 → 名称 → 部位分组 → 检查部位 → 检查方法 →
  对照码 → 排序 → 3-up 带 → 得分带 → 备注 → 常用项目。
