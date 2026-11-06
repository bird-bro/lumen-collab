# auth-rbac Delta: workstation-context-api

## ADDED Requirements

### Requirement: Workstation Authorization

The service SHALL maintain a typed workstation grant per user in
`system.user_workstation` (`user_id` FK, `workstation` enum admin/radiology/us/endo,
`is_default` flag + the shared audit block with logical delete), enforcing at most one
live default row per user (partial unique index). The workstation enum SHALL be closed -
no free-text stations; v2 adds `remote`.

A user with no live workstation rows SHALL be treated as holding only `admin` (the
console is not department work) - existing accounts continue to work without migration
backfill. The login/me payloads SHALL carry the effective grant and default per the api
spec. The assignment endpoint SHALL be enforced at button level by the
`system:user:assign-ws` authority (seeded as an F node under 用户管理 with the admin
auto-grant, the V15 content-guard discipline).

The menu tree SHALL carry the station grouping: V21 marks the existing 系统管理 L1 as
`admin` and seeds the 放射科/超声科/内镜科 placeholder L1s with their station codes
(`menu_info.workstation`, L1-level only). The station enum SHALL be closed - a fixed
form dropdown in the L1 create/edit form, NOT operator-typed or a creatable node
(the station set is a product enum); a child menu's station is its ancestor L1's
value. The 菜单管理 page SHALL render the tree GROUPED by workstation (a group header
row per station above its L1 menus - a rendering grouping, NOT a menu_info node), so
administrators see and manage every station's menus in one tree without switching.

#### Scenario: Default uniqueness is enforced

- **WHEN** an assignment posts two rows with `isDefault = true` for one user
- **THEN** the request is rejected (an `A`-class business error) and no rows change

#### Scenario: Grant flows to the next login

- **WHEN** an administrator assigns workstations to a user
- **THEN** the user's next login/me carries the new `workstations` array and
  `primaryWorkstation`

#### Scenario: Deleted user loses the grant

- **WHEN** a user is logically deleted
- **THEN** their workstation rows stop contributing to any payload (live-row joins)

#### Scenario: Grouped tree shows every station in one view

- **WHEN** the migrations run and an administrator opens the menu management page
- **THEN** the tree renders group headers (系统管理工作站 admin / 放射科工作站
  radiology / 超声科 us / 内镜科 endo) each above its L1 menus with their station
  markers
- **AND** the L1 form's station dropdown offers exactly the four enum values
  (no free text, no station creation)
