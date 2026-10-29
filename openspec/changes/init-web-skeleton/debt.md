---
change: init-web-skeleton
---
# Technical Debt Log

Legacy code changed without a full test harness. Each entry MUST be revisited when a test
runner is added to its stack. Linked from audit H-legacy check.

- [DEBT] Frontend mock tool unselected: `lumen-web` has no test runner; `lumen-web/CLAUDE.md`
  mandates mock-first development against `openspec/specs/api/spec.md`, but the choice between
  MSW, `vite-plugin-mock-dev-server`, and mockjs is deferred (handoff doc §10 lists it as
  undecided; `design.md` D5 explains why deciding now is premature - no endpoint exists yet to
  mock). This change delivers only the empty `src/api/` seam. Risk **med**: without a mock layer
  a contributor may invent an endpoint path to make progress, which
  `specs/web-app-shell` § API Client Seam Without Invented Endpoints forbids. Revisit **as soon
  as `openspec/specs/api/spec.md` gains its first concrete endpoint** - that change should select
  and wire the mock tool before writing any client against the seam.

- [DEBT] No test runner for `lumen-web`: `openspec/verify.config.yaml` sets `test: null` for this
  stack, so `/opsx:verify` reports L3 **SKIP** rather than exercising behavior. Every task in
  `tasks.md` is therefore verified by the TypeScript strict build or by inspection, which catches
  type errors but not behavioral regressions in the shell layout, router, theming, or proxy
  config. Risk **med**: the shell's observable behavior (lazy route splitting, placeholder
  rendering no request, token-based primary color) is asserted at review time only. Revisit
  **when Vitest is added** - that change must also flip `verify.config.yaml` `lumen-web.test` from
  `null` to `pnpm test` so L3 becomes a real gate, and must backfill tests for the scenarios in
  `specs/web-app-shell/spec.md`.

- [DEBT] Vite pinned one major behind current: `design.md` D1 pins Vite 7.3.7 with
  `@vitejs/plugin-react` 5.2.0 because plugin-react 6.x peers Vite `^8` only. The registry
  `latest` for both is a major ahead. Risk **low**: the committed `pnpm-lock.yaml` keeps the build
  reproducible and Vite 7 still satisfies the Node engine floor. Revisit **in the stack-upgrade
  proposal** that moves React 18 -> 19 and AntD 5 -> 6, so Vite 8 + plugin-react 6 upgrade as one
  tested unit rather than piecemeal.
