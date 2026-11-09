# data Delta: role-type-dict-api

## ADDED Requirements

### Requirement: Role Enable Column

`role_info` SHALL carry `enable integer NOT NULL DEFAULT 1` (V24 migration) —
the lifecycle flag rendered by the console's 状态 column and toggled by
`PUT /api/v1/system/roles/{id}/enable`. Existing rows inherit the default
(enabled); the column follows the dict vocabulary's `enable` name, not orgs'
`status`. Disabling has NO server-side behavioral consequence in this change
(store/display/toggle only — coordination D7).

#### Scenario: Migration adds the column with a safe default

- **WHEN** the migration chain replays through V24
- **THEN** `role_info` carries `enable` NOT NULL DEFAULT 1 and every existing
  row reads as enabled

#### Scenario: Toggle persists through the entity

- **WHEN** the enable toggle endpoint posts `0` for a role
- **THEN** `role_info.enable` reads `0` afterwards and the row remains live
  (the logical-delete column is untouched)
