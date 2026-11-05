# Proposal: web-foundation-auth-console

## Why

The backend foundation is ready — `lumen-system` (login/RBAC) and system-data (dicts, logs) are
implemented, verified, and archived as specs — but the web SPA is still a bare scaffold:
placeholder routes, a hard-coded nav, and a provisional teal theme that was chosen before any
real UI direction existed. Frontend development must now start, and it should visually anchor
to the proven OPS reference UI (`ops_web`: MiSans, 18px font scale, deep-blue palette,
60px header + 160px sidebar layout) so the product inherits a field-tested look instead of an
invented one. The first deliverable that pairs with the ready backend is the authentication
flow plus the system console that administers what lumen-system already manages.

## What Changes

- **New design system (frontend)**: lumen-web adopts the OPS visual language — bundled MiSans
  Regular/Medium/Bold fonts, an 18px-based font scale (small 16 / base 18 / medium 20), and the
  OPS deep-blue palette (`#3178fc` primary family, `#003676` nav family) — replacing the
  provisional teal `#0E7C86` tokens. Hex literals stay confined to `src/theme/tokens.ts`;
  red remains reserved for clinical alarm semantics.
- **New auth flow (frontend)**: login page (OPS-style centered card), JWT session handling,
  route guard, dynamic navigation rendered from `GET /api/v1/system/me`'s permission set,
  button-level gating by `perms` strings, and client-side logout.
- **New system console (frontend)**: administration pages for what lumen-system already serves —
  org tree, departments, users, roles + menu assignment, menu tree, dictionaries, and
  operation logs — using the OPS pattern (left org-tree aside + right table area).
- **Dev mock server (frontend)**: every documented `/api/v1/system/**` endpoint is mocked so the
  console runs without the backend (mock-first rule); mocks live behind the existing
  `src/api/` seam and proxy boundary.
- **Spec sync — `api` capability**: documents the management endpoint surface lumen-system has
  already implemented (`users`, `orgs`, `org-deps`, `roles` + menu assignment, `menus/tree`).
  Also declares `GET /api/v1/system/roles/list`, which the role page needs but the backend does
  NOT yet implement — flagged as a backend follow-up (frontend mocks it per spec; backend
  implements to spec in a small follow-up change).
- **Spec revision — `web-app-shell` capability**: the Design Token Theming requirement
  supersedes the provisional teal decision with the OPS palette; a Dev Mock Server requirement
  and a small Module Layout clarification (platform pages besides department pages) are added.

User decisions 2026-11-03 (brainstorm): full OPS palette adoption (supersedes teal decision D6);
MiSans font files bundled (free license — NOT Microsoft YaHei, whose `MSYH.TTF` has licensing
restrictions); scope = theme foundation + login + system console.

## Capabilities

### New Capabilities

- `web-design-system`: The web SPA's visual language — bundled MiSans fonts, the 18px-based
  type scale, the OPS palette tokens, and the OPS layout metrics — owning token-only styling
  rules that all later pages inherit.
- `web-auth-flow`: The frontend authentication session — login page behavior, JWT session
  storage, route guarding (including envelope-code `203` handling), dynamic menus and button
  gating from `/me`, and logout.
- `web-system-console`: The system administration pages — org tree, departments, users, roles
  and role-menu assignment, menu tree, dictionaries, and operation logs — their interaction
  contracts with the api capability and their mock-first delivery.

### Modified Capabilities

- `api`: ADD per-resource endpoint requirements for the management surface lumen-system already
  implements (users, orgs, org-deps, roles + `PUT /{id}/menus`, menus tree) — a documentation
  sync of shipped code, required so `src/api/` clients declare no invented endpoints. Also ADD
  the not-yet-implemented `GET /api/v1/system/roles/list` role-list requirement the console needs.
- `web-app-shell`: MODIFY Design Token Theming (OPS deep-blue palette supersedes the
  provisional teal; font scale added; red-ban rule unchanged) and ADD a Dev Mock Server
  requirement; small Module Layout clarification covering platform pages (login, system
  console) alongside department route modules.

## Impact

- **Code — `lumen-web/` only**: `src/theme/` (tokens + font assets), `src/layouts/BasicLayout`
  (OPS metrics, dynamic menu), `src/api/` (auth + management clients, 203 interceptor),
  `src/stores/` (auth session), `src/router.tsx` (guard, routes), new `src/pages/login/` and
  `src/pages/system/**`, new `mock/` fixtures, `package.json` (mock dev-server plugin, Vitest +
  Testing Library dev-deps for TDD).
- **Specs**: `openspec/specs/api/` and `openspec/specs/web-app-shell/` modified (deltas in this
  change); three new `web-*` specs added on archive.
- **No backend code changes in this change** (cross-domain ban). One backend gap is surfaced:
  `GET /api/v1/system/roles/list` — declared by the api delta here, to be implemented by a small
  backend follow-up change; until then the mock serves it.
- **Dependencies**: `vite-plugin-mock-dev-server` (or equivalent) + `vitest`/
  `@testing-library/react` dev-deps, exact-pinned per house convention; MiSans TTFs copied from
  the OPS reference repo (`ops_web/dist/assets/ttf/`).
- **Fonts/licensing**: MiSans (Xiaomi) is free to redistribute and bundle; Microsoft YaHei
  (`MSYH.TTF` present in the OPS repo root) is NOT bundled.
