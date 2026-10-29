# Tasks

> This stack has no test runner (`verify.config.yaml`: `test: null` -> L3 SKIP), so each task's
> verification is the TypeScript strict build or a direct inspection command. Adding Vitest is a
> separate change; it is logged in `debt.md`.

## 1. Project scaffold and pinned toolchain

- [ ] 1.1 Create `lumen-web/package.json` with exact (non-caret) pins per `design.md` D1:
  `react`/`react-dom` 18.3.1, `antd` 5.29.3, `vite` 7.3.7, `@vitejs/plugin-react` 5.2.0,
  `typescript` 5.9.3, `react-router-dom` 7.18.5; scripts `dev`, `build`, `preview`; and an
  `engines.node` range of `^20.19.0 || >=22.12.0` (D2). Verify: `node -e
  "JSON.parse(require('fs').readFileSync('lumen-web/package.json'))"` exits 0 and shows no `^` or
  `~` on those six dependencies.
- [ ] 1.2 Run `pnpm --dir lumen-web install` and commit the generated `pnpm-lock.yaml`. Verify:
  install exits 0, and `pnpm --dir lumen-web ls react antd vite typescript --depth 0` reports
  exactly 18.3.1, 5.29.3, 7.3.7, and 5.9.3.
- [ ] 1.3 Create `tsconfig.json` / `tsconfig.node.json` with `strict: true`, `noUnusedLocals`,
  `noUnusedParameters`, `noFallthroughCasesInSwitch`, and bundler module resolution. Verify:
  `pnpm --dir lumen-web exec tsc --noEmit` exits 0 on the scaffold, and
  `grep -n '"strict"' lumen-web/tsconfig.json` shows `true`.
- [ ] 1.4 Create `vite.config.ts`, `index.html`, and `src/main.tsx` entry. Verify:
  `pnpm --dir lumen-web build` exits 0.
- [ ] 1.5 Extend `.gitignore` for Node (`node_modules`, `dist`, `*.local`). Verify:
  `git check-ignore -v lumen-web/node_modules` matches a rule and no `node_modules` path appears
  in `git status --porcelain`.

## 2. Module layout

- [ ] 2.1 Create `src/api/`, `src/pages/`, `src/components/`, `src/stores/`, `src/layouts/`,
  and `src/theme/` with an index or placeholder module in each so the directories are tracked
  and imported. Verify: all six directories exist under `lumen-web/src/` and
  `pnpm --dir lumen-web build` exits 0.
- [ ] 2.2 Add path aliases (`@/api`, `@/pages`, `@/components`, `@/stores`, `@/layouts`) in both
  `tsconfig.json` and `vite.config.ts` so they resolve identically for type checking and bundling.
  Verify: a module importing via `@/api` compiles - `pnpm --dir lumen-web exec tsc --noEmit`
  exits 0.

## 3. API client seam (typed, no endpoints)

- [ ] 3.1 Define the envelope type in `src/api/` as
  `interface ApiEnvelope<T> { timestamp: string; code: string; msg: string; data: T | null }`
  plus a `BASE_PATH = '/api/v1'` constant, exporting both from the module index (design D4).
  Verify: `pnpm --dir lumen-web exec tsc --noEmit` exits 0 and the envelope's four fields match
  `openspec/specs/api/spec.md` (OPS `R` contract - `code` is a string, not a number).
- [ ] 3.2 Add an `EntityId` type alias to `string` in `src/api/` for snowflake identifiers, with
  a doc comment citing the API spec's ID Serialization requirement. Verify: the alias is
  `string` (not `number`) - `grep -n "EntityId" lumen-web/src/api/*.ts` shows the string alias.
- [ ] 3.3 Assert no endpoint path was invented. Verify:
  `grep -rn "api/v1/" lumen-web/src --include=*.ts --include=*.tsx` returns only the
  `BASE_PATH` constant, with no resource-specific path.

## 4. Dev-server gateway proxy

