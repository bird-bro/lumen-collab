## Modified Requirements

### Requirement: Exam Body Page with In-Page Group Management

The console SHALL render the 检查部位 page (component `setting/exam-body/index`): a
left column managing 部位分组 for the selected 检查类型 (list + 新建/编辑/删除 gated
by `setting:body:*`), and a right table of bodies rendered as **tree rows** from the
server's `up_id` forest (部位 / 编码 / 排序 / 创建时间 — 编码, 排序 and 创建时间
rendered centered; no 上级部位 column — the hierarchy expresses the parent). Every
level SHALL start expanded, users may collapse; the tree re-opens on refetch. The
部位 keyword filter SHALL prune the forest to hits plus their ancestor chains
(a no-hit keyword empties the tree). The right table's 操作 column SHALL ride the
shared console-table chrome: icon-only row verbs 编辑 / 删除 gated by
`setting:body:*` (删除 carrying the delete glyph), with the refresh affordance
pinned to the right edge of the 操作 header re-fetching the selected group's
bodies. The Drawer form keeps (检查类型 readout, 上级部位 optional flat select
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
- **THEN** the 编码, 排序 and 创建时间 headers and their cells render centered
- **AND** the 部位 column keeps its default alignment

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
