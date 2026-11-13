## Modified Requirements

### Requirement: Exam Setting Menu Pages

The `system` schema menu tree SHALL carry the exam-setting console group as the L1
directory 「设置」 — a sibling of the system root (menu 1, titled 「系统」 per the
same ruling), between it (sort 1) and the workstation W nodes (sort 10+) — with the
four C pages grouped under two L2 icon-bearing directories 「检查配置」 (icon
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
- 报告配置 — icon `temp`, under the L1 设置 row
  - 报告模板 — path `rep-template`, component `setting/rep-template/index`, perms
    `setting:temp:list`

#### Scenario: Fresh deployment seeds the exam-setting console group

- **WHEN** the menu migrations run on a fresh database
- **THEN** the L1 「设置」 directory carries the two L2 groups at their ruled
  icons, the four pages live under their groups at the ruled paths and perms
  strings, and the system root is titled 「系统」

#### Scenario: Migrated database keeps non-admin grants alive

- **WHEN** the V30 promotion and V32 re-grouping run on a database where a
  non-admin role holds the old L2 directory binding or a page binding
- **THEN** every binding's ancestor chain is completed (V30 re-points the old
  directory binding to the L1 row; V32 grants each page holder its new L2 group)
  so the role's page grants survive the TreeUtils orphan rule

#### Scenario: Re-execution is a no-op

- **WHEN** the V28→V32 tail re-runs on an already-migrated database
- **THEN** no duplicate live menu rows appear (the soft-deleted L2 row stays dead,
  one live row per L2 group) and the sequences converge past MAX(id)
