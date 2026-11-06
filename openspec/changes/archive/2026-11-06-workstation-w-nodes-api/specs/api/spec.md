# api Delta: workstation-w-nodes-api

## REMOVED Requirements

### Requirement: Workstation Context Endpoints

**Reason**: the per-user workstation grant (`user_workstation` table +
`PUT /users/{id}/workstations` + the login/me `workstations`/`primaryWorkstation`
payload fields) bypassed the role system — the user ruled (2026-11-06) that menu
permissions flow ONLY through roles: the workstation is a type-W menu node whose
access is governed by role_menu grants.

**Migration**: V22 drops `user_workstation`, deletes the `system:user:assign-ws` F
node + grants, drops the `menu_info.workstation` column, and seeds the three type-W
station nodes (放射工作站/超声工作站/内镜工作站). Workstation context flows through the
menu tree (W nodes) — see the REPLACED requirement in the auth-rbac delta.

## ADDED Requirements

### Requirement: Workstation Menu Nodes

The menu tree SHALL carry workstation nodes as `type = W` entries at L1 level (放射
工作站/超声工作站/内镜工作站 seeded by V22 with the admin auto-grant; new stations
creatable through the menu management page's type option). The login/me menu tree
carries the W nodes with their subtrees — the frontend derives the workstation list
from the granted tree (a user whose roles hold no W node sees the console only).
The menu create/update validation SHALL accept the `W` type.

#### Scenario: W nodes are seeded and granted

- **WHEN** the migrations run
- **THEN** three type-W nodes (放射工作站/超声工作站/内镜工作站) exist at L1
- **AND** the admin role's grant contains them

#### Scenario: Grant flows through roles

- **WHEN** a role is granted the 放射工作站 W node and a user binds that role
- **THEN** the user's next login/me menu tree contains the W node with its subtree

#### Scenario: Wrong machinery is gone

- **WHEN** a client calls `PUT /api/v1/system/users/{id}/workstations`
- **THEN** the endpoint does not exist (404)
- **AND** `user_workstation` no longer exists as a table
