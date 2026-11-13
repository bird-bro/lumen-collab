# Spec Delta: web-system-console

## ADDED Requirements

### Requirement: Exam Item Page

The console SHALL render the 检查项目 page (menu component `setting/exam-item/index`)
against the real `/api/v1/setting/exam-items` endpoints: a filter bar (检查类型 select
from dict 1021, 部位分组 select from `exam-body-groups/list`, 常用项目 toggle, keyword
search over 名称/编码), a table (项目名称 / 项目编码 / 检查部位 / 检查方法 / 单价 /
折扣 / 诊断计费数量 / 诊断得分 / 审核得分 / 创建时间), row 编辑/删除 affordances gated
by `setting:item:*`, and a Drawer form with 检查类型 readout, 项目编码, 项目名称,
部位分组 → 检查部位 cascade (group select drives the body tree select), 检查方法
multi-select, 排序, 单价, 折扣, 诊断计费数量, 诊断得分, 审核得分, 常用项目 switch.

#### Scenario: Group selection cascades the body options

- **WHEN** the drawer's 部位分组 select changes
- **THEN** the 检查部位 tree select reloads with that group's bodies and the stale
  body value clears

#### Scenario: Create posts to the real backend

- **WHEN** the user saves a valid new item
- **THEN** the page POSTs to `/api/v1/setting/exam-items` and refreshes the table from
  the response of a fresh page query — no mock data anywhere in the flow

### Requirement: Exam Body Page with In-Page Group Management

The console SHALL render the 检查部位 page (component `setting/exam-body/index`): a
left column managing 部位分组 for the selected 检查类型 (list + 新建/编辑/删除 gated
by `setting:body:*`), and a right table of bodies (部位名称 / 部位编码 / 上级部位 /
排序 / 创建时间) filtered by group, with a Drawer form (检查类型 readout, 上级部位
optional tree select within the group, 部位名称, 部位编码, 排序). Group deletion that
the backend refuses (bodies present) surfaces the backend's blocking message.

#### Scenario: Left column manages groups without leaving the page

- **WHEN** the user creates a group in the left column
- **THEN** the group list and the page's group filter refresh in place

#### Scenario: Blocked group delete shows the reason

- **WHEN** a group delete is refused by the backend because bodies exist
- **THEN** the page shows the backend's refusal message and keeps the group

### Requirement: Exam Method Page

The console SHALL render the 检查方法 page (component `setting/exam-method/index`):
检查类型 filter, 名称/编码 keyword search, table (方法名称 / 方法编码 / 检查类型 /
创建时间), Drawer form (检查类型 readout, 方法名称, 方法编码, 排序), affordances gated
by `setting:method:*`.

#### Scenario: Method CRUD round-trips the real API

- **WHEN** the user creates, edits, then deletes a method
- **THEN** each action hits `/api/v1/setting/exam-methods` and the table reflects the
  server state after each action

### Requirement: Report Template Page

The console SHALL render the 报告模板 page (component `setting/rep-template/index`)
with a 公共/私有 tab toggle switching the scope `type` (1/2): a left pane with 检查类
型 select (dict 1021) and the folder/template tree (node-level 新建子级/编辑/删除),
and a right pane with the template table (模板名称 / 影像学表现 / 影像学意见) paged
under the selected node plus name search and a Drawer form (名称, 上级 folder tree
select, 目录/模板 flag — flag=1 reveals 影像学表现/影像学意见 textareas). Private-tab
writes use the real owner binding; the page never offers another user's private rows.

#### Scenario: Tab toggle switches scope without cross-leak

- **WHEN** the user switches from 私有 to 公共 tab
- **THEN** tree and table reload with type=1 data, and switching back reloads the
  user's own type=2 tree

#### Scenario: Directory node creates a child template

- **WHEN** the user uses a tree node's 新建 action with flag=模板
- **THEN** the drawer opens pre-bound to that node as 上级 and saving shows the new
  template in the right table under that node
