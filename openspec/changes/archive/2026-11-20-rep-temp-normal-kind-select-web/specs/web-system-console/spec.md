# Spec Delta: web-system-console

## Purpose

Swap the measure row's 正常范围 kind control from the pinned per-row Segmented to
a Select dropdown (user ruling 2026-11-20, rep-temp-normal-kind-select-web): the
two-option Segmented read as a data field and the user finds the dropdown
handier. Editor semantics (v2 union, null degrade, backfill) unchanged.

## MODIFIED Requirements

### Requirement: Report Template Page

The console SHALL render the 报告模板 page (component `setting/rep-template/index`)
with a 公共/个人 Segmented scope switch (radio options) above the left folder/tree —
检查类型 select + 新建目录 in the tool row; the left tree renders DIRECTORIES ONLY
(2026-11-20 ruling: template leaves never render on the left — templates are
managed in the right table), directory nodes carry 新建子目录/编辑目录/删除节点.
The right pane keeps the paged template table (模板名称 / 影像学表现 / 影像学意见)
under the selected node with the name-filter tool row; its 操作 column SHALL ride
the shared console-table chrome: icon-only row verbs 编辑 / 删除 gated by the
scope-keyed family (删除 carrying the delete glyph), with the refresh affordance
pinned to the right edge of the 操作 header re-running the current page query.
Template authoring is DUAL-FORM (2026-11-18 ruling): the 添加模板 / 编辑 surface
carries a 简单模板/分节模板 mode — 简单模式 keeps the 影像学表现/影像学意见
textareas reading the prose columns; 分节模式 opens the WIDE drawer of section
cards (each card: 节名 input, 测量项 row editor 项目/单位/正常范围 with add/remove,
节所见 textarea, collapse and delete) with the global 影像学意见 at the bottom
writing `content.impression`. The template drawer width is FIXED at 960px
(2026-11-19 ruling) — switching the mode never resizes it. The 测量项 正常范围
editor is the v2 UNION editor (rep-temp-normal-range): a per-row Select (区间/文本;
user ruling 2026-11-20 — ex-Segmented) — 区间 renders min/max InputNumbers
(decimals allowed; a blank end is a single-sided open interval) and 文本 renders
a ≤64-char input; serialization ALWAYS writes content version 2 — 区间 with both
ends empty serializes `normal: null`, 区间 with at least one end serializes
`{kind:'range',min,max}`, 文本 non-empty serializes `{kind:'text',text}`; the
editor backfills the select and inputs from the row's stored `kind` (no v1
branch exists after the server migration); submitting min > max is blocked
client-side with a field-naming message and NO request is issued. Converting
简单 → 分节 maps 表现 → first section's 节所见 and 意见 → `content.impression`;
converting 分节 → 简单 is REFUSED with a warning (sections would be dropped). For
structured rows the right table's 表现 cell renders the 「N 节 · M 项」 summary and
the 意见 cell renders `content.impression`. 添加模板 warns 请先选择目录 when no
directory is selected. Private-scope writes use the real owner binding; the page
never offers another user's private rows. Scope-keyed perm families gate the page
(2026-11-19 ruling): the Segmented 公共 option renders ONLY for users holding
`setting:temp:public:list` — without it the page is a personal-template library
defaulting to the 个人 scope; the default scope is 公共 only when
`setting:temp:public:list` is held; and EVERY template action
(新建目录/新建子目录/编辑目录/删除节点/添加模板/表格编辑与删除) keys its
`hasPerm` check on the CURRENT scope — 公共 scope consults the
`setting:temp:public:*` family, 个人 scope the base `setting:temp:*` family.

#### Scenario: Tab toggle switches scope without cross-leak

- **WHEN** the user switches from 个人 to 公共 scope
- **THEN** tree and table reload with type=1 data, and switching back reloads the
  user's own type=2 tree

#### Scenario: Directory node creates a child directory

- **WHEN** the user uses a directory node's 新建子目录 action
- **THEN** the directory drawer opens pre-bound to that node in the 上级 select
  and saving shows the new directory in the left tree

#### Scenario: Tree renders directories only

- **WHEN** the tree renders for the selected 检查类型 and scope
- **THEN** only directory nodes (flag=0) appear — template leaves never render
  on the left (templates are managed in the right table's 操作 column)
- **AND** the auto-anchored selection is the first directory, so 添加模板 is
  usable without a manual pick

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

#### Scenario: 区间 normal serializes the v2 range variant

- **WHEN** the user switches a measure row to 区间, enters min 19 / max 40 (and in
  another row leaves max blank with min 3)
- **THEN** the saved content carries `{kind:'range',min:19,max:40}` and
  `{kind:'range',min:3,max:null}` at version 2

#### Scenario: 文本 normal serializes the v2 text variant

- **WHEN** the user switches a measure row to 文本 and enters 阴性
- **THEN** the saved content carries `{kind:'text',text:'阴性'}`

#### Scenario: Empty normal serializes null

- **WHEN** a measure row's 区间 inputs are both left empty
- **THEN** the saved content carries `normal: null` for that measure

#### Scenario: Inverted range is blocked client-side

- **WHEN** the user enters min 40 / max 19 and submits
- **THEN** the submit is blocked with a message naming 正常范围 and no create/update
  request is issued

#### Scenario: Backfill reads the stored kind

- **WHEN** the user edits a structured row whose measure carries a text variant
- **THEN** the row editor's kind select shows 文本 with the stored text input; a
  range variant row shows 区间 with the stored ends

#### Scenario: 操作 header carries the refresh affordance

- **WHEN** the right template table renders
- **THEN** the 操作 header shows the refresh glyph at its right edge and 删除
  carries the delete glyph (编辑 the edit glyph)
- **AND** clicking the refresh re-runs the current page query (current scope,
  directory, name filter, page)
