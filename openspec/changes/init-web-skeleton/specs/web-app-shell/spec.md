# Spec Delta

## Purpose

Defines the web SPA's build integrity, module layout, the department routing pattern, the
typed API client seam, the dev-server boundary to the gateway, and the design-token theming
rules that later page changes build on.

## ADDED Requirements

### Requirement: Production Build Integrity
The web application SHALL build successfully with TypeScript strict mode enabled. Strict
violations MUST be fixed, not suppressed, and the codebase MUST NOT weaken `strict` or add
`any` to silence the compiler.

#### Scenario: Clean build
- **WHEN** `pnpm --dir lumen-web install` then `pnpm --dir lumen-web build` runs in a clean
  checkout
- **THEN** both commands exit 0
- **AND** the TypeScript compilation reports no errors

#### Scenario: A strict violation is introduced
- **WHEN** code would fail strict checking
- **THEN** the code is corrected rather than exempted
- **AND** neither `strict: false` nor a broad `any` annotation is used as the fix

### Requirement: Component Style
UI code SHALL use function components and hooks only. Ant Design 5 components SHALL be
preferred over custom equivalents, and an existing component MUST NOT be modified to serve a
new use - it is composed or extended instead.

#### Scenario: New UI needed
- **WHEN** a screen requires a control that Ant Design 5 provides
- **THEN** the AntD component is used
- **AND** it is configured through props or composition rather than by editing it

#### Scenario: Existing component does not fit a new screen
- **WHEN** a shared component nearly satisfies a new requirement
- **THEN** it is wrapped or extended
- **AND** its existing behavior is left unchanged for current consumers

### Requirement: Module Layout
Source SHALL be organized into `src/api/` (typed API clients and the mock seam),
`src/pages/` (route modules per department), `src/components/` (shared components),
`src/stores/` (state), `src/layouts/` (shell and navigation), and `src/theme/` (design
tokens). The route table lives in `src/router.tsx`; the application entry in `src/main.tsx`
and `src/App.tsx`. The application SHALL be a
single SPA - department features are lazy-loaded route modules, not micro-frontends.

#### Scenario: Adding a new department
- **WHEN** a department such as ultrasound is added later
- **THEN** it becomes a lazily-loaded route module under `src/pages/`
- **AND** it does not introduce a separate deployable frontend

#### Scenario: Locating an API client
- **WHEN** a page needs to call the backend
- **THEN** the typed client lives in `src/api/`
- **AND** the page does not embed the transport logic itself

### Requirement: API Client Seam Without Invented Endpoints
`src/api/` SHALL expose the response envelope `{ timestamp, code, msg, data }` as a typed
contract consistent with `openspec/specs/api/spec.md`, with `code` typed as `string` and
entity IDs typed as strings. No endpoint path MAY be declared until it exists in
`openspec/specs/api/spec.md`.

#### Scenario: Envelope type is consumed
- **WHEN** a future API client is written against the seam
- **THEN** it returns the typed envelope with `timestamp`, `code`, `msg`, and `data`
- **AND** entity ID fields are typed as `string`, never `number`

#### Scenario: An endpoint is not yet specified
- **WHEN** a page needs data that `openspec/specs/api/spec.md` does not define
- **THEN** no endpoint path is invented in `src/api/`
- **AND** the missing contract is raised through a proposal rather than guessed at

### Requirement: Unified API Entry Boundary
All backend requests SHALL go to the `/api/v1` base path. In development the dev server SHALL
proxy `/api/v1` to the platform's unified API entry (nginx, port 80 in the hospital form) as
recorded in `openspec/coordination/init-project-skeleton.md`. Frontend code MUST NOT address
an individual service host or port.

#### Scenario: Dev-server request path
- **WHEN** the dev server handles a request to `/api/v1/<anything>`
- **THEN** it proxies the request to the unified API entry on its allocated port
- **AND** no per-service port is targeted

#### Scenario: Service host appears in source
- **WHEN** a source file hard-codes a backend service host or port
- **THEN** the unified-entry boundary is violated
- **AND** the call must be routed through the `/api/v1` base path instead

### Requirement: Design Token Theming
Visual styling SHALL be driven by Ant Design 5 theme tokens. The primary color MUST NOT be
red, because red is reserved for clinical alarm semantics. Ad-hoc hex values MUST NOT be used
in place of tokens.

#### Scenario: Theme is applied
- **WHEN** the application shell renders
- **THEN** the AntD `ConfigProvider` theme supplies the design tokens
- **AND** the resolved primary color is not red

#### Scenario: A component needs a color
- **WHEN** styling requires a specific color
- **THEN** it comes from a theme token
- **AND** no inline hex literal is introduced for it

### Requirement: Placeholder Routes Declare Their State
A route created before its feature exists SHALL render an explicit not-implemented state. It
MUST NOT fetch data or imply a delivered capability.

#### Scenario: User opens a placeholder department route
- **WHEN** the radiology placeholder route is visited
- **THEN** it renders an explicit "not yet implemented" state
- **AND** it issues no API request
