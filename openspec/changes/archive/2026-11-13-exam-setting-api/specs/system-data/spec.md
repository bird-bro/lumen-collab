# Spec Delta: system-data

## ADDED Requirements

### Requirement: Exam Setting Menu Pages

The `system` schema menu tree SHALL gain an L2 directory 「检查设置」 under the 系统管理
root (L1 admin), icon per the console group-icon convention, carrying four C pages in
CONTENT-guarded serial inserts with the setval tail (V14/V27 discipline — ids are data,
perms are the contract):

- 检查项目 — path `exam-item`, component `setting/exam-item/index`, perms
  `setting:item:list`
- 检查部位 — path `exam-body`, component `setting/exam-body/index`, perms
  `setting:body:list`
- 检查方法 — path `exam-method`, component `setting/exam-method/index`, perms
  `setting:method:list`
- 报告模板 — path `rep-template`, component `setting/rep-template/index`, perms
  `setting:temp:list`

#### Scenario: Fresh deployment seeds the exam-setting console group

- **WHEN** the menu migration runs on a fresh database
- **THEN** 系统管理 carries the 检查设置 directory with the four pages at the ruled
  paths and perms strings

#### Scenario: Re-execution is a no-op

- **WHEN** the migration statements re-run on an already-seeded database
- **THEN** no duplicate menu rows appear and the sequence converges past MAX(id)

### Requirement: Exam Setting Button Perms

Each exam-setting C page SHALL carry F button nodes 新建/编辑/删除 with perms
`setting:<resource>:{create,update,delete}` (`item` / `body` / `method` / `temp`),
seeded in the V15 CONTENT-guard discipline keyed by perms, with the admin role
auto-granted.

#### Scenario: Admin role receives the button grants

- **WHEN** the button-node migration section completes
- **THEN** the admin role's menu set includes every exam-setting F node
