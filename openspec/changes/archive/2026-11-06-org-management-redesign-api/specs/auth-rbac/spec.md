# auth-rbac Delta: org-management-redesign-api

## REMOVED Requirements

### Requirement: Hierarchical Org Tree

**Reason**: replaced by the controlled hospital/campus model required by the on-premise
product.

**Migration**: the existing seeded hospital becomes the single hospital root and receives a
default campus; existing departments migrate to that campus.

### Requirement: Department Anchored to the Org Tree

**Reason**: anchoring to any org node is too permissive for campus workflows.

**Migration**: departments anchor to the default campus or another live, enabled campus;
their ids and codes remain stable.

## ADDED Requirements

### Requirement: Hospital-Campus Organization Types

The on-premise organization model SHALL use `org_info.type = 0` for hospital and `type = 1`
for campus in v1. At most one live hospital root SHALL exist. A hospital MAY have campus
children, while a campus SHALL NOT have child `org_info` nodes.

#### Scenario: Fresh on-premise deployment

- **WHEN** migrations complete on a fresh database
- **THEN** one editable first-run hospital root and one default campus exist
- **AND** the hospital root uses the Option B bootstrap placeholder details

#### Scenario: Existing departments remain visible

- **WHEN** the migration moves seeded departments to the default campus
- **THEN** the three seeded departments remain live and enabled with their original codes
- **AND** they are returned for the default campus

#### Scenario: Root proliferation is blocked

- **WHEN** an ordinary administrator attempts to create a second live hospital root
- **THEN** the service rejects the request with an A-class business error

### Requirement: Organization Lifecycle Guards

Organization type SHALL be immutable after creation. Reparenting SHALL be rejected in this
change. Organization deletion SHALL be refused while the node has live child organizations
or live departments.

#### Scenario: Type and parent cannot be rewritten

- **WHEN** an administrator updates an organization with a new type or parent
- **THEN** the service rejects the request with an A-class business error
- **AND** the stored type and parent remain unchanged

#### Scenario: Dependent organization cannot be deleted

- **WHEN** an administrator deletes an organization with a live child or department
- **THEN** the service rejects the deletion with an A-class business error
- **AND** the organization and dependents remain live

### Requirement: Campus-Anchored Departments

Departments SHALL belong to live, enabled campus nodes rather than arbitrary organization
nodes:

- Department create/update SHALL reject hospital-root and disabled-campus owners.
- Department code uniqueness SHALL be scoped to the owning campus among live rows.
- Department codes SHALL be immutable after creation.
- Department ids and existing codes SHALL remain stable through migration.

#### Scenario: Two campuses have the same department

- **WHEN** campus A and campus B each define `radiology`
- **THEN** both department rows persist
- **AND** each campus list returns only its own row

#### Scenario: Hospital cannot own a department

- **WHEN** a client creates or updates a department whose owner is the hospital root
- **THEN** the request is rejected with an A-class business error
- **AND** the department row is unchanged

#### Scenario: Legacy department migration preserves routing identity

- **WHEN** the seeded departments move from the hospital root to the default campus
- **THEN** their department ids and codes are unchanged
