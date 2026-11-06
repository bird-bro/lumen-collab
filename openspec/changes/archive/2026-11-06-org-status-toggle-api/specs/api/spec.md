## ADDED Requirements

### Requirement: Organization Status Toggle Endpoint

`lumen-system` SHALL expose `PUT /api/v1/system/orgs/{id}/status` to set a live
organization's lifecycle status. The request body SHALL contain `status` equal to `0`
or `1`; any other value SHALL be rejected without mutation. The endpoint SHALL require
the existing `system:org:update` authority and return a standard success envelope with
`data: true`.

#### Scenario: Valid status toggle
- **WHEN** an authorized client sends `{"status":0}` to an existing organization
- **THEN** the response is a standard `200` envelope with `data: true`
- **AND** the organization status is persisted as `0`.

#### Scenario: Invalid status body
- **WHEN** a client omits `status` or sends a value other than `0` or `1`
- **THEN** the request is rejected
- **AND** the stored organization status is unchanged.
