# web-system-console Delta: workstation-context-web

## ADDED Requirements

### Requirement: Workstation Assignment UI

用户管理 SHALL provide a per-user workstation assignment affordance: the administrator
checks the stations to grant and marks the primary one, and saving full-replaces the
user's grant through `PUT /api/v1/system/users/{id}/workstations`. The affordance SHALL
be gated client-side by `system:user:assign-ws`; backend rejections (e.g. two
defaults) SHALL surface on the page. Existing users with no workstation rows SHALL
display the bootstrap state (`admin` only, primary).

#### Scenario: Assignment round-trip

- **WHEN** an administrator grants 放射工作站 with primary to a user and saves
- **THEN** the documented endpoint is called with the posted set and reopening the
  affordance shows exactly that set with the primary marked

#### Scenario: Two defaults are rejected inline

- **WHEN** the posted set carries two primary stations
- **THEN** the backend's rejection message surfaces and the affordance stays open

#### Scenario: Gated by the assignment perm

- **WHEN** a session holds 用户管理's page perm but not `system:user:assign-ws`
- **THEN** the assignment affordance does not render

<!-- REMOVED (workstation-context-web, revised 2026-11-06): the per-user workstation
     assignment drawer — the user ruled that workstation access flows through ROLES
     (role_menu grants of the W nodes), never directly to users. The workstation
     grant happens in 角色管理's menu assignment tree; the menu management type option
     gains 工作站 per the W-node model. -->

### Requirement: Menu Management Workstation Grouping

The menu management page SHALL render the tree grouped by workstation — a group header
row per station (系统管理工作站 / 放射科工作站 / 超声科工作站 / 内镜科工作站) above its
L1 menus — and the L1 create/edit form SHALL carry a 所属工作站 dropdown offering
exactly the four enum values (no free text, no station creation). Child menus SHALL
inherit their station from their ancestor L1 (displayed as derived). The grouping is a
rendering grouping, NOT a menu_info node level — the role_menu ancestor-chain and the
tree's data shape are unchanged.

#### Scenario: Grouped tree renders every station in one view

- **WHEN** an administrator opens the menu management page
- **THEN** station group headers render above their L1 menus with the station markers

#### Scenario: L1 form offers the closed station enum

- **WHEN** the administrator creates or edits an L1 menu
- **THEN** the 所属工作站 dropdown offers exactly admin/radiology/us/endo
- **AND** no station can be typed or created from the page


<!-- synced-to-be from: workstation-context-web (delta, revised 2026-11-06) -->

### Requirement: Workstation Type in Menu Management

The menu management type option SHALL include 工作站 (type W) so administrators can
create workstation nodes as L1 entries; the type tags SHALL label W as 工作站. The
per-user workstation assignment affordance in 用户管理 SHALL be absent (workstation
access flows through roles).

#### Scenario: Workstation nodes are menu content

- **WHEN** an administrator creates an L1 node with the 工作站 type
- **THEN** the node renders with the 工作站 type tag in the tree
- **AND** no workstation assignment affordance exists in 用户管理
