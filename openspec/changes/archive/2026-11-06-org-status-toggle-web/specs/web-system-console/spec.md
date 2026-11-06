## ADDED Requirements

### Requirement: Organization Status Switch Uses Status Endpoint

The organization campus table SHALL render status as a switch consistent with user
management. Toggling the switch SHALL call `PUT /api/v1/system/orgs/{id}/status` with
`status: 0` or `status: 1`, then refresh the organization tree.

#### Scenario: Campus status toggle
- **WHEN** an administrator flips an enabled campus switch off
- **THEN** the client calls the dedicated status endpoint with `status: 0`
- **AND** the refreshed tree determines the switch state.
