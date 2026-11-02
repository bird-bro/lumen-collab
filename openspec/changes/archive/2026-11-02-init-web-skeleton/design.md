# Design

## Context

`lumen-web/` holds only `CLAUDE.md`/`AGENTS.md`; there is no `package.json` and nothing to
build. See `proposal.md` § Why for motivation and `specs/web-app-shell/spec.md` for the
normative requirements. This design records the pinned toolchain, the Node engine floor, the
proxy target, and why the mock tool is deferred.

The binding constraint is `openspec/verify.config.yaml`: this stack's L1 gate is `pnpm build`
and its L3 gate is `test: null` (SKIP). So "done" means the production build is green under
TypeScript strict - there is no test runner in this change.

## Goals / Non-Goals

**Goals:**
- A green `pnpm install` + `pnpm build` from a clean checkout.
- The module layout, envelope seam, proxy boundary, and token theming that later page changes
  plug into.
- One lazy-loaded department route proving the pattern, with an honest placeholder state.

**Non-Goals:**
- Any real page, form, or data flow.
- Selecting or wiring a mock tool (deferred - see D5).
- Adding Vitest, which would change `verify.config.yaml`'s L3 from SKIP to a real gate.
- Auth UI, routing guards, or i18n.

## Decisions

### D1 - Pinned toolchain (verified against the npm registry, 2026-10-29)
The registry's `latest` tags have moved past this project's constraints - React is at 19.x and
AntD at 6.x - so every pin below is a deliberate choice, not a default. All were confirmed to
exist with compatible peer ranges.

| Package | Version | Why |
|:--|:--|:--|
| `react` / `react-dom` | 18.3.1 | Project mandates React 18; 18.3.1 is the final 18.x patch |
| `antd` | 5.29.3 | Project mandates AntD 5; `latest-5` tag. Peer: `react >=16.9` - satisfied |
| `vite` | 7.3.7 | Node engine `^20.19.0 \|\| >=22.12.0`; Vite 8 is available but 7.x is the conservative pick that still satisfies the engine on current LTS |
| `@vitejs/plugin-react` | 5.2.0 | Peer `vite: ^4.2 \|\| ^5 \|\| ^6 \|\| ^7 \|\| ^8` - compatible with Vite 7. (plugin-react 6.x peers **only** `^8`, so it would force a Vite 8 upgrade) |
| `typescript` | 5.9.3 | Final 5.x line; TS 7 exists but is a different compiler distribution and would risk AntD/Vite type compatibility |
| `react-router-dom` | 7.18.5 | Peer `react >=18` - satisfied. Data-router API gives lazy route modules cleanly |

- **Alternative considered:** adopt the current registry defaults (React 19 + AntD 6 + Vite 8 +
  TS 7). Rejected - root `CLAUDE.md` and `lumen-web/CLAUDE.md` both specify React 18 + AntD 5,
  and AntD 6 would break the "Ant Design 5 components first" coding standard. Upgrading the
  stack is its own proposal.
- **Alternative considered:** Vite 8 + plugin-react 6. Viable on Node 22.16, but rejected for
  the skeleton to keep the toolchain one major behind the newest release while Vite 7 remains
  supported; revisit when the stack upgrade proposal happens.

### D2 - Node engine floor recorded in package.json
Vite 7.3.7 requires Node `^20.19.0 || >=22.12.0`. `package.json` declares that range in
`engines` so a contributor on Node 18 gets a clear failure at install rather than a confusing
build error. The local toolchain was verified at Node v22.16.0 and pnpm 10.32.1.

### D3 - Proxy target comes from the coordination doc
The dev server proxies `/api/v1` to `http://localhost:80`, the **nginx unified entry** port
allocated in `openspec/coordination/init-project-skeleton.md` (user decision 2026-10-29:
there is no Java gateway; nginx is the only inbound route). The target is configurable via an
env var (`VITE_API_ENTRY_URL`, defaulting to `http://localhost:80`) so no service port is
hard-coded in source. Only the unified entry is ever targeted -
`specs/web-app-shell` § Unified API Entry Boundary.

