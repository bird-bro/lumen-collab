# exam-item-external-code — 检查项目通用对照编码（HIS 等外部系统对照）

**Date**: 2026-11-17 · **Status**: proposed · **Stacks**: lumen-sev + lumen-web

## Problem

`exam_item.code` 经 exam-item-polish D4（2026-11-16）确认仅为院内自有业务编码、无
第三方消费方；与外部系统（HIS 首个消费方，v1 集成路线 `lumen-integration` HL7v2）
的对照没有目录级锚点。用户裁决 2026-11-17 增加通用对照编码。

## Shared decisions (user rulings 2026-11-17)

- **D1 — 通用 `external_code`，非 HIS 专名**: 通用列免二次迁移；EIS 参照系对照
  锚点在订单级（`clinicItemId` 字典同步模式），目录级无先例，本列是 lumen 目录级
  最小落法。否决：`his_code` 专名、映射表（v1 过重）。
- **D2 — 不强制唯一**: 打包计费（一 HIS 收费项 ↔ 多院内检查项）合法；与院内
  `code` 的 partial unique index 有意对比。无索引（keyword LIKE 不吃 btree）。
- **D3 — 不新增分页过滤参数**: keyword 检索后端扩到 `external_code` 即可；对照
  维护走抽屉 + 表格列。
- **D4 — 载荷与回显**: create/update 载荷携带 `externalCode`（可空），detail 回显。

## Change registry

| Change | Stack | Scope | Status |
|:--|:--|:--|:--|
| `exam-item-external-code-sev` | lumen-sev | V36 迁移 + 实体/DTO + keyword LIKE 分支 + exam-setting spec delta | archived 2026-11-18 |
| `exam-item-external-code-web` | lumen-web | 类型 + 抽屉对照编码字段 + 表格列 + 保存载荷 + 页测/api 测 + web-system-console spec delta | archived 2026-11-18 |

## Dependencies / ordering

- **后端先行**: web 构建于真实字段之上（`pnpm dev` 对本地 `lumen-system` 直连验证）。
- 基于 `exam-item-common-filter-drop` 的 post-filter-drop endpoint 文本
  （无 isCommon filter）——归档顺序 common-filter-drop → 本 feature。
- 与 active 的 `exam-item-polish`（同页抽屉/表格修饰）叠加：polish 的 D2 选项行、
  4-up 数字带已定稿，web change 在其之上加字段；归档顺序 polish → external-code。

## Verification gate

后端 verify PASS 后前端构建验证；两栈各自 verify PASS 后批量归档（archive 双 change）。
