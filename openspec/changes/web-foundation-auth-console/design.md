# Design

## Context

`lumen-web` is a Vite 7 + React 18 + TS + AntD 5 SPA whose scaffold ships an empty api seam
(`src/api/` holds only envelope/entity-id types and `BASE_PATH`), a hard-coded
`BasicLayout` nav (radiology/ultrasound/endoscopy), provisional teal theme tokens, and no test
runner. The backend (`lumen-system` + system-data) is implemented and archived as specs; the
visual target is the OPS reference web (`ops_web`, Vue3/Element Plus — style reference only,
never code). See proposal.md — Why. Constraints that shape the approach: hex literals confined
to the token module (`web-app-shell` § Design Token Theming); no invented endpoint paths
(§ API Client Seam); `/api/v1` proxy with `VITE_API_ENTRY_URL` override already configured in
`vite.config.ts`; exact-pinned deps.

## Goals / Non-Goals

**Goals:**
- OPS look-and-feel on the AntD 5 token system (palette, 18px scale, MiSans, layout metrics)
- A working auth session: login page → token → guarded routes → dynamic menu → logout
- The system console pages against a full mock of the documented `/api/v1/system/**` surface
- A wired test runner (Vitest) so the WHEN/THEN scenarios are TDD-able

**Non-Goals:**
- Real backend wiring (mock-first; the proxy target stays as-is)
- Backend code of any kind (cross-domain ban) — including the `roles/list` endpoint, declared
  here and implemented by a backend follow-up change
- Dark theme (OPS supports one; v1 ships light only)
- Departments' real pages (placeholders stay placeholders, now nav-driven)
- UI rework of the department placeholder content

## Decisions

- **D1 — Palette via AntD tokens, not a CSS-variable overlay.** `src/theme/tokens.ts` is
  rewritten with the OPS mapping (below); components keep reading `theme.useToken()`.
  Alternatives: an Element-Plus-style CSS-var layer (rejected — two theming systems to keep in
  sync; AntD 5's token pipeline is already the house rule), keep teal (user decision: full OPS
  adoption). Mapping: `--primary-color #3178fc` → `colorPrimary/colorInfo/colorLink`;
  `--mainnav-background-color #0f356a` / `--base-color #003676` → Sider/header backgrounds via
  `Layout` token group (`colorBgLayout`, sider tokens); `--form-border-color #d7dbe2` →
  `colorBorder`; `--table-th-bg #f0f3fa` → `Table.headerBg`; `--table-background-striped
  #f6f6f6` → `Table.rowHoverBg`/striped tokens; text `#333/#666/#999` → `colorText`,
  `colorTextSecondary`, `colorTextTertiary`. The tints (`--primary-color-rgba1..3`) map to
  `colorPrimaryBg`/`colorPrimaryBgHover`/`colorPrimaryBorder`.
- **D2 — Fonts: copy the three MiSans TTFs from `ops_web/dist/assets/ttf/` into
  `lumen-web/src/theme/fonts/`, loaded by a `fonts.css` with `@font-face` (Regular 400,
  Medium 500, Bold 700) and a `MiSans, PingFang SC, Microsoft YaHei, sans-serif` fallback
  stack, wired through the AntD `fontFamily` token.** MSYH.TTF is excluded (licensing).
  Alternative: woff2 subsetting (~80% smaller) — deferred; OPS ships plain TTFs and parity
  keeps the comparison honest. Trade-off: repo grows by the TTF size, accepted.
- **D3 — Mock tooling: `vite-plugin-mock-dev-server`.** File-based `mock/` tree mirrors the
  `/api/v1/system/**` paths (one file per resource), dev-only (no prod bundle), sits naturally
  in front of the existing proxy. Alternatives: MSW (service-worker machinery unneeded for a
  path-based mock and heavier in CI), `vite-plugin-mock` (unmaintained). Exact-pin a release
  whose peer range includes Vite 7; if none exists at install time, fall back to MSW and
  record the swap here. Fixtures mirror backend seeds: admin (uid 1), hospital root, three
  departments, `common_status` dict, role + menu fixtures for the console demos.