- [ ] 4.1 Configure the Vite dev server to proxy `/api/v1` to
  `process.env.VITE_API_ENTRY_URL ?? 'http://localhost:80'`, the **nginx unified entry** port
  from `openspec/coordination/init-project-skeleton.md` (design D3; no Java gateway exists).
  Verify: `vite.config.ts` contains a `server.proxy` entry for `/api/v1` and the target reads
  from the env var with the port-80 default.
- [ ] 4.2 Confirm no service host or port is hard-coded in source. Verify:
  `grep -rnE "localhost:80[0-9][0-9]|127\.0\.0\.1" lumen-web/src lumen-web/vite.config.ts`
  returns at most the nginx-entry default in `vite.config.ts`, and nothing under `src/`.
- [ ] 4.3 Start the dev server and confirm the proxy is registered. Verify: `pnpm --dir lumen-web
  dev` starts without error and the startup output or a request to `/api/v1` shows the proxy
  forwarding to the nginx entry on port 80 (a 502/ECONNREFUSED is acceptable - the backend need
  not be running; what is asserted is the forwarding target, not a successful response).

## 5. Shell, theming, and department routing

- [ ] 5.1 Create the shell layout in `src/layouts/` (navigation + content region) and render it
  from `src/App.tsx`. Verify: `pnpm --dir lumen-web build` exits 0 and the layout is the app's
  root render.
- [ ] 5.2 Add a single AntD 5 `ConfigProvider` theme with design tokens whose primary color is
  in the blue/teal family - **not red** (design D6). Verify: the theme token definition contains
  no red primary value, and
  `grep -rnE "#(f5[0-9a-f]{4}|ff[0-9a-f]{4}|e[0-9a-f]{5})" lumen-web/src` finds no red primary
  token; component styling references tokens rather than inline hex.
- [ ] 5.3 Wire `react-router-dom` data router with the route table in `src/router.tsx`
  (standalone file, design D7) and a lazily-loaded `radiology/` route module
  under `src/pages/`, nested in the shell layout. Verify: `pnpm --dir lumen-web
  build` exits 0 and the build output shows a separate chunk for the lazily-loaded radiology
  module.
- [ ] 5.4 Make the radiology placeholder render an explicit "not yet implemented" state issuing
  no network request. Verify: the module contains no fetch/axios/API call and no path under
  `/api/v1`, and renders the placeholder text.
- [ ] 5.5 Add the `ultrasound/` and `endoscopy/` route modules as the same lazy placeholder
  shape, so the department pattern is proven by more than one instance. Verify: both exist under
  `src/pages/`, both are lazy-loaded, and `pnpm --dir lumen-web build` exits 0.

## 6. Debt and spec hygiene

- [ ] 6.1 Confirm `debt.md` records both deferred decisions - the unselected mock tool and the
  absent test runner - each with its risk level and revisit trigger (design D5). Do not edit
  `lumen-web/CLAUDE.md`: the role file's mock-first mandate stays in force and the gap belongs in
  the debt log, not in a weakened rule. Verify:
  `grep -c "\[DEBT\]" openspec/changes/init-web-skeleton/debt.md` returns at least 2, and each
  entry names a revisit trigger.
- [ ] 6.2 Update `openspec/coordination/init-project-skeleton.md`: confirm the API entry port the
  proxy targets matches the doc's port table, and set this change's registry row to its current
  status. Verify: the port table says 80/443 for the nginx entry and `vite.config.ts` defaults to
  that same port.
- [ ] 6.3 Run the L1 gate from `openspec/verify.config.yaml`. Verify: `pnpm --dir lumen-web
  build` exits 0 (L1 PASS); L3 is SKIP because `test: null`.
- [ ] 6.4 Run `openspec validate init-web-skeleton --strict`. Verify: exit 0 with no errors and
  no warnings.

## Workflow follow-up

- Generate and approve the execution contract (`build-contract.sh`), pass
  `guard.sh proposing applying`, then run `/opsx:apply`.
- Apply **after** `init-backend-skeleton` - `openspec/loop-state.yaml` tracks one active change
  and one `artifacts_hash` at a time (see coordination doc Decisions).
- After apply, run `/opsx:verify` against the WHEN/THEN scenarios in `specs/`.
- Archive this change only once **both** registered changes verify PASS, per the coordination
  doc's Gate section.
