# web-app-shell Delta: workstation-context-web

## ADDED Requirements

### Requirement: Workstation Switcher and Navigation Filtering

The shell SHALL render a workstation chip in the header (zone 2, left of the product
title) showing the active workstation; its dropdown lists every workstation in the
user's session `workstations` payload (icon + name + one-line description, a check on
the active row). Selecting a station stores it in the auth store and reroutes to that
station's landing page; the secondary sidebar SHALL then render ONLY the subtree of
the L1 menus whose `workstation` marker equals the active station (api delta) —
the filter reads the ownership field on L1 (no path-prefix rules, no runtime string
matching against user data). The sidebar shows those L1s flat (single-station content
needs no second header). Re-login SHALL land on the session's
`primaryWorkstation`. The `admin` station SHALL render the console subtree exactly as
today. The menu management page shows the WHOLE tree (all four roots) regardless of
the active station - administrators never switch stations to manage menus.

#### Scenario: Chip lists granted stations

- **WHEN** a user whose `/me` carries `workstations: ["admin", "radiology"]` opens the
  chip dropdown
- **THEN** both stations render with the active one checked

#### Scenario: Switching reroutes and refilters

- **WHEN** the user picks a different station in the dropdown
- **THEN** the app navigates to that station's landing page and the sidebar renders
  only that station's domain

#### Scenario: Default selection is the first W node

- **WHEN** a user whose granted tree carries 放射工作站 and 超声工作站 logs in
- **THEN** the chip's active station is 放射工作站 (the first) without manual
  switching
- **AND** 放射工作站's child menus are promoted into the left menu bar

#### Scenario: 控制台 is today's sidebar

- **WHEN** the active station is 控制台 (`admin`)
- **THEN** the sidebar renders the admin-domain L1 menus exactly as before this change

#### Scenario: Department station appends beside 系统管理

- **WHEN** the user selects 放射工作站 and 放射科's L1 登记 exists in the granted tree
- **THEN** the sidebar shows 系统管理 AND 登记 side by side (append, not replace)

#### Scenario: Menu management shows the grouped tree regardless of the active station

- **WHEN** an administrator on any active station opens 菜单管理
- **THEN** the tree renders station group headers above their L1 menus (station =
  L1 ownership field; no switching needed to manage another station's menus)
