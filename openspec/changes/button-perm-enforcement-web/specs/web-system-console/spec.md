# web-system-console Delta: button-perm-enforcement-web

## ADDED Requirements

### Requirement: Button-Level Affordance Gating

Console management pages SHALL gate each action affordance by its own button-level
permission string (the coordination inventory): the action renders only when the
session permission set contains that string. The action column itself remains gated by
the page permission. Affordances sharing one endpoint share one perm string (新建机构
with 新建根机构; 新建根节点 with 新增下级). Disabled-until-selected affordances keep
their existing disabled logic and additionally require the perm. Read-only pages (log
queries) carry no gated affordances.

#### Scenario: Ungranted action stays hidden

- **WHEN** a user's session holds the 角色管理 page perm but not `system:role:delete`
- **THEN** the role list renders with 新建角色/编辑角色/菜单授权 (as granted) but no
  删除角色 affordance

#### Scenario: Granted action appears

- **WHEN** the session additionally contains `system:role:delete`
- **THEN** the 删除角色 affordance renders for role rows

#### Scenario: Page perm still gates the column

- **WHEN** a session holds button perms but somehow lacks the page perm
- **THEN** no action column renders (the page surface itself is route-gated by the
  page perm; the server remains authoritative either way)
