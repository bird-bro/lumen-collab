# auth-rbac Delta: menu-management-api

## ADDED Requirements

### Requirement: Menu Node Lifecycle Administration

Menu nodes (directories, menus, buttons) SHALL be administrable through the menu
management family while preserving the grant model:

- Creating a menu node SHALL also insert the node into the admin role's `role_menu`
  grant, so the super administrator immediately sees and can re-grant every new node
  (lumen keeps explicit `role_menu` grants - no wildcard authority). Grant rows SHALL be
  created only on CREATE; updating a node SHALL NOT alter any role's grants.
- Updating a node SHALL NOT be able to reparent it into itself or its own descendant
  subtree, and SHALL NOT allow any node to hang under a button (`type = F`) node.
- Deleting a menu node SHALL be refused while the node still has live children.
  Deleting a childless node SHALL soft-delete the node and SHALL soft-delete its
  `role_menu` binding rows, so no role keeps granting a permission whose owning node is
  gone.

#### Scenario: New node is immediately admin-visible

- **WHEN** an administrator creates any menu node
- **THEN** the admin role's grant contains the new node without a separate assignment step

#### Scenario: Update never touches grants

- **WHEN** an administrator renames, re-icons, or moves an existing node
- **THEN** the `role_menu` rows for that node are unchanged

#### Scenario: Deleted button stops granting its permission

- **WHEN** a button node (`type = F`) with a `perms` string is deleted
- **THEN** roles that had the button granted no longer receive the `perms` string in
  their login/me permission set