- **D4 — Transport client in the existing seam.** A small fetch wrapper in `src/api/`
  (no axios — the envelope types are already ours) joining `BASE_PATH`, injecting
  `Authorization: Bearer <token>`, unwrapping `ApiEnvelope`, and translating: `code !==
  "200"/"204"` → typed error carrying `code`+`msg`; `code === "203"` → session-clear +
  redirect signal. Pages never branch on mock vs real (spec § Dev Mock Server).
- **D5 — Session state: `zustand` (exact-pinned) with a localStorage-backed `auth` store**
  (token, profile, menu tree, perms set). Alternatives: React Context (re-render churn on a
  session object read by shell + guard + pages), redux (heavier than the problem). The empty
  `src/stores/` module exists for exactly this. Hydration happens at store creation; the
  guard reads the store, not localStorage, so tests can stub it.
- **D6 — Router: a `RequireAuth` wrapper element + `/login` route outside the shell.** On
  boot with a stored token, the shell fetches `/me` to refresh profile/menus (stale-token
  path exercises the `203` handling). `NAV_ITEMS` is deleted — the shell renders the menu
  tree from the store (directory → submenu, menu → link, button → ignored for nav). Console
  routes: `/system/orgs|deps|users|roles|menus|dicts|logs`; department routes unchanged;
  `/` redirects to the first menu entry the user's tree grants (admin fixtures: `/system/orgs`).
- **D7 — Login page:** OPS-pattern centered card — product title ("LumenCollab" + 影像协作平台
  subtitle), account + password fields with Enter-submit, primary button with loading state,
  `A`-class `msg` surfaced via `message.error`. OPS's privacy-agreement checkbox and
  forgot-password link are dropped: no such endpoint/product requirement exists here.
- **D8 — Console pages under `src/pages/system/`, one folder per resource
  (`orgs/ deps/ users/ roles/ menus/ dicts/ logs/`), AntD components only.** Org + department
  pages use the OPS left-aside (org `Tree`) + right work-area pattern; users/roles/dicts/logs
  are table pages (search form + `Table` + modal form). Roles page carries the menu-assignment
  tree dialog (`Tree` checkable, full-replace PUT). Menu page is a read-only `Tree`/`Table`
  hybrid showing `perms`. Duplicate-name/code and `A`-class failures surface via form message,
  per `web-system-console` scenarios.
- **D9 — Tests: Vitest + @testing-library/react + jsdom, `pnpm test` wired.** Unit tests
  stub the api seam with `vi.mock` (not the dev-server mock) and drive the spec scenarios:
  login success/failure, guard redirect, 203 clearing, menu-from-permission-set, one CRUD
  round-trip per console page, hex-discipline (a source-scan test asserting no hex literals
  outside `src/theme/`).

## Risks / Trade-offs

- [18px base enlarges dense AntD widgets (Table, Form)] → accept OPS's own trade-off (its EP
  base is 18px); per-component `size` props adjust where a screen demands density; revisit
  only with user feedback on the prototype.
- [Full-CJK TTFs grow the repo/bundle by ~2-3 MB per weight] → parity with OPS accepted;
  woff2 subsetting recorded as a future optimization, not this change.
- [`roles/list` is mock-only until the backend follow-up lands] → the roles page works in dev
  against the mock; the gap is declared in the api delta and flagged in the coordination
  ledger so the backend follow-up is trackable, not forgotten.
- [`vite-plugin-mock-dev-server` peer range may lag Vite 7] → verify at install; D3 names the
  MSW fallback so the decision cannot stall implementation.
- [`203` arrives as HTTP 200 with a body flag] → the transport client keys on the envelope
  `code`, never HTTP status; covered by a dedicated unit test.

## Migration Plan

Frontend-only plus spec deltas; no data migration. Deploy = the existing static build; fonts
are hashed assets. Rollback = revert the commit. The only external system touched is the OPS
repo (read-only font copy at implementation time).

## Open Questions

None — deferred items (woff2 subsetting, dark theme, backend `roles/list`) are recorded above
as non-goals or follow-ups and change nothing here.
