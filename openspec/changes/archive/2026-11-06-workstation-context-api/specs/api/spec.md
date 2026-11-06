# api Delta: workstation-context-api

## ADDED Requirements

### Requirement: Workstation Context Endpoints

`lumen-system` SHALL carry the workstation context in the session payloads and expose
the assignment endpoint:

- The login and `/me` responses SHALL include `workstations`: the array of the user's
  live workstation codes (`admin`/`radiology`/`us`/`endo`; `["admin"]` when the user
  holds no workstation rows).
- The login and `/me` profile SHALL include `primaryWorkstation`: the user's default
  workstation code (the live `is_default=1` row; `admin` when none).
- `PUT /api/v1/system/users/{id}/workstations` with body `{ workstations: [{workstation,
  isDefault}] }` SHALL full-replace the user's workstation grant (mirroring the
  role-menu assignment contract), enforce at most one live default per user, and return
  success. The endpoint SHALL be pinned by `system:user:assign-ws`.
- The Menu Tree Endpoint's nodes SHALL carry a nullable `workstation` field: the
  station enum on L1 menus (系统管理=`admin`, plus the 放射科/超声科/内镜科 placeholder
  L1s seeded by V19), `null` on every other node (a node's station = its ancestor
  L1's value). The workstation enum is closed: the assignment DTO validates it with a
  fixed pattern; clients cannot define new stations.

#### Scenario: Payloads carry the workstation context

- **WHEN** an authenticated client requests `GET /api/v1/system/me`
- **THEN** the response includes the user's `workstations` array and
  `profile.primaryWorkstation`

#### Scenario: Bootstrap users default to admin

- **WHEN** a user with no workstation rows requests login or `/me`
- **THEN** `workstations` is `["admin"]` and `profile.primaryWorkstation` is `"admin"`

#### Scenario: Full-replace assignment

- **WHEN** an authorized client puts a workstation set to
  `PUT /api/v1/system/users/{id}/workstations`
- **THEN** the posted set is the user's complete grant afterwards
- **AND** at most one row carries `isDefault = true`

#### Scenario: Assignment requires the button perm

- **WHEN** a session without `system:user:assign-ws` calls the assignment endpoint
- **THEN** the request is denied with the `A0301` envelope

#### Scenario: Tree nodes mark their station on L1

- **WHEN** an authenticated client requests `GET /api/v1/system/menus/tree`
- **THEN** the L1 menus carry `workstation` values (系统管理=`admin`, 放射科=
  `radiology`, …) and every other node carries `null` (station = ancestor L1)
