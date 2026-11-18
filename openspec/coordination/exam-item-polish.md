# exam-item-polish — 检查项目页：常用项目筛选下线 + 抽屉/表格修饰

**Date**: 2026-11-16 · **Status**: active · **Stacks**: lumen-sev + lumen-web

## Problem

检查项目页 (`/setting/exam-items`) 的常用项目能力只落了一半：

1. 筛选栏的 常用项目 checkbox 从未被真正使用（用户裁决 2026-11-16：删除，前后端清理）。
2. 抽屉表单尾部的 常用项目 switch 孤悬底部（用户裁决：**保留**该能力，只挪位置），
   数字字段（单价/折扣/诊断计费数量、诊断得分/审核得分）用 Input.Group compact
   占位符当标签，尾部 25% 死空，布局不美观（用户裁决：改真实标签并排行）。
3. 表格没有 备注 列（用户裁决：加列，省略号截断）。

## Shared decisions (user rulings 2026-11-16)

- **D1 — 筛选下线，实体能力保留**: `exam_item.is_common` 列、`ExamItem.isCommon`
  实体字段、`ItemCreateRequest`/`ItemUpdateRequest.isCommon`、抽屉 switch 全部保留。
  下线的只有**分页查询过滤**：后端 `ItemPageQuery.isCommon` + `getPage` 过滤分支；
  前端筛选 checkbox + `commonOnly` 状态 + `pageExamItems` 查询参数。
- **D2 — 抽屉尾部布局**（单列平铺内的并排行，不违反 drawer-sections-not-tabs 裁决
  ——该裁决禁的是双列表单网格/分节标题，不是短控件共行）:
  排序 + 常用项目 同一行；单价/折扣/诊断计费数量 真实标签并排一行；
  诊断得分/审核得分 真实标签并排一行；备注 整行 TextArea 收尾。
  常用项目 switch 不再是表单最后一项。
  **2026-11-17 修订（用户复核抽屉）**: 排序+常用项目 同行让 28px 开关悬在半列里
  空区突兀——常用项目 改用 menus 抽屉 `是否显示` 的**选项行**模式（`label htmlFor` +
  `Form.Item noStyle` + `Switch id`，标签与开关同一行，位次：检查方法 之后、数字带
  之前），排序 并入 单价/折扣/诊断计费数量 成 4 列窄带；备注 仍收尾。
  spec delta 场景与页测同步改写。
- **D3 — 表格加 备注 列**: 位于 审核得分 与 创建时间 之间，省略号截断
  （antd `ellipsis`，悬停 title），空值渲染 —。
- **D4 — 项目编码语义**: 用户问询确认——编码当前仅为院内目录自有业务编码
  （模态内未删除行唯一 + 关键词检索），**无第三方对照消费方**；若未来定位为
  HIS/第三方对照编码，需新提案落规格。本次不动 code 的任何行为。
- **D5 — 无库变更**: 不动 V29 的 `is_common` 列与 `exam_item_common_idx` 索引，
  无新迁移。

## Change registry

| Change | Stack | Scope | Status |
|:--|:--|:--|:--|
| `exam-item-common-filter-drop` | lumen-sev | `ItemPageQuery`/`ExamItemServiceImpl`/接口与控制器 javadoc + exam-setting spec delta | archived 2026-11-18 |
| `exam-item-polish-web` | lumen-web | 筛选 checkbox 下线、抽屉尾部重排、表格 备注 列、api client/types、页测 + api 测 + web-system-console spec delta | archived 2026-11-18 (skip-specs: delta 为已同步终态子集) |

Backend first（惯例）；两栈无运行时耦合窗口（前端停发参数、后端停收参数，
单独落地均安全），按批次归档门禁同收。

## Verification gate

两栈各自 verify PASS 后批量归档（archive 双 change）。
