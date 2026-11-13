## Modified Requirements

### Requirement: Exam Setting Menu Pages

The `system` schema menu tree SHALL carry the exam-setting console group as the L1
directory 「设置」 — a sibling of the system root (menu 1, titled 「系统」 per the
same ruling), between it (sort 1) and the workstation W nodes (sort 10+) — with the
four C pages under it in the V28 CONTENT-guarded serial-insert discipline (ids are
data, perms are the contract). The V28-seeded L2 「检查设置」 under the system root is
soft-deleted with its bindings carried over to the L1 row (V30):

- 检查项目 — path `exam-item`, component `setting/exam-item/index`, perms
  `setting:item:list`
- 检查部位 — path `exam-body`, component `setting/exam-body/index`, perms
  `setting:body:list`
- 检查方法 — path `exam-method`, component `setting/exam-method/index`, perms
  `setting:method:list`
- 报告模板 — path `rep-template`, component `setting/rep-template/index`, perms
  `setting:temp:list`

#### Scenario: Fresh deployment seeds the exam-setting console group

- **WHEN** the menu migrations run on a fresh database
- **THEN** the L1 「设置」 directory carries the four pages at the ruled paths and
  perms strings, and the system root is titled 「系统」

#### Scenario: Migrated database keeps non-admin grants alive

- **WHEN** the V30 promotion runs on a database where a non-admin role holds the
  old L2 directory binding
- **THEN** the binding is re-pointed to the new L1 「设置」 row so the role's page
  grants keep their ancestor chain

#### Scenario: Re-execution is a no-op

- **WHEN** the V28→V30 tail re-runs on an already-migrated database
- **THEN** no duplicate live menu rows appear (the soft-deleted L2 row stays dead)
  and the sequences converge past MAX(id)
