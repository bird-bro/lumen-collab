# Spec Delta: web-system-console

## Purpose

Polish the 检查项目 page: retire the unused 常用项目 filter toggle, re-band the
drawer tail with real labels, and surface the item remark in the table.

## MODIFIED Requirements

### Requirement: Exam Item Page

The console SHALL render the 检查项目 page (menu component `setting/exam-item/index`)
against the real `/api/v1/setting/exam-items` endpoints: a filter bar (检查类型 select
from dict 1021, 部位分组 select from `exam-body-groups/list`, keyword search over
名称/编码 — NO 常用项目 toggle), a table (项目名称 / 项目编码 / 检查部位 / 检查方法 /
单价 / 折扣 / 诊断计费数量 / 诊断得分 / 审核得分 / 备注 / 创建时间 — 备注 ellipsized
with hover title, — for null), row 编辑/删除 affordances gated by `setting:item:*`,
and a Drawer form with 检查类型 readout, 项目编码, 项目名称, 部位分组 → 检查部位
cascade (group select drives the body tree select), 检查方法 multi-select, the
常用项目 option line (label and switch on ONE line — menus 是否显示 pattern —
between 检查方法 and the numeric bands), a banded tail — 排序 / 单价（元） / 折扣 /
诊断计费数量 as real-labeled fields in one 4-up row, 诊断得分 / 审核得分 likewise
(tooltip 留空 = 不计), 备注 TextArea closing the form.

#### Scenario: Group selection cascades the body options

- **WHEN** the drawer's 部位分组 select changes
- **THEN** the 检查部位 tree select reloads with that group's bodies and the stale
  body value clears

#### Scenario: Create posts to the real backend

- **WHEN** the user saves a valid new item
- **THEN** the page POSTs to `/api/v1/setting/exam-items` and refreshes the table from
  the response of a fresh page query — no mock data anywhere in the flow

#### Scenario: Filter bar omits the 常用项目 toggle

- **WHEN** the filter bar renders
- **THEN** only the 检查类型 / 部位分组 selects, the keyword input and the query
  button appear — no 常用项目 checkbox
- **AND** the page query carries no `isCommon` parameter

#### Scenario: Table renders the remark column

- **WHEN** the table renders an item whose remark is set (or null)
- **THEN** the 备注 column shows the remark ellipsized with a hover title (— for
  null), between 审核得分 and 创建时间

#### Scenario: Drawer tail lays out the option line and banded numbers

- **WHEN** the drawer form renders
- **THEN** 常用项目 renders as an option line (label and switch on one line)
  between 检查方法 and the numeric bands, 排序 joins 单价（元）/ 折扣 /
  诊断计费数量 as real-labeled fields in one 4-up row, and 诊断得分 / 审核得分
  render likewise (tooltip 留空 = 不计)
- **AND** 备注 is the closing full-width TextArea