### D4 - Envelope seam mirrors the API spec, with IDs as strings
`src/api/` exports the envelope as a generic type:
```ts
interface ApiEnvelope<T> { timestamp: string; code: string; msg: string; data: T | null }
```
This is the OPS `R` contract adopted verbatim (`timestamp`, string `code`, `msg`) - the API
spec was aligned to it by `init-backend-skeleton` (user decision 2026-10-29). Note `code` is
a **string**, so clients compare against `"200"`/`"204"` or letter codes, never numbers.
ID-bearing DTOs type identifiers as `string` per `openspec/specs/api/spec.md` § ID
Serialization (snowflake bigints lose precision as JS numbers). No endpoint constant is
declared - `specs/web-app-shell` § API Client Seam Without Invented Endpoints forbids paths
that the spec does not yet define. The seam is deliberately unpopulated.

### D5 - Mock tool deferred and logged as debt
`lumen-web/CLAUDE.md` mandates mock-first development, but the handoff doc (§10) lists the
frontend mock approach as **unselected**. Choosing between MSW (network-level interception,
works in tests and browser), `vite-plugin-mock-dev-server` (dev-server middleware), and mockjs
(data-shape mocking only) is a real tradeoff that deserves its own decision, and none of it can
be exercised here because no endpoint exists yet in `openspec/specs/api/spec.md`. This change
creates the `src/api/` seam the mock layer will plug into and records the gap in `debt.md` with
a revisit trigger. Deciding now would mean picking a tool with nothing to mock.

### D6 - Theming through ConfigProvider tokens, primary is not red
A single `ConfigProvider` supplies AntD 5 theme tokens, defined in `src/theme/tokens.ts`
(a first-class module directory per the Module Layout requirement - user confirmed
2026-10-29). The primary token is a cool, non-alarming hue (blue/teal family) so red stays
available for clinical warnings, per `lumen-web/CLAUDE.md` and `specs/web-app-shell` §
Design Token Theming. Component styling reads tokens; no inline hex.

### D7 - Department routing pattern established by one placeholder
`react-router-dom` data router with the route table in `src/router.tsx` (standalone file,
user confirmed 2026-10-29) and a lazy-loaded `radiology/` route module under `src/pages/`,
wrapped in the shell layout. The placeholder renders an explicit "not yet implemented" state and
issues no request (`specs/web-app-shell` § Placeholder Routes Declare Their State). `ultrasound/`
and `endoscopy/` later copy this exact shape.

## Risks / Trade-offs

- **Version drift off the constrained stack.** `pnpm install` with loose ranges would pull
  React 19 / AntD 6. → Exact pins in `package.json` plus a committed `pnpm-lock.yaml`; the lock
  is the reproducible artifact.
- **Vite 7 approaches end of active support while Vite 8 is current.** → Accepted for the
  skeleton; the engine floor and lock file keep the build reproducible, and the stack-upgrade
  proposal can move to Vite 8 + plugin-react 6 as one unit.
- **No test runner means regressions in the shell are only caught by `pnpm build`.** → Type
  errors are caught (strict build); behavioral regressions are not, which is why `debt.md` also
  flags the missing test runner. Adding Vitest is a separate change that flips
  `verify.config.yaml` L3 from SKIP to a gate.
- **The API seam is empty, so a contributor may invent an endpoint to get moving.** → The spec
  requirement forbids it and the proposal states it; review should reject any path not present
  in `openspec/specs/api/spec.md`.

## Migration Plan

Greenfield - nothing to migrate. Rollback is removing `lumen-web/`'s generated files
(`package.json`, lock, configs, `src/`), leaving the role files intact. If abandoned before
apply, `openspec/loop-state.yaml` returns to `phase: proposing`.

## Open Questions

None that affect specs, approach, or task breakdown. The mock tool selection (D5) is a genuine
future decision, logged in `debt.md`, not an unresolved question for this change - nothing here
depends on which tool is eventually chosen.
