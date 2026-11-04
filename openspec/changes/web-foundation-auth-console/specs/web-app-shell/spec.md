# Spec Delta

## MODIFIED Requirements

### Requirement: Design Token Theming
Visual styling SHALL be driven by Ant Design 5 theme tokens carrying the single
blue-family visual language (user decisions 2026-11-04): the action primary color is bright
blue `#3178fc` and the navigation shell is near-black tech blue `#0B2540` (rail decision),
and the base font size is 18px per the OPS type scale. The primary color MUST NOT be red, because red is
reserved for clinical alarm semantics. Ad-hoc hex values MUST NOT be used in place of tokens;
hex literals live only in the theme token module. The bundled MiSans family is the
application font (fonts owned by the `web-design-system` capability).

#### Scenario: Theme is applied
- **WHEN** the application shell renders
- **THEN** the AntD `ConfigProvider` theme supplies the design tokens
- **AND** the resolved primary color is the bright blue `#3178fc` family and is not red,
  with the shell resolving to the near-black tech blue navigation family
- **AND** the resolved base font size is 18px

#### Scenario: A component needs a color
- **WHEN** styling requires a specific color
- **THEN** it comes from a theme token
- **AND** no inline hex literal is introduced for it

### Requirement: Module Layout
Source SHALL be organized into `src/api/` (typed API clients and the mock seam),
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

## MODIFIED Requirements

### Requirement: API Client Seam Without Invented Endpoints
`src/api/` SHALL expose the response envelope `{ timestamp, code, msg, data }` as a typed
contract consistent with `openspec/specs/api/spec.md`, with `code` typed as `string` and
entity IDs typed as JSON numbers per that spec's ID Serialization requirement (Integer
auto-increment; the former snowflake-string clause is withdrawn - user decision 2026-11-02,
synced by rename-auth-to-system). No endpoint path MAY be declared until it exists in
`openspec/specs/api/spec.md`.

#### Scenario: Envelope type is consumed
- **WHEN** a future API client is written against the seam
- **THEN** it returns the typed envelope with `timestamp`, `code`, `msg`, and `data`
- **AND** entity ID fields are typed as `number`, never `string` (per ID Serialization)

#### Scenario: An endpoint is not yet specified
- **WHEN** a page needs data that `openspec/specs/api/spec.md` does not define
- **THEN** no endpoint path is invented in `src/api/`
- **AND** the missing contract is raised through a proposal rather than guessed at

## ADDED Requirements

### Requirement: Dev Mock Server
The dev server SHALL serve mock responses for every endpoint documented in
`openspec/specs/api/spec.md` so the SPA runs without the backend. Mocks SHALL live behind the
`src/api/` client seam and the `/api/v1` proxy boundary - pages MUST NOT branch on mock
versus real backend.

#### Scenario: Console runs without backend
- **WHEN** `pnpm --dir lumen-web dev` runs with no backend process available
- **THEN** login and the system console pages function against mock responses in the standard
  envelope shape
- **AND** every mocked path is one the api capability documents

#### Scenario: Undocumented endpoint refused
- **WHEN** a mock is needed for a path absent from `openspec/specs/api/spec.md`
- **THEN** the mock is not written
- **AND** the missing contract is raised through a proposal instead

#### Scenario: Seed parity
- **WHEN** the mock fixtures are compared with the backend's seed migrations
- **THEN** the default administrator, the hospital root, the three departments, and the
  starter dictionary resolve equivalently on both sides
