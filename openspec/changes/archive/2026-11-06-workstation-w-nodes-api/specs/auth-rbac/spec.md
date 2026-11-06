# auth-rbac Delta: workstation-w-nodes-api

## REMOVED Requirements

### Requirement: Workstation Authorization

**Reason**: superseded by the user ruling 2026-11-06 — the direct user grant
(`user_workstation` table, per-user assignment endpoint, assign-ws button perm,
bootstrap fallback machinery) bypassed the role system. The workstation is a type-W
menu node; its authorization is the EXISTING role_menu flow (roles granted the W node
through the 菜单授权 tree; users bind roles). V22 tears the wrong machinery down.

**Migration**: V22 drops `user_workstation`, deletes the assign-ws F node + grants,
drops the `menu_info.workstation` column, seeds the three type-W station nodes. The
REPLACED requirement below re-establishes the workstation authorization in role terms.

## ADDED Requirements

### Requirement: Workstation Nodes in the Menu Tree

The menu tree SHALL carry workstation nodes as `type = W` entries at L1 level, granted
through the existing role_menu mechanism (roles own menu permissions; users bind
roles):

- V22 SHALL seed 放射工作站/超声工作站/内镜工作站 W nodes (admin auto-grant, content-
  guard discipline) and SHALL drop the wrong machinery (`user_workstation` table, the
  per-user assignment endpoint's button node, the `menu_info.workstation` column).
- The menu management page SHALL offer the 工作站 type (the station set grows only
  through the menu tree, NOT through a per-user grant surface).
- A role granted a W node SHALL pass that workstation's subtree to every bound user's
  login/me menu tree; a user whose roles hold no W node SHALL see the console only.

#### Scenario: W nodes seeded and role-granted

- **WHEN** the migrations run and the admin opens 角色管理's 菜单授权 tree
- **THEN** the three W nodes render (with their type tag) among the grantable nodes

#### Scenario: Station access is role-driven

- **WHEN** a role is granted the 放射工作站 W node and a user binds that role
- **THEN** the user's next login/me menu tree contains the W node and its subtree
- **AND** a user bound only to a role WITHOUT the W node sees no workstation in the tree

#### Scenario: Wrong machinery removed

- **WHEN** the migrations run
- **THEN** `user_workstation` does not exist, the workstation column is dropped, and
  the `system:user:assign-ws` node is gone from the tree and grants
