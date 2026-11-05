# Spec Delta

## MODIFIED Requirements

### Requirement: Module Layout
Source SHALL be organized into `src/api/` (typed API clients),
`src/pages/` (route modules per department plus platform-level pages such as the login page
and the system console), `src/components/` (shared components), `src/stores/` (state),
`src/layouts/` (shell and navigation), and `src/theme/` (design tokens and fonts). The route
table lives in `src/router.tsx`; the application entry in `src/main.tsx` and `src/App.tsx`.
The application SHALL be a single SPA - department features are lazy-loaded route modules,
not micro-frontends.

#### Scenario: Adding a new department
- **WHEN** a department such as ultrasound is added later
- **THEN** it becomes a lazily-loaded route module under `src/pages/`
- **AND** it does not introduce a separate deployable frontend

#### Scenario: Locating an API client
- **WHEN** a page needs to call the backend
- **THEN** the typed client lives in `src/api/`
- **AND** the page does not embed the transport logic itself

#### Scenario: Locating a platform page
- **WHEN** the login page or a system console page is looked for
- **THEN** it lives under `src/pages/` alongside the department route modules
- **AND** it uses the same typed client seam and theme tokens as the rest of the app

## ADDED Requirements

### Requirement: Stub and Demo Data Boundaries
Unit tests MAY stub API client modules internally; such stubs SHALL NOT serve
page data at runtime. Static demo pages with hard-coded data MAY exist for
visual preview, but their data SHALL NOT route through `src/api/` clients or
the network layer, and the page SHALL be identifiable as a demo.

#### Scenario: Test stubs stay at the module seam
- **WHEN** the unit-test suite runs
- **THEN** API client modules are stubbed via the module seam
  (`vi.mock('@/api/...')`)
- **AND** the stubs have no effect on the dev-server request path

#### Scenario: Static demo page boundary
- **WHEN** a static demo page with hard-coded data is added for visual preview
- **THEN** its data does not pass through `src/api/` clients or the network
  layer
- **AND** it is identifiable as a demo (not presented as backend-backed data)
