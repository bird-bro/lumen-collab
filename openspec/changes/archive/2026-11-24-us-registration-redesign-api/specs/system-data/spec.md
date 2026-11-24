<!-- us-registration-redesign-api delta. 拍板 07-A + 拍板 06-A menu placement (预约设置 under 检查配置).
     NOTE: the coordination note's 检查设置→检查配置 rename ALREADY shipped with menu-settings-root
     (2026-11-13) — this migration is additive-only (no rename). MODIFIED blocks are supersets. -->

## MODIFIED Requirements

### Requirement: Exam Setting Menu Pages

The `system` schema menu tree SHALL carry the exam-setting console group as the L1
directory 「设置」 — a sibling of the system root (menu 1, titled 「系统」 per the
same ruling), between it (sort 1) and the workstation W nodes (sort 10+) — with the
C pages grouped under two L2 icon-bearing directories 「检查配置」 (icon
`exam`, sort 1) and 「报告配置」 (icon `temp`, sort 2), all seeded in
the V28 CONTENT-guarded serial-insert discipline (ids are data, perms are the
contract; the L2 groups are type M with NULL perms). The L1 directory's rail icon
is `tune` (V31 shipped the AntD placeholder `sliders`; V34 swaps it to the RCP
reference glyph — tasks 1.3/1.6). The V28-seeded L2 「检查设置」 under the system root
is soft-deleted with its bindings carried over to the L1 row (V30), and the pages
re-group under their L2 directories (V32) whose icons take the RCP reference
glyphs (V33, task 1.5 — the rcp-test dict_menu rows for the same console domain):

- 检查配置 — icon `exam`, under the L1 设置 row
  - 检查项目 — path `exam-item`, component `setting/exam-item/index`, perms
    `setting:item:list`
  - 检查部位 — path `exam-body`, component `setting/exam-body/index`, perms
    `setting:body:list`
  - 检查方法 — path `exam-method`, component `setting/exam-method/index`, perms
    `setting:method:list`
  - 预约设置 — path `exam-slot`, component `setting/exam-slot/index`, perms
    `setting:slot:list` (us-registration-redesign-api: the per-modality
    pre-registration capacity policy, 拍板 06-A — additive leaf, no directory rename)
- 报告配置 — icon `temp`, under the L1 设置 row
  - 报告模板 — path `rep-template`, component `setting/rep-template/index`, perms
    `setting:temp:list`

#### Scenario: Fresh deployment seeds the exam-setting console group

- **WHEN** the menu migrations run on a fresh database
- **THEN** the L1 「设置」 directory carries the two L2 groups at their ruled
  icons, the five pages live under their groups at the ruled paths and perms
  strings (预约设置 under 检查配置), and the system root is titled 「系统」

#### Scenario: Migrated database keeps non-admin grants alive

- **WHEN** the V30 promotion and V32 re-grouping run on a database where a
  non-admin role holds the old L2 directory binding or a page binding
- **THEN** every binding's ancestor chain is completed (V30 re-points the old
  directory binding to the L1 row; V32 grants each page holder its new L2 group)
  so the role's page grants survive the TreeUtils orphan rule

#### Scenario: Re-execution is a no-op

- **WHEN** the V28→V34 tail re-runs on an already-migrated database
- **THEN** no duplicate live menu rows appear (the soft-deleted L2 row stays dead,
  one live row per L2 group) and the sequences converge past MAX(id)

#### Scenario: The 预约设置 leaf seeds additively

- **WHEN** the slot-config menu migration runs (fresh or already-migrated database)
- **THEN** the 检查配置 group gains the 预约设置 C page (path `exam-slot`, perms
  `setting:slot:list`) with the admin role auto-granted, the four existing leaves
  stay untouched, and the re-run adds no duplicate live rows (CONTENT-guarded)

### Requirement: Exam Setting Button Perms

Each exam-setting C page SHALL carry F button nodes 新建/编辑/删除 with perms
`setting:<resource>:{create,update,delete}` (`item` / `body` / `method` / `temp` /
`slot`), seeded in the V15 CONTENT-guard discipline keyed by perms, with the admin role
auto-granted.

#### Scenario: Admin role receives the button grants

- **WHEN** the button-node migration section completes
- **THEN** the admin role's menu set includes every exam-setting F node (the `slot`
  family 新建/编辑/删除 arriving with the 预约设置 page)

## ADDED Requirements

### Requirement: Search Filter Table

The `system` schema SHALL carry the generic saved-search table `search_filter` (拍板 07-A):
`uid` (the owning user), `page_key` (the workbench page discriminator), `name`, `query`
jsonb (the per-page filter shape, passed through verbatim), the default flag, and the house
audit columns — seeded by the system migration (V50, together with the 预约设置 menu leaf).
ONE default per (uid, page_key) among live rows. The table is workbench-agnostic — every
list page may persist filters against it; column settings deliberately stay localStorage
(frontend-only, the EIS useHeaderColumn pattern).

#### Scenario: The table lands with the house shape

- **WHEN** the system migration runs
- **THEN** `search_filter` exists in the system schema with the uid/page_key/name/query
  jsonb/default flag columns and the EntityBase audit column set

#### Scenario: One default per user and page

- **WHEN** a second row is flagged default for the same (uid, page_key)
- **THEN** the partial unique index refuses the second live default (one default per
  uid + page_key among live rows; setting a new default goes through the endpoint's
  full-replace semantics)
