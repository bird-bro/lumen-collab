# Spec Delta

## ADDED Requirements

### Requirement: Patient Number Rule Menu Pages

The `system` schema menu tree SHALL carry a new L2 icon-bearing directory 「患者配置」
(type M, NULL perms, sort 3) under the L1 「设置」 row, containing the C page 号码规则
(path `number-rule`, component `patient/number-rule/index`, perms `patient:rule:list`),
seeded in the CONTENT-guarded serial-insert discipline (ids are data, perms are the
contract), with the admin role auto-granted the page binding. The L2 group's icon SHALL
be a glyph from the console icon family (exact glyph pinned in the change design), and
non-admin grants SHALL complete the ancestor chain so page bindings survive the
TreeUtils orphan rule.

#### Scenario: Fresh deployment seeds the patient config group

- **WHEN** the menu migrations run on a fresh database
- **THEN** the L1 「设置」 row carries the L2 「患者配置」 group at its ruled icon and
  sort, and the 号码规则 page lives under it at the ruled path and perms string

#### Scenario: Non-admin grants keep their chain

- **WHEN** a non-admin role holds the new page binding
- **THEN** the role's menu set includes the 「患者配置」 group ancestor so the page
  grant resolves in the menu tree

#### Scenario: Re-execution is a no-op

- **WHEN** the menu seed migration re-runs on an already-migrated database
- **THEN** no duplicate live menu rows appear and the sequence converges past MAX(id)

### Requirement: Patient Number Rule Button Perms

The 号码规则 C page SHALL carry F button nodes 新建/编辑/删除 with perms
`patient:rule:{create,update,delete}`, seeded in the CONTENT-guard discipline keyed by
perms, with the admin role auto-granted.

#### Scenario: Admin role receives the button grants

- **WHEN** the button-node migration section completes
- **THEN** the admin role's menu set includes every patient-number-rule F node
