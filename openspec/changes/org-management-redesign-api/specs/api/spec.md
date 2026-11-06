# api Delta: org-management-redesign-api

## REMOVED Requirements

### Requirement: Org Tree Endpoints

**Reason**: the generic multi-root organization contract does not express the controlled
on-premise hospital/campus model.

**Migration**: replaced by Controlled Organization Management Endpoints below. Existing
routes remain.

### Requirement: Department Endpoints

**Reason**: department ownership by any org node, global code uniqueness, and the array-only
list contract cannot support campus ambiguity and a scalable department table.

**Migration**: replaced by the department requirements below. The existing list route
remains, and the page route is added.

## ADDED Requirements

### Requirement: Controlled Organization Management Endpoints

`lumen-system` SHALL expose organization management under `/api/v1/system/orgs` for the
controlled hospital/campus model. `GET /tree` SHALL return the hospital root and campuses
with profile fields. `POST` SHALL create only a bootstrap hospital root or a campus under
that hospital. `PUT` SHALL update profile fields and status only. `DELETE` SHALL be guarded
by live dependents. The family remains gated by `system:org:list`.

#### Scenario: Controlled tree query

- **WHEN** an authenticated client requests the organization tree
- **THEN** it returns the single live hospital root and its campus children with profile
  fields

#### Scenario: Campus creation

- **WHEN** an authorized client posts a campus record under the live hospital
- **THEN** the endpoint returns the new campus id
- **AND** the tree contains that campus under the hospital

#### Scenario: Second hospital root is rejected

- **WHEN** a client posts another `upId = 0` hospital while a live root exists
- **THEN** the response is an A-class business error with a descriptive message
- **AND** no organization row is created

#### Scenario: Campus cannot contain a child organization

- **WHEN** a client posts an organization whose parent is a campus
- **THEN** the response is an A-class business error
- **AND** no organization row is created

#### Scenario: Type and parent are immutable

- **WHEN** a client updates an organization with a different type or a different parent id
- **THEN** the response is an A-class business error
- **AND** the stored type and parent remain unchanged

#### Scenario: Organization delete guard

- **WHEN** a client deletes an organization that has a live child organization or live
  department
- **THEN** the response is an A-class business error
- **AND** the organization and its dependents remain live

### Requirement: Existing Department List Compatibility

`lumen-system` SHALL continue to expose `GET /api/v1/system/org-deps/list`. An absent
`orgId` SHALL return all live departments. A present `orgId` SHALL filter departments by
the owning organization. The endpoint SHALL remain gated by `system:dep:list`.

#### Scenario: Existing list without a campus filter

- **WHEN** an authenticated client requests the existing department list without `orgId`
- **THEN** all live departments return in `data`

#### Scenario: Existing list with a campus filter

- **WHEN** an authenticated client requests the existing department list with a campus
  `orgId`
- **THEN** only that campus's departments return in `data`

### Requirement: Department Table Query

`lumen-system` SHALL expose `GET /api/v1/system/org-deps/page` for the department table.
The query SHALL support optional `orgId`, `keyword`, `status`, `pageNum`, and `pageSize`.
An absent `orgId` SHALL mean all campuses; a present `orgId` SHALL be a campus. Results
SHALL be paginated and deterministically ordered. The endpoint SHALL remain gated by
`system:dep:list`.

#### Scenario: Department table query returns all campuses

- **WHEN** the department page query is requested without `orgId`
- **THEN** departments from every campus are returned in the page result
- **AND** the total count covers all matching campus rows

#### Scenario: Department table query filters a campus

- **WHEN** the department page query is requested with a campus `orgId`
- **THEN** only that campus's matching departments are returned
- **AND** the result is ordered deterministically

#### Scenario: Department table query filters keyword and status

- **WHEN** the department page query is requested with a keyword and a status
- **THEN** only live departments whose code or name matches the keyword and whose status
  matches the requested value are returned

### Requirement: Campus-Scoped Department Mutations

`POST /api/v1/system/org-deps` and `PUT /api/v1/system/org-deps/{id}` SHALL require a live,
enabled campus owner. A hospital root or disabled campus SHALL NOT own a department. A
department code SHALL be immutable after creation and unique among live departments within
the same campus but MAY repeat across campuses.

#### Scenario: Department belongs to a campus

- **WHEN** an authorized client creates a department for a live, enabled campus
- **THEN** it returns the new department id
- **AND** the department list for that campus contains the department

#### Scenario: Hospital owner is rejected

- **WHEN** a client creates or updates a department whose owning org is the hospital root
- **THEN** the response is an A-class business error
- **AND** no department ownership change is persisted

#### Scenario: Disabled campus cannot receive a department

- **WHEN** a client creates or updates a department whose owning org is a disabled campus
- **THEN** the response is an A-class business error
- **AND** no department ownership change is persisted

#### Scenario: Department code is immutable

- **WHEN** a client updates a department with a code different from the stored code
- **THEN** the response is an A-class business error
- **AND** the stored code remains unchanged

#### Scenario: Duplicate code within a campus

- **WHEN** a client creates or updates a department with a code that already exists live in
  the same campus
- **THEN** the response is an A-class duplicate-code error

#### Scenario: Same code across campuses

- **WHEN** campuses A and B each have a department with code `radiology`
- **THEN** both departments persist
- **AND** querying each campus returns only its own department
