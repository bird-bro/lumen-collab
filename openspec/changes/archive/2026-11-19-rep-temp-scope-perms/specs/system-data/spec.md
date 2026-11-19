# system-data Delta

## ADDED Requirements

### Requirement: Report Template Scope Button Perms

The 报告模板 page (type `C`, perms `setting:temp:list`) SHALL gain four F button
nodes under it in the V28 CONTENT-guarded serial-insert discipline (ids are data,
perms are the contract, `NOT EXISTS`-guarded, no fixed ids): 公共模板可见
(`setting:temp:public:list`, sort 4), 新建公共模板
(`setting:temp:public:create`, sort 5), 编辑公共模板
(`setting:temp:public:update`, sort 6), 删除公共模板
(`setting:temp:public:delete`, sort 7). The seeded administrator role (role 1) SHALL
be auto-granted the four nodes via guarded `role_menu` inserts (V28 shape), and the
`menu_info` id sequence SHALL advance past the seeded rows (V14 setval discipline).
Business roles (e.g. 科室主任) bind the nodes at deployment time via role
authorization — no business-role seed is shipped.

#### Scenario: Admin gains the public family without manual grant

- **WHEN** the migration has run and the seed admin (role 1) requests its
  permission set
- **THEN** all four `setting:temp:public:*` perm strings are present and bound to
  role 1, idempotent across repeated migrations
