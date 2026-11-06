# web-system-console Delta: org-management-redesign-web

## REMOVED Requirements

### Requirement: Org Tree Management

**Reason**: a generic multi-root tree does not express the on-premise hospital/campus model,
allowed uncontrolled root creation, and is not the best organization-page layout.

**Migration**: replaced by hospital profile plus campus-table management below. The seeded
hospital remains the root; existing departments move to its default campus.

### Requirement: Department Management

**Reason**: department ownership by any org node, global code uniqueness, and an
unpaginated list do not support campus ambiguity or large department volumes.

**Migration**: replaced by the hospital/campus filter tree and department table below.

## ADDED Requirements

### Requirement: Hospital Profile and Campus Table Management

The organization page SHALL render a hospital profile card on the left and a campus table on
the right. The hospital card SHALL show key profile fields and an edit action. The campus
table SHALL support keyword/status filtering and campus create/edit/default/delete actions.
The page SHALL NOT offer generic root creation, reparenting, or type changes during normal
operation. Mutation affordances SHALL follow `system:org:list`.

#### Scenario: Hospital and campuses render

- **WHEN** an administrator opens organization management
- **THEN** the left hospital profile card renders
- **AND** the right campus table renders one row per campus with code, default state, and
  status distinguishable

#### Scenario: Campus CRUD round-trip

- **WHEN** an administrator creates a campus, edits its profile, and deletes an empty campus
- **THEN** each operation calls the documented endpoint and the campus table reflects the
  result

#### Scenario: Root creation is not a normal action

- **WHEN** a hospital administrator opens the organization page
- **THEN** no generic new-root affordance is available
- **AND** creating another root through the API remains rejected by the backend

#### Scenario: Organization rejection surfaces

- **WHEN** the backend rejects a create, update, or delete because of controlled-tree rules
- **THEN** the page shows the backend message
- **AND** the hospital card and campus table are unchanged until a successful mutation

### Requirement: First-run Organization Profile Completion

The console SHALL support Option B initialization by rendering the seeded editable hospital
root and default campus. While the hospital root remains in its bootstrap state, the page
SHALL show a first-run completion hint. Administrators SHALL complete hospital details through
the existing hospital edit form; the page SHALL NOT offer root creation.

#### Scenario: First-run state renders

- **WHEN** an administrator opens organization management on a fresh deployment
- **THEN** the first-run hospital card and default campus row render
- **AND** a completion hint is shown without a generic root-creation control

#### Scenario: First-run profile is completed

- **WHEN** an administrator updates the hospital name, code, and profile through the edit form
- **THEN** the refreshed hospital card and campus table show the completed values
- **AND** the first-run hint is no longer shown

### Requirement: Campus Profile Management

The organization page SHALL collect and display hospital/campus profile fields:

- hospital and campus records: code, name, short name, status, province/city/district,
  address, contact, phone, email, logo URL, and sort;
- campus records additionally support marking one default campus per hospital.

Code SHALL be required for a new hospital or campus. Status SHALL default to enabled. Type
and parent SHALL not be edited in the form.

#### Scenario: Create campus profile

- **WHEN** an administrator submits a campus with code, name, region, address, contact, and
  status
- **THEN** the new campus appears in the campus table with those values

#### Scenario: Code is required

- **WHEN** an administrator submits a new campus without a code
- **THEN** the form blocks submission with a field-level required message

#### Scenario: Default campus remains unique

- **WHEN** an administrator marks a different campus as the default campus
- **THEN** the hospital has exactly one default campus after refresh

### Requirement: Department Campus Filter Tree

The department page SHALL render a left hospital/campus tree as a filter. The tree SHALL
contain only the hospital and campus nodes, never departments. Selecting the hospital SHALL
mean "all campuses"; selecting a campus SHALL filter departments to that campus.

#### Scenario: Departments of a selected campus

- **WHEN** an administrator selects a campus in the filter tree
- **THEN** the department table queries and displays only that campus's departments

#### Scenario: Hospital selection means all campuses

- **WHEN** an administrator selects the hospital root in the filter tree
- **THEN** the department table queries all campuses
- **AND** cross-campus rows show their campus names

#### Scenario: Tree is not a department editor

- **WHEN** an administrator views the left hospital/campus tree
- **THEN** no department nodes are rendered in the tree
- **AND** department actions are available from the right table context rather than tree
  department nodes

### Requirement: Department Table Management

The department page SHALL render a right-side department table with server-side pagination,
keyword filtering, and status filtering. It SHALL allow department creation and editing
through a dialog that requires a live, enabled campus owner. Mutation affordances SHALL
follow `system:dep:list`.

#### Scenario: Department table is paginated

- **WHEN** many matching departments exceed the page size
- **THEN** the table shows only the requested page
- **AND** the pager reports the total matched department count

#### Scenario: Keyword and status filtering

- **WHEN** an administrator filters by keyword and status
- **THEN** the table queries matching live departments by code/name and status
- **AND** changing the filter resets to the first page

#### Scenario: Department create campus context

- **WHEN** an administrator creates a department from a campus-filtered view
- **THEN** the dialog prefills that campus and requires it to be a live, enabled campus
- **AND** when the hospital-root view is selected, the dialog requires an explicit campus

#### Scenario: Hospital cannot own a department

- **WHEN** the selected node is the hospital root
- **THEN** the create dialog does not treat the hospital as the owner
- **AND** it requires a campus before submission

### Requirement: Department Code Handling

Department creation SHALL include the campus context, and the department code SHALL be
immutable after creation. Same-campus duplicate-code rejection SHALL surface on the code
field. Campus reassignment MAY be allowed, but the target campus SHALL be validated and the
code SHALL remain unchanged.

#### Scenario: Same code across campuses

- **WHEN** campus A and campus B each contain a department with code `radiology`
- **THEN** selecting campus A shows only campus A's department
- **AND** selecting campus B shows only campus B's department

#### Scenario: Code cannot be edited after creation

- **WHEN** an administrator opens the edit dialog for an existing department
- **THEN** the code field is read-only
- **AND** submitting the edit does not change the stored code

#### Scenario: Duplicate code in one campus

- **WHEN** an administrator creates a second `radiology` in the same campus
- **THEN** the backend rejection message appears on the code field
- **AND** the dialog remains open
