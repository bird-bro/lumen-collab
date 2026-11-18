# Design — exam-item-external-code-web

## Decisions

- **D1 — 字段落位**: 抽屉里 对照编码 紧跟 项目编码 —— 两个编码是同一实体的两个
  编码空间（院内自有 / 外部对照），成对相邻便于录入与核对；表格列同样紧跟
  项目编码。
- **D2 — 渲染样式与 项目编码 一致**: 列空值渲染 —；对照编码不参与 ellipsis 特判
  （短编码串，项目编码 同款处理）；不做着色/弱化差异，保持列语义对称。
- **D3 — 保存载荷**: create/update 载荷都带 `externalCode: values.externalCode ??
  null`（对齐现有 `code ?? null` 写法）；编辑回显走 `getExamItem` detail 的
  `externalCode`。
- **D4 — 不动筛选栏**: 不为对照编码加独立过滤参数（后端 D6 同款裁决）；keyword
  后端已覆盖对照编码检索，前端关键词占位文案不必变（名称/编码 已含语义）。
- **D5 — 类型与 api client**: `ExamItem` + `CreateExamItemBody` 各加一行
  `externalCode: string | null`；`UpdateExamItemBody` 是 alias 自动继承；
  不新增端点函数（后端契约无新路由）。

## Risks / Notes

- 依赖 `exam-item-external-code-sev` 先落地（真实字段存在才能构建）——后端先行
  惯例；`pnpm dev` 对本地 `lumen-system` 直连即可验证。
- `exam-item-polish-web`（active）正在改同一个抽屉/表格：本 change 在其之上叠加
  （常用项目 选项行、4-up 数字带已定稿），归档顺序 polish → external-code，
  coordination 文档记录链。
