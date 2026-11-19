# web-system-console Delta

## MODIFIED Requirements

### Requirement: Report Template Page

The console SHALL render the 报告模板 page (component `setting/rep-template/index`)
with a 公共/个人 Segmented scope switch (radio options) above the left folder/tree —
检查类型 select + 新建目录 in the tool row; tree directory nodes carry
新建子目录/编辑目录/删除节点, template leaves carry 删除 only. The right pane keeps
the paged template table (模板名称 / 影像学表现 / 影像学意见) under the selected
node with the name-filter tool row. Template authoring is DUAL-FORM (2026-11-18
ruling): the 添加模板 / 编辑 surface carries a 简单模板/分节模板 mode — 简单模式
keeps the 640 drawer with 影像学表现/影像学意见 textareas reading the prose
columns; 分节模式 opens a WIDE 960 drawer of section cards (each card: 节名 input,
测量项 row editor 项目/单位/正常范围 with add/remove, 节所见 textarea, collapse
and delete) with the global 影像学意见 at the bottom writing `content.impression`.
Converting 简单 → 分节 maps 表现 → first section's 节所见 and 意见 →
`content.impression`; converting 分节 → 简单 is REFUSED with a warning (sections
would be dropped). For structured rows the right table's 表现 cell renders the
「N 节 · M 项」 summary and the 意见 cell renders `content.impression`. 添加模板
warns 请先选择目录 when the selected node is not a directory. Private-scope writes
use the real owner binding; the page never offers another user's private rows.
Scope-keyed perm families gate the page (2026-11-19 ruling): the Segmented 公共
option renders ONLY for users holding `setting:temp:public:list` — without it the
page is a personal-template library defaulting to the 个人 scope; the default scope
is 公共 only when `setting:temp:public:list` is held; and EVERY template action
(新建目录/新建子目录/编辑目录/删除节点/添加模板/表格编辑与删除) keys its
`hasPerm` check on the CURRENT scope — 公共 scope consults the
`setting:temp:public:*` family, 个人 scope the base `setting:temp:*` family.

<!-- synced from: rep-temp-scope-perms 2026-11-19 -->

#### Scenario: Tab toggle switches scope without cross-leak

- **WHEN** the user switches from 个人 to 公共 scope
- **THEN** tree and table reload with type=1 data, and switching back reloads the
  user's own type=2 tree

#### Scenario: Directory node creates a child directory

- **WHEN** the user uses a directory node's 新建子目录 action
- **THEN** the directory drawer opens pre-bound to that node in the 上级 select
  and saving shows the new directory in the left tree

#### Scenario: Section mode edits sections and measures

- **WHEN** the user adds a section, fills its 节名, adds measure rows
  (项目/单位/正常范围) and 节所见 text, and saves
- **THEN** the create/update request carries the content object matching the
  edited sections and the row appears in the right table with the summary cell

#### Scenario: Mode switch converts prose to sections

- **WHEN** a 简单模板 form with filled 表现/意见 is switched to 分节模式
- **THEN** the first section's 节所见 carries the former 表现 text and
  影像学意见 stays as the global conclusion

#### Scenario: Structured to prose conversion refused

- **WHEN** a structured template's edit form is switched to 简单模式
- **THEN** the page warns that sections would be dropped and keeps 分节模式

#### Scenario: Structured row renders summary cells

- **WHEN** the right table lists a structured template row (2 sections, 5 measures)
- **THEN** the 表现 cell shows 「2 节 · 5 项」 and the 意见 cell shows
  `content.impression`

#### Scenario: 公共 option hidden without the public list perm

- **WHEN** a user without `setting:temp:public:list` opens the page
- **THEN** the Segmented shows only the 个人 option, the initial scope is 个人
  (no 公共 tree/table request is issued), and the page reads as a personal
  template library

#### Scenario: 公共 scope actions consult the public family

- **WHEN** a user holding `setting:temp:public:list` but neither
  `setting:temp:public:create` nor `setting:temp:public:update` switches to the
  公共 scope
- **THEN** the tree/table read fine while 新建目录/新建子目录/添加模板/编辑
  affordances are hidden — and the same affordances reappear on the 个人 scope
  when the base family is held

#### Scenario: 个人 scope actions keep the base family

- **WHEN** a user holding the base family (no public family) works on the 个人
  scope
- **THEN** 新建目录/新建子目录/添加模板/编辑/删除 all consult
  `setting:temp:*` and behave exactly as before this change
