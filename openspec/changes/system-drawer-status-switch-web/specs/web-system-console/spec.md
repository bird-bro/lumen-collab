## ADDED Requirements

### Requirement: Drawer Lifecycle Status Controls

Binary lifecycle status fields in the organization, department, and room drawers SHALL
use the number-rule drawer pattern: they SHALL NOT use a Select; the create drawer SHALL
hide status and submit the record enabled; the edit drawer SHALL render status as a
Switch in the common boolean-switch row. Organization and department switches SHALL save
through their existing profile update APIs; the room switch SHALL use the existing room
status endpoint. Dedicated table status switches SHALL remain unchanged.

#### Scenario: Create drawer hides lifecycle status

- **WHEN** an administrator opens an organization, department, or room create drawer
- **THEN** the drawer renders no status Select or status Switch
- **AND** submitting creates the record with enabled status

#### Scenario: Edit drawer uses the status Switch

- **WHEN** an administrator opens an organization, department, or room edit drawer
- **THEN** the drawer renders status as a Switch, not a Select
- **AND** changing organization or department status persists through the profile update request
- **AND** changing room status persists through the room status endpoint
