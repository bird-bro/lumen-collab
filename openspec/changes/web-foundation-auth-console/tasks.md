# Tasks

## 1. Dependencies & Tooling

- [ ] 1.1 Add dev-deps exact-pinned (`vite-plugin-mock-dev-server`, `vitest`, `@testing-library/react`, `jsdom`, `@testing-library/jest-dom`) and runtime dep `zustand` to `lumen-web/package.json`; add `pnpm test` script and minimal vitest config (jsdom environment). Verify: `pnpm --dir lumen-web install` exits 0 and `pnpm --dir lumen-web build` stays green.

## 2. Design System Foundation (fonts, tokens, metrics)

- [ ] 2.1 Copy MiSans Regular/Medium/Bold TTFs from `../OPS/ops_web/dist/assets/ttf/` into `lumen-web/src/theme/fonts/`; add `fonts.css` with `@font-face` (400/500/700) and the MiSans-first fallback stack. Verify: files present, no MSYH file copied, `pnpm build` green.
- [ ] 2.2 TDD: source-scan test asserting no hex literals outside `src/theme/` (fails first). Rewrite `src/theme/tokens.ts` to the OPS palette (`#3178fc` primary family, `#003676`/`#0f356a` nav family, `#333/#666/#999` text, `#f0f3fa` table header, `#f6f6f6` striped row) + 18px `fontSize` + MiSans `fontFamily`; wire `fonts.css` and updated tokens through `ConfigProvider` in `src/App.tsx`. Verify: the new tests pass (token assertions + hex scan), `pnpm test` green.
- [ ] 2.3 Restyle `BasicLayout` to OPS metrics — 60px header, 160px Sider, deep-blue Sider via tokens. Component test asserts the rendered metrics and token-derived colors. Verify: test passes, shell visually matches OPS metrics.

## 3. Mock Server & Seed-Parity Fixtures

- [ ] 3.1 Wire `vite-plugin-mock-dev-server` into `vite.config.ts` (dev-only, in front of the proxy); create the `mock/` tree covering every documented `/api/v1/system/**` path (login, me, users+list+password+enable, orgs+tree, org-deps+list, roles+list+menus, menus/tree, dicts catal/detail, logs) in the standard envelope; fixtures mirror backend seeds (admin uid 1, hospital root, radiology/ultrasound/endoscopy deps, `common_status` dict, demo role + menu tree). Verify: dev server responds to `GET /api/v1/system/menus/tree` and `POST /api/v1/system/login` with envelope JSON; grep proves no undocumented path is mocked.

## 4. Transport Client & Auth Session State

- [ ] 4.1 TDD: fetch wrapper in `src/api/` — joins `BASE_PATH`, injects `Authorization: Bearer`, unwraps `ApiEnvelope`, typed error carrying `code`+`msg`, and a `203` signal that clears the session and redirects. Unit tests (no HTTP status reliance — 203 arrives as HTTP 200). Verify: wrapper tests pass.
- [ ] 4.2 TDD: `auth` store in `src/stores/` (zustand, localStorage persistence: token/profile/menu tree/perms set) with `login/clear` actions and hydration at store creation. Verify: store tests pass including persistence round-trip.
- [ ] 4.3 Typed clients for the auth surface per `openspec/specs/api/spec.md` (`POST /api/v1/system/login`, `GET /api/v1/system/me`) plus the management clients the console pages need. Verify: unit tests assert paths, payloads, envelope typing (IDs as numbers).

## 5. UI Prototype (frontend-design skill) & Auth Flow UI

- [ ] 5.1 Run the `frontend-design` skill to produce the UI prototype for the NEW pages (login card, system console shells: aside+tree pattern, table pages, assignment dialog); user confirms the prototype before page implementation. Verify: prototype artifacts delivered and user confirmation recorded.
- [ ] 5.2 TDD: login page per the confirmed prototype — centered OPS-style card, Enter submits, loading state, `A`-class `msg` via message. Tests: valid credentials route into shell; wrong password shows msg and stays; Enter triggers submit. Verify: tests pass.
- [ ] 5.3 TDD: `RequireAuth` guard + router restructure — `/login` outside the shell, `/system/**` console routes, `/` redirects to the first menu the user's tree grants, boot-with-stored-token refreshes via `/me`. Tests: unauthenticated redirect issues no API call; `203` clears session and redirects. Verify: tests pass.
- [ ] 5.4 TDD: dynamic navigation from the `/me` menu tree (NAV_ITEMS deleted; directory→submenu, menu→link), `perms`-gated action helper, logout action (clears session → login page). Tests: menu renders exactly the permission-set tree; ungated control hidden; logout returns to login. Verify: tests pass.

## 6. System Console Pages (each: client usage + page + tests)

- [ ] 6.1 Orgs page: tree from `/orgs/tree`, create-child/edit/delete round-trip, multi-root capable. Verify: CRUD round-trip test passes.
- [ ] 6.2 Departments page: OPS left org-aside + right list, list filters by selected org, CRUD round-trip. Verify: round-trip test passes.
- [ ] 6.3 Users page: paged list with search, create (duplicate username surfaces backend `msg`, dialog stays open), password reset, enable toggle. Verify: list/dup-feedback/action tests pass.
- [ ] 6.4 Roles page: list (mock `roles/list` per api delta), create/edit/delete, menu-assignment dialog — checkable tree, full-replace PUT, reopen shows exactly the saved set. Verify: role CRUD + full-replace tests pass.
- [ ] 6.5 Menus page: read-only directory/menu/button tree with `perms` visible. Verify: render test passes.
- [ ] 6.6 Dicts page: catalog list + CRUD with duplicate-`code` feedback; per-catalog details ordered by `sort`. Verify: catalog/detail tests pass.
- [ ] 6.7 Logs page: paged query with `type`/`uid`/`dateYm` filters, newest first, no mutation controls. Verify: filter/paging tests pass.

## 7. Integration Verification

- [ ] 7.1 Full suite: `pnpm --dir lumen-web test` and `pnpm --dir lumen-web build` both green in a clean install. Verify: both commands exit 0.
- [ ] 7.2 Mock smoke walkthrough: `pnpm dev` with no backend — login as seeded admin → shell renders OPS-look → walk each console page through its spec scenario. Verify: every `web-auth-flow`/`web-system-console` WHEN/THEN observed passing.
- [ ] 7.3 Cross-check the `web-design-system` scenarios (18px base, OPS primary, offline fonts, metrics) against the running app. Verify: all four requirement scenarios hold.

## Workflow follow-up

- Backend follow-up change: implement `GET /api/v1/system/roles/list` to the api delta scenario; then re-point roles page beyond the mock.
- `/opsx:verify` L2 against the WHEN/THEN scenarios; `/opsx:archive` when all registered changes verify PASS.
- Future optimization (non-goal here): woff2 font subsetting; dark theme parity.
