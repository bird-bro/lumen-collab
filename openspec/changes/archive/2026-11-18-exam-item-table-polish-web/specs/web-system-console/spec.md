# Spec Delta: web-system-console

## Purpose

Polish the three exam-setting lists: merge the in-hospital code into the 项目
cell, shorten the exam-item headers, center the count/score columns, retire the
创建时间 column everywhere (the catalogs are initialization-imported data), move
the row verbs onto the shared console-table chrome, and re-order the exam-item
drawer per the 2026-11-17 review.

## MODIFIED Requirements

### Requirement: Exam Item Page

The console SHALL render the 检查项目 page (menu component `setting/exam-item/index`)
against the real `/api/v1/setting/exam-items` endpoints: a filter bar (检查类型 select
from dict 1021, 部位分组 select from `exam-body-groups/list`, keyword search over
名称/编码 — NO 常用项目 toggle), a table (项目 — the in-hospital 编码 rides the SAME
cell STACKED on two lines: the name truncates on the first line and the code renders
below it in the tertiary small font — / 对照码 / 检查部位 / 检查方法 /
单价 / 折扣 / 计费数量 / 诊断得分 / 审核得分 / 备注 — 计费数量, 诊断得分 and 审核得分
rendered centered; 备注 ellipsized with hover title, — for null; NO 创建时间 column),
row affordances gated by `setting:item:*` riding the shared console-table chrome
(icon-only 编辑 / 删除 — 删除 carries the delete glyph; the 操作 header carries the
refresh affordance at its right edge re-running the current page query), and a Drawer
form: 检查类型 readout, REQUIRED 编码, 名称 (60-character cap), 部位分组 → 检查部位
cascade (group select drives the body tree select), 检查方法 multi-select, 对照码
BELOW 检查方法, 排序 on its own row, 单价（元） / 折扣 / 计费数量 as real-labeled
fields in one 3-up row, 诊断得分 / 审核得分 likewise (tooltip 留空 = 不计), 备注
TextArea, and the 常用项目 option line CLOSING the form below 备注 (label pinned
left, switch pinned right).

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

#### Scenario: 项目 cell carries the code on a second line

- **WHEN** the table renders an item whose in-hospital code is set (or null)
- **THEN** the 项目 column stacks two lines: the name truncates on the first line and
  the code renders below it in the tertiary small font (name alone when the code is
  null); no standalone 项目编码 column renders

#### Scenario: Centered count and score columns, renamed headers

- **WHEN** the table renders
- **THEN** the headers read 项目 / 对照码 / 检查部位 / 检查方法 / 单价（元） / 折扣 /
  计费数量 / 诊断得分 / 审核得分 / 备注 / 操作
- **AND** 计费数量, 诊断得分 and 审核得分 headers and cells render centered
- **AND** no 创建时间 column renders

#### Scenario: Table renders the remark column

- **WHEN** the table renders an item whose remark is set (or null)
- **THEN** the 备注 column shows the remark ellipsized with a hover title (— for
  null), between 审核得分 and 操作

#### Scenario: 操作 header carries the refresh affordance

- **WHEN** the table renders
- **THEN** the 操作 header shows the refresh glyph at its right edge
- **AND** clicking it re-runs the current page query
- **AND** 删除 carries the delete glyph (编辑 the edit glyph)

#### Scenario: Drawer order and the closing 常用项目 line

- **WHEN** the drawer form renders
- **THEN** 编码 (required — an empty save blocks with 编码不能为空) and 名称
  (maxLength 60) lead the form, 检查方法 is followed by 对照码, 排序 renders on its
  own row before the 3-up 单价（元） / 折扣 / 计费数量 band, and 备注 is the last
  TextArea
- **AND** 常用项目 closes the form below 备注 as an option line with the label
  pinned left and the switch pinned right

### Requirement: Exam Body Page with In-Page Group Management

The console SHALL render the 检查部位 page (component `setting/exam-body/index`): a
left column managing 部位分组 for the selected 检查类型 (list + 新建/编辑/删除 gated
by `setting:body:*`), and a right table of bodies rendered as **tree rows** from the
server's `up_id` forest (部位 / 编码 / 备注 — 编码 rendered centered; 备注
prose-aligned and ellipsized; NO 上级部位 column — the hierarchy expresses the
parent, NO 创建时间 column). Every level SHALL start expanded, users may collapse;
the tree re-opens on refetch. The 部位 keyword filter SHALL prune the forest to hits
plus their ancestor chains (a no-hit keyword empties the tree). The right table's
操作 column SHALL ride the shared console-table chrome: icon-only row verbs 编辑 /
删除 gated by `setting:body:*` (删除 carrying the delete glyph), with the refresh
affordance pinned to the right edge of the 操作 header re-fetching the selected
group's bodies. The Drawer form keeps (检查类型 readout, 上级部位 optional flat select
within the group, 部位名称, 部位编码, 排序). Group deletion that the backend refuses
(bodies present) surfaces the backend's blocking message.

#### Scenario: Left column manages groups without leaving the page

- **WHEN** the user creates a group in the left column
- **THEN** the group list and the page's group filter refresh in place

#### Scenario: Blocked group delete shows the reason

- **WHEN** a group delete is refused by the backend because bodies exist
- **THEN** the page shows the backend's refusal message and keeps the group

#### Scenario: 操作 header carries the refresh affordance

- **WHEN** the right bodies table renders
- **THEN** the 操作 header shows the refresh glyph at its right edge
- **AND** clicking it re-fetches the selected group's bodies from the real backend

#### Scenario: Centered columns

- **WHEN** the right bodies table renders
- **THEN** the 编码 headers and cells render centered
- **AND** the 部位 and 备注 columns keep their default alignment
- **AND** the 排序 and 创建时间 columns do not render

#### Scenario: Forest renders as an expanded tree

- **WHEN** the right bodies table renders a group whose bodies carry `up_id`
  hierarchy
- **THEN** parent rows render with expand controls and every level is expanded by
  default
- **AND** the 上级部位 column does not render

#### Scenario: Keyword filter prunes to hits plus ancestors

- **WHEN** the user searches a 部位 keyword that matches a child body
- **THEN** the matching row and its ancestor chain render
- **AND** branches without a match drop entirely; a no-hit keyword empties the table

### Requirement: Exam Method Page

The console SHALL render the 检查方法 page (component `setting/exam-method/index`):
检查类型 filter, 名称/编码 keyword search, table (方法名称 / 方法编码 / 检查类型 —
NO 创建时间 column) whose row affordances gated by `setting:method:*` ride the shared
console-table chrome (icon-only 编辑 / 删除 — 删除 carries the delete glyph; the
操作 header carries the refresh affordance at its right edge re-running the current
page query), Drawer form (检查类型 readout, 方法名称, 方法编码, 排序).

#### Scenario: Method CRUD round-trips the real API

- **WHEN** the user creates, edits, then deletes a method
- **THEN** each action hits `/api/v1/setting/exam-methods` and the table reflects the
  server state after each action

#### Scenario: No 创建时间 column

- **WHEN** the table renders
- **THEN** the columns are 方法名称 / 方法编码 / 检查类型 / 操作 and no 创建时间
  header renders

#### Scenario: 操作 header carries the refresh affordance

- **WHEN** the table renders
- **THEN** the 操作 header shows the refresh glyph at its right edge and 删除
  carries the delete glyph
- **AND** clicking the refresh re-runs the current page query
