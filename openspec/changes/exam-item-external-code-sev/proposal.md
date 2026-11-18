# Proposal — exam-item-external-code-sev

## Why

检查项目缺少与外部系统对照的编码字段：`exam_item.code` 经 exam-item-polish 协调文档
D4（2026-11-16）确认仅为院内自有业务编码、无第三方消费方。v1 集成路线
（`lumen-integration`，HL7v2 对 HIS）落地前需要目录级对照锚点——用户裁决
2026-11-17：增加**通用对照编码 `external_code`**（HIS 是第一个消费方），可空、
不强制唯一。

## What Changes

- V37 迁移：`exam_item` 增列 `external_code varchar(32) NULL`（additive，不动已应用迁移）
- 实体 `ExamItem` + DTO（`ItemCreateRequest` / `ItemUpdateRequest` / `ItemVO`）增 `externalCode`
- 分页 keyword 检索范围扩到 `external_code`（`ExamItemServiceImpl` 的 LIKE 分支）
- 无索引、无唯一约束（用户裁决：打包计费「一 HIS 收费项 ↔ 多院内检查项」是合法映射）
- 不新增分页过滤参数（keyword 覆盖即可）；抽屉/表格 UI 由配套 web change 落地

## Capabilities

### Modified Capabilities
- `exam-setting`：Exam Item Columns（`external_code` 列 + 不唯一语义）、
  Exam Item Endpoints（keyword 检索范围 + 请求/回显携带 externalCode）
