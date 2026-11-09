# web-system-console Delta: role-list-api

## MODIFIED Requirements

### Requirement: Role Management and Menu Assignment

The console SHALL provide role management backed by `GET /api/v1/system/roles/list`,
create (`POST /api/v1/system/roles`), edit (`PUT /api/v1/system/roles/{id}`), and delete
(`DELETE /api/v1/system/roles/{id}`). For each role, menus SHALL be assignable via a tree
with checkboxes that full-replaces the grant through `PUT /api/v1/system/roles/{id}/menus`.
The list is served by the real backend endpoint (the former dev-mock arrangement is
retired); its rows carry the `menuIds` grant echo that drives the assignment dialog's
initial checked set and the 授权数 column.

#### Scenario: Role CRUD round-trip

- **WHEN** an administrator creates, renames, and deletes a role
- **THEN** the documented endpoints are called and the list reflects the result

#### Scenario: Menu assignment is full-replace

- **WHEN** an administrator checks a set of menus for a role and saves
- **THEN** the checked set is sent as the role's complete grant
- **AND** reopening the dialog shows exactly that set
