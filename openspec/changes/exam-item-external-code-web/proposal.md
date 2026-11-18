# Proposal — exam-item-external-code-web

## Why

后端配套 change `exam-item-external-code-sev` 给 `exam_item` 增加了通用对照编码
`external_code`（外部系统对照锚点，HIS 首个消费方，可空、不唯一）。检查项目页需要
能查看和维护它——否则对照维护无法进行（编码成对录入、成对核对）。

## What Changes

- `ExamItem` / `CreateExamItemBody` 类型增 `externalCode`（api client 不新增端点）
- 抽屉：项目编码 之后新增 对照编码 字段（可空 Input，maxLength 32）
- 表格：项目编码 之后新增 对照编码 列（空值 —，与 项目编码 渲染一致）
- 保存载荷携带 `externalCode`（create/update 同步）
- 页测 + 断言更新；不动筛选栏

## Capabilities

### Modified Capabilities
- `web-system-console`：Exam Item Page（抽屉字段 + 表格列 + 保存载荷）
