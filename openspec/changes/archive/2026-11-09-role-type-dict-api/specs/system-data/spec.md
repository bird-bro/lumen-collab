# system-data Delta: role-type-dict-api

## ADDED Requirements

### Requirement: Role Type Dict Catalog

The platform dict seed SHALL carry a 「角色类型」 catalog (id 1020) whose details
define the `role_info.type` vocabulary: code **10 = 系统角色**, code **11 =
RCP角色** — mirroring the copied `RoleConstants` lineage (`TYPE_GLOBAL`,
`TYPE_RCP`). Seeded deterministically with ON CONFLICT guards (V6 house style) so
re-runs are no-ops; further categories are operator-managed rows in 字典管理
(no code change required — user ruling 2026-11-09).

#### Scenario: Seed provisions the role type catalog

- **WHEN** a fresh deployment replays the migration chain through V23
- **THEN** `dict_catal` contains 「角色类型」 (id 1020, enabled) and
  `dict_detail` contains its two items (code 10 / code 11) with deterministic ids
  1020001 / 1020002

#### Scenario: Seed re-run is a no-op

- **WHEN** the migration chain replays on a database that already carries the
  catalog
- **THEN** no duplicate rows appear and Flyway reports success

#### Scenario: Operator extends the vocabulary

- **WHEN** an administrator adds a detail (code + value) to the 「角色类型」
  catalog in 字典管理
- **THEN** the new category is immediately part of the type vocabulary served by
  the by-name lookup — no deployment or code change involved
