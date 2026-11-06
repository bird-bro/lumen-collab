# web-app-shell Specification

## Purpose
Defines the web SPA's build integrity, module layout, the department routing pattern, the
typed API client seam, the dev-server boundary to the gateway, and the design-token theming
rules that later page changes build on.

## Requirements
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

<!-- synced from: web-foundation-auth-console 2026-11-05 -->
<!-- synced from: frontend-backend-first 2026-11-05 -->

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

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

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

### Requirement: Placeholder Routes Declare Their State
A route created before its feature exists SHALL render an explicit not-implemented state. It
MUST NOT fetch data or imply a delivered capability.

#### Scenario: User opens a placeholder department route
- **WHEN** the radiology placeholder route is visited
- **THEN** it renders an explicit "not yet implemented" state
- **AND** it issues no API request

<!-- synced from: web-foundation-auth-console 2026-11-05 -->

### Requirement: Dev Server Serves Only Real Backend
The dev server SHALL have NO mock layer (user ruling 2026-11-05, backend-first): every
`/api/v1` request SHALL fall through the proxy to the real unified entry
(`VITE_API_ENTRY_URL` override, default nginx :80), and the SPA SHALL render real database
data. When the entry is unreachable the failure SHALL surface as-is (the transport's
B-class error) - the SPA MUST NOT fake or stub data at any layer.

#### Scenario: Pages render real database data
- **WHEN** the backend is running and a console page loads
- **THEN** every list/detail payload comes from the real backend through the proxy
- **AND** no request is served by an in-process fake

#### Scenario: Unreachable entry fails loudly
- **WHEN** the unified entry is down and a page issues a request
- **THEN** the transport's B-class error surfaces to the operator
- **AND** no canned substitute data renders

#### Scenario: Direct-service preview override
- **WHEN** a developer previews against a single service started outside nginx
  (e.g. lumen-system on :8081)
- **THEN** `VITE_API_ENTRY_URL=http://localhost:8081` retargets the proxy without any
  source change

<!-- synced from: frontend-backend-first 2026-11-05 -->

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


<!-- synced from: workstation-context-web 2026-11-06 - REVISED: stations derive from
     the granted tree's type-W nodes (the per-user grant machinery was torn down by
     workstation-w-nodes-api; see archive/workstation-context-web + workstation-w-nodes-api) -->

### Requirement: Workstation Switcher and Menu Promotion

The shell SHALL render a workstation chip in the header (zone 2, left of the product
title); its dropdown lists every workstation node in the granted menu tree
(`type = 'W'`, title/icon from the node, a check on the active), defaulting to the
FIRST granted W node. Display rule (user spec 2026-11-06):

- **W-type** nodes appear ONLY in the chip dropdown; the ACTIVE W node's child menus
  are PROMOTED one level and render in the left menu bar beside the resident M-type
  L1 menus (登记 beside 系统管理 — the append the user described). The W node itself
  never renders as a menu-bar entry.
- **M-type** L1 menus render exactly as today (current hierarchy unchanged).
- Clicking a promoted entry navigates normally WITHOUT changing the chip's active
  station; the active station changes only through the chip. Session-scoped: re-login
  re-selects the first W node; a user with no granted W node sees no chip (console
  only).

#### Scenario: Chip lists granted stations

- **WHEN** a user whose granted tree carries 放射工作站 and 超声工作站 opens the chip
  dropdown
- **THEN** both stations render with the active one checked

#### Scenario: Default selection is the first W node

- **WHEN** a user whose granted tree carries 放射工作站 and 超声工作站 logs in
- **THEN** the chip's active station is 放射工作站 (the first) without manual
  switching
- **AND** 放射工作站's child menus are promoted into the left menu bar

#### Scenario: Switching swaps the promoted set

- **WHEN** the user selects a different workstation in the dropdown
- **THEN** the promoted set in the left menu bar is replaced by the newly active
  station's children
- **AND** the resident 系统管理 L1 stays in place

#### Scenario: A station without leaf pages switches context only

- **WHEN** the user selects a workstation node whose subtree carries no leaf route
- **THEN** the active station changes WITHOUT navigating (navigating to the index
  route would blank the page)
