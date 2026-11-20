# Spec Delta: web-system-console

## Purpose

The preview measure table lands its final shape (user rulings 2026-11-20):
the 检验报告 two-up layout (two 项目/结果/参考范围/单位 groups per row,
visible grid, centered headers), the editor row field renames 名称 → 项目,
and the 所见 textarea grows to fourteen rows.

## MODIFIED Requirements

### Requirement: Report Template Page

The console SHALL render the 报告模板 page (component `setting/rep-template/index`)
with a 公共/个人 Segmented scope switch (radio options) above the left folder/tree —
检查类型 select + 新建目录 in the tool row; the left tree renders DIRECTORIES ONLY
(2026-11-20 ruling: template leaves never render on the left — templates are
managed in the right table), directory nodes carry 新建子目录/编辑目录/删除节点.
The right pane keeps the paged template table (模板名称 / 影像学表现 / 影像意见)
under the selected node with the name-filter tool row; its 操作 column SHALL ride
the shared console-table chrome: icon-only row verbs 编辑 / 删除 gated by the
scope-keyed family (删除 carrying the delete glyph), with the refresh affordance
pinned to the right edge of the 操作 header re-running the current page query.
Template authoring is DUAL-FORM (2026-11-18 ruling): the 添加模板 / 编辑 surface
carries a 简单模板/分节模板 mode — 简单模式 keeps the 影像学表现/影像意见
textareas reading the prose columns; 分节模式 opens the WIDE drawer of section
cards (each card: the 节名 edited IN PLACE inside the card head — a borderless
input carrying the example placeholder 节名（如：心腔、肝脏）with the gray
positional 节 N tag beside it; 测量项 row editor 项目/单位/正常范围 with add/remove (user ruling 2026-11-20 follow-up: the row field reads 项目, ex-名称)
— the 项目 code input is RETIRED, the code rides a nullable passthrough so legacy
rows keep theirs across the full-replace PUT; 所见 textarea; the verbs 折叠/
展开, 删除节, 删除测量项, 添加测量项 rendered ICON-ONLY with their aria-labels)
with the global 影像意见 at the bottom writing `content.impression`. The template
drawer width is FIXED at 960px (2026-11-19 ruling) — switching the mode never
resizes it. The 测量项 正常范围 editor is the v2 UNION editor
(rep-temp-normal-range): a per-row Select (区间/文本; user ruling 2026-11-20 —
ex-Segmented) — 区间 renders min/max InputNumbers (decimals allowed; a blank end
is a single-sided open interval) and 文本 renders a ≤64-char input; serialization
ALWAYS writes content version 2 — 区间 with both ends empty serializes
`normal: null`, 区间 with at least one end serializes `{kind:'range',min,max}`,
文本 non-empty serializes `{kind:'text',text}`; the editor backfills the select
and inputs from the row's stored `kind` (no v1 branch exists after the server
migration); submitting min > max is blocked client-side with a field-naming
message and NO request is issued. Converting 简单 → 分节 maps 表现 → first
section's 所见 and 意见 → `content.impression`; converting 分节 → 简单 is
REFUSED with a warning (sections would be dropped). For structured rows the
right table's 表现 cell renders the 「N 节 · M 项」 summary and the 意见 cell
renders `content.impression`. 添加模板 warns 请先选择目录 when no directory is
selected. Private-scope writes use the real owner binding; the page never offers
another user's private rows. Scope-keyed perm families gate the page
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

#### ~~Scenario: Card title echoes the typed 节名~~

*Deprecated 2026-11-20 — superseded by the 「节名 edits in place inside the card
head」 scenario below (rep-template-ux-polish-web: the title-echo rendering and the
standalone body row are retired — the 节名 edits directly inside the card head).
Body preserved for history.*

- **WHEN** the user types a 节名 into a section card (and while the card is empty)
- **THEN** the card title shows the typed name live — the gray positional 节 N
  tag stays beside it for reference; an empty card shows 节名未填 until filled
  (the 节名-required save guard is unchanged)

#### Scenario: 节名 edits in place inside the card head

- **WHEN** the user types a 节名 into a section card's head input (and while the
  card is fresh)
- **THEN** the borderless head input carries the example placeholder and the
  gray 节 N tag stays beside it; no standalone 节名 row renders in the card
  body (the 节名-required save guard is unchanged)

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

### Requirement: Template Placeholder Convention and Live Preview

The 报告模板 editing surface SHALL keep storage PLAIN TEXT and own all
report-shape treatment at the render layer. The 【左|右】 delimited token
(doctor-typed; | separates quick-pick candidates; the whole bracket is
replaced with the actual finding at report time; an unpaired 【 degrades to
literal text) SHALL highlight with the warning-class amber tint (`.rep-ph`)
in the 报告预览 modal and in the right table's prose 影像学表现/影像意见
cells. The 报告预览 SHALL be an ON-DEMAND modal — the template drawer's
footer carries a 预览 button summoning it (the always-on bottom block is
retired, ux-polish 2026-11-20); the modal renders the current draft — one
paragraph per line with a 2em first-line indent (the print shape) and, in
分节模式, the B-form mini measure table per section — the 检验报告 TWO-UP
layout: each table row carries TWO 项目 / 结果 / 参考范围 / 单位 groups
(in that exact column order) separated by the ONE center vertical rule
(rep-temp-preview-measure-table-web 2026-11-20: the table draws its outer
frame + row lines + the center rule in the standard border token —
表格线要明显 — while every OTHER vertical column line is suppressed; the
HEADERS render CENTERED; the unit stays a SEPARATE column since complex
English units like g/l cannot fold into a header parenthesis; an odd measure
count leaves the second half blank; 结果 stays always — on the template —
the template carries the reference, the report carries the actual; 单侧开区间
`19 ~` / `~ 40`, both ends empty renders —). The 报告预览 modal body renders at a generous default
height (70vh, internal scroll). The 插入占位 affordances SHALL be retired outright
(rep-template-placeholder-insert-retire-web 2026-11-20: the card's and both
prose fields' buttons are gone — the token types by hand). The 所见
textarea renders FOURTEEN rows (user ruling 2026-11-20 follow-up, 3→6→10→14 across the day). Save SHALL normalize leading whitespace per
line on 影像学表现/影像意见/所见 (and the 简单→分节 conversion) — the
indent belongs to the renderer, never the stored text.


#### Scenario: Live preview renders the print shape

- **WHEN** the drawer's 表现 carries two lines including a 【左|右】 token and
  the user clicks the footer 预览 button
- **THEN** the 报告预览 modal opens showing the token highlighted amber and each
  line as a paragraph with a 2em first-line indent
- **AND** in 分节模式 each section renders its 节名, the mini measure table and
  its 所见 paragraphs (reference range `19 ~ 40` for a two-sided band)

#### Scenario: Placeholder tokens highlight in the right table

- **WHEN** the template table renders a prose row whose 表现/意见 carries a
  token
- **THEN** the token renders highlighted inline (`.rep-ph`) within the cell

#### ~~Scenario: 插入占位 splices at the caret~~

*Deprecated 2026-11-20 — superseded by the retirement above
(rep-template-placeholder-insert-retire-web: all 插入占位 affordances are gone —
the 【左|右】 token types by hand). Body preserved for history.*

- **WHEN** the user clicks 插入占位 on an empty 表现 textarea and keeps typing
- **THEN** the token lands first and the typing continues right after it (the
  caret re-anchors after the token on the next frame)

#### Scenario: Save normalizes leading whitespace per line

- **WHEN** a save carries a line starting with full-width spaces
- **THEN** the stored prose drops them (the 2em indent is render-layer CSS)
