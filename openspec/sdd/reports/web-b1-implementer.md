# Implementer Report - init-web-skeleton Batches 1-2 (tasks 1.1-2.2), lumen-web

- **Status**: DONE
- **Commit**: `661aca61afb6313e7121c52976e56ebc688948b7` - `✨ feat(web): Batch 1-2 - pinned Vite+React18+AntD5 scaffold, strict TS, module layout (init-web-skeleton 1.1-2.2)` (15 files, 2291 insertions)
- **Environment**: Node v22.16.0, pnpm 10.32.1 (both as expected); registry.npmjs.org reachable

## 1. Pin table (requested vs resolved)

| Package | Requested (design D1 / brief) | Resolved (pnpm ls + pnpm-lock.yaml) | Match |
|:--|:--|:--|:--|
| `react` | 18.3.1 (exact) | 18.3.1 | yes |
| `react-dom` | 18.3.1 (exact) | 18.3.1 | yes |
| `antd` | 5.29.3 (exact) | 5.29.3 | yes |
| `react-router-dom` | 7.18.5 (exact) | 7.18.5 | yes |
| `vite` | 7.3.7 (exact) | 7.3.7 | yes |
| `@vitejs/plugin-react` | 5.2.0 (exact) | 5.2.0 | yes |
| `typescript` | 5.9.3 (exact) | 5.9.3 | yes |
| `engines.node` | `^20.19.0 \|\| >=22.12.0` (D2) | declared in package.json | yes |

No drift: install printed the newer majors as "(available)" notes only (react 19.3.1, vite 8.3.5, typescript 7.0.2, @vitejs/plugin-react 6.1.2) - none adopted, per D1 and the contract's blocking category.

### @types additions (beyond the brief's six - flagged as instructed)

| Package | Version | Rationale |
|:--|:--|:--|
| `@types/react` | 18.3.31 | Required by `tsc --noEmit` under `strict: true` - React types do not ship with react 18.x; latest 18.x line (kept off 19.x types to match the React 18 pin) |
| `@types/react-dom` | 18.3.7 | `createRoot` API in `src/main.tsx`; latest 18.x, peers `@types/react` ^18.3 satisfied by 18.3.31 |
| `@types/node` | 22.16.5 | `path`/`__dirname` in `vite.config.ts` (task 2.2 requires both); chosen on the 22.x line matching the verified runtime (v22.16.0) rather than newest 22.20.x or 26.x |

All three are exact-pinned like everything else (consistent with D1's no-drift rule).

## 2. Verification commands + exit codes

| # | Command | Exit | Evidence |
|:--|:--|:--|:--|
| 1 | `pnpm --dir lumen-web install` | 0 | lockfile `pnpm-lock.yaml` created and committed (2136 lines, 194 resolved packages) |
| 2 | `pnpm --dir lumen-web ls react antd vite typescript react-router-dom --depth 0` | 0 | exactly 18.3.1 / 5.29.3 / 7.3.7 / 5.9.3 / 7.18.5 |
| 3 | `node -e "JSON.parse(...package.json)"` + regex check | 0 | all six core deps match `^\d+\.\d+\.\d+$` (no `^`/`~`) |
| 4 | `grep -n '"strict"' lumen-web/tsconfig.json` | 0 | line 16: `"strict": true` |
| 5 | `pnpm --dir lumen-web exec tsc --noEmit` | 0 | strict clean on final tree |
| 6 | `pnpm --dir lumen-web build` | 0 | `tsc --noEmit && vite build` (vite 7.3.7); rerun twice, exit 0 both |
| 7 | `git check-ignore -v lumen-web/node_modules lumen-web/dist` | 0 | `.gitignore:1:node_modules/`, `.gitignore:2:dist/`; neither path in `git status --porcelain` |

### Task 2.2 alias proof (value import, not side-effect)

- `src/alias-probe.ts` with `import { API_SEAM_PLACEHOLDER } from '@/api'` (temp export added to `src/api/index.ts`): `tsc --noEmit` exit **0**; `pnpm build` exit **0** with the `@/api` import in the bundler graph (rollup resolves vite's `resolve.alias`; an unresolved alias would fail the build).
- **Negative control**: same value import from `@/no-such-module` -> `error TS2307: Cannot find module '@/no-such-module'`, tsc exit **2** - proves the pass in the previous step is a real resolution, not a vacuous one.
- Probe file and temp export **removed** afterwards; final `tsc --noEmit` + `pnpm build` re-run clean (exit 0).

### Module layout (task 2.1)

All six directories exist under `lumen-web/src/`: `api/`, `components/`, `stores/`, `layouts/`, `theme/` each with a placeholder `index.ts` (doc comment + `export {}`); `pages/` has only a `.gitkeep` (no module yet - per brief, noted here as agreed). Batch 3 populates `api/`; Batch 5 populates `layouts/`, `theme/`, `pages/`.

## 3. Files delivered

`lumen-web/package.json`, `pnpm-lock.yaml`, `tsconfig.json`, `tsconfig.node.json`, `vite.config.ts`, `index.html`, `src/main.tsx`, `src/App.tsx`, `src/vite-env.d.ts`, `src/{api,components,stores,layouts,theme}/index.ts`, `src/pages/.gitkeep`. Root `.gitignore` untouched (already covers `node_modules/`, `dist/`, `*.log` - task 1.5 verify passed without changes; nothing duplicated).

## 4. Deviations / notes (with reasons)

1. **First alias probe was vacuous - caught and redone.** A side-effect-only `import '@/api'` exits 0 even for a bogus path: TypeScript 5.9 does not error on unresolvable side-effect imports (bundler-resolved assets like CSS are the rationale). The negative control exposed this, so the proof was redone with a value import as described above. Final evidence stands on the value-import pass + TS2307 failure control.
2. **No `"type": "module"` in package.json** (deliberate omission, not an oversight): the brief mandates `resolve.alias: { "@": path.resolve(__dirname, "src") }`; `__dirname` requires the CJS default. Proven working - vite 7.3.7 loads the .ts config and builds (exit 0).
3. **`tsconfig.json` checks `src/` only; `vite.config.ts` is covered by `tsconfig.node.json`** (standard Vite split per the brief). Consequence: the `tsc --noEmit` gate does not type-check `vite.config.ts` itself; its correctness is proven by `vite build` succeeding. No strictness weakening - all five strict flags are on in `tsconfig.json`, `strict: true` also in `tsconfig.node.json`; `skipLibCheck` only (explicitly allowed).
4. **pnpm warning "Ignored build scripts: esbuild@0.28.2"** - pnpm 10 default behavior (no approval prompt in CI-style runs). Harmless: esbuild's platform binary ships via the `@esbuild/darwin-arm64` optional dependency, proven by two successful production builds. No `pnpm approve-builds` state committed.
5. **pnpm printed "(X is available)" newer-major notes** - informational only; lock file and exact pins keep resolution at the D1 versions.
6. **Scope discipline**: no ConfigProvider/theme/router/proxy wired (Batches 4-5), no endpoint path anywhere in `src/`, no test runner added. `openspec/sdd/progress.md` (modified by the orchestrator before my run) left untouched; the report file itself is uncommitted per the commit list in the brief.

## 5. Out-of-batch observations for the reviewer (no action taken)

- Task 2.1's tasks.md wording wants directories "tracked and imported"; the placeholder `index.ts` files are tracked but nothing imports them yet - the alias proof was done with a temporary import (now removed). Import wiring arrives with real content in Batches 3-5.
- `useDefineForClassFields: true` and `isolatedModules: true` included per standard Vite template conventions (no classes exist yet; harmless).
