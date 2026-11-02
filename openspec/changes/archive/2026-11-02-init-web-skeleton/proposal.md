# Proposal: init-web-skeleton

## Why

`lumen-web/` today contains only `CLAUDE.md` and `AGENTS.md` - there is no `package.json`, no
build, and nothing to run. The frontend agent's role file mandates mock-first development
against `openspec/specs/api/spec.md`, but there is no scaffold for that seam to live in.
Establishing the SPA shell now means later page changes add routes and components instead of
arguing about build tooling, TypeScript configuration, or how `/api/v1` reaches the gateway.

## What Changes

- Scaffold `lumen-web/` as a Vite + React 18 + TypeScript single SPA using pnpm.
- Configure TypeScript `strict` mode; function components and hooks only.
- Add Ant Design 5 with theming driven by design tokens, not ad-hoc hex values, and with red
  excluded from the primary palette (product decision in `lumen-web/CLAUDE.md`).
- Create the module structure the role file specifies: `src/api/`, `src/pages/`,
  `src/components/`, `src/stores/`, `src/layouts/`.
- Add an application shell layout with navigation and a lazily-loaded `radiology/` route
  module as a placeholder, establishing the department-as-route-module pattern that
  `ultrasound/` and `endoscopy/` will follow.
- Configure the dev server to proxy `/api/v1` to the nginx unified entry at port 80, so no
  frontend
  code ever targets a service port directly.
- **No API endpoints are implemented or invented.** The `src/api/` layer is an empty seam with
  the typed response envelope; concrete clients arrive with the feature changes that have
  endpoints in `openspec/specs/api/spec.md` to mock from.
- **No mock tool is selected in this change.** `lumen-web/CLAUDE.md` requires mock-first
  development, but choosing between MSW, `vite-plugin-mock-dev-server`, and mockjs is a
  separate decision. The gap is recorded in `debt.md`.

## Capabilities

### New Capabilities

- `web-app-shell`: the SPA's build integrity, module layout, routing pattern for department
  modules, the typed API client seam, the dev-server proxy boundary to the gateway, and the
  design-token theming rules including the red exclusion.

### Modified Capabilities

None. `openspec/specs/api`, `openspec/specs/data`, and `openspec/specs/errors` are unchanged.
The shell establishes the seam through which those contracts will be consumed; it implements
none of them, because no endpoint exists yet to call or mock.

## Affected Stacks

single-stack: frontend (`lumen-web/`)

## Depends-On

Cross-stack tag `init-project-skeleton` - see
`openspec/coordination/init-project-skeleton.md`. Depends on the coordination doc's port
allocation (nginx unified entry, port 80) for the proxy target, but not on
`init-backend-skeleton` being applied: the proxy points at a port, and `pnpm build` never
makes a network call.

## Scope

- In scope:
  - `lumen-web/package.json`, `pnpm-lock.yaml`, `tsconfig*.json`, `vite.config.ts`,
    `index.html`, `.gitignore` additions
  - `src/main.tsx`, `src/App.tsx`, shell layout, router with a lazy `radiology/` placeholder
  - `src/api/` seam with the typed `{ timestamp, code, msg, data }` envelope (OPS `R`
    contract) and a base-URL constant
  - AntD 5 `ConfigProvider` theme with design tokens; red excluded from primary palette
  - Dev-server proxy `/api/v1` -> the nginx unified entry (`http://localhost:80`)
  - `pnpm build` passing under TypeScript strict
- Out of scope:
  - Any concrete API client, endpoint, or mocked response
  - Selecting or wiring a mock tool
  - Login/auth UI, patient/booking/exam/report pages
  - A test runner (Vitest) - `verify.config.yaml` currently has `test: null` for this stack
  - DICOM viewer or image display components
  - `docker/` and any deployment configuration
  - CI workflow

## Success Criteria

- `pnpm --dir lumen-web install` and `pnpm --dir lumen-web build` both exit 0.
- TypeScript strict compilation produces no errors and no `any` escape hatches added to
  silence the compiler.
- `src/api/` exposes the response envelope type matching `openspec/specs/api/spec.md`, with
  no endpoint path invented.
- The dev-server proxy maps `/api/v1` to the nginx unified entry (port 80), and no source file
  contains a hard-coded service port.
- The primary theme token is not red, and colors come from theme tokens rather than inline hex.
- `openspec validate init-web-skeleton --strict` passes.

## Constraints

- React 18 and Ant Design 5 - the project has not adopted React 19 or AntD 6.
- pnpm, single SPA; departments are lazy-loaded route modules, **not** micro-frontends.
- TypeScript strict; function components and hooks only.
- Never modify existing components - compose or extend.
- Avoid red as a primary color (alarm semantics reserved for clinical warnings).
- File names kebab-case; auto-loaded files English only.
- `verify.config.yaml` has `test: null` for `lumen-web`, so L3 reports SKIP, not FAIL. No test
  runner is added by this change.

## Risks

- Latest registry versions are React 19.x and AntD 6.x, so a careless install drifts off the
  constrained stack and breaks the "AntD 5 first" rule → pin exact versions in
  `package.json`; `design.md` records the verified set and the peer-dependency reasoning.
- Vite's Node engine requirement excludes older Node releases, so CI and contributor machines
  could fail differently → record the required Node range in `design.md` and in
  `package.json` `engines`.
- Scaffolding an API seam without a mock tool leaves a gap the role file explicitly requires
  → logged in `debt.md` with a revisit trigger, rather than silently shipping an incomplete
  mock-first story.
- A placeholder `radiology/` route could be mistaken for a delivered page → the placeholder
  renders an explicit "not yet implemented" state and no data fetching.
