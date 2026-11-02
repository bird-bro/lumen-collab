# Implementer Report - init-web-skeleton Batch 5 (tasks 5.1-5.5), lumen-web

- **Status**: DONE
- **Commit**: `d00617a7d2c4e4e9b3db41d3ee2dc17afd5da160` - `✨ feat(web): Batch 5 - shell layout, token theming (non-red), lazy department routes (init-web-skeleton 5.1-5.5)` (11 files, 227 insertions, 16 deletions; only `lumen-web/**` staged, no `openspec/` files committed)
- **Environment**: Node v22.16.0, pnpm 10.32; no test runner per `verify.config.yaml` (`test: null` -> L3 SKIP), so verification is the strict TS build + inspection commands (same method as B1-B4). "Red" evidence captured pre-implementation: `grep -rn colorPrimary lumen-web/src` -> no match; `src/router.tsx` missing; `src/pages/` contained only `.gitkeep`; no `BasicLayout` anywhere.

## 1. Deliverables (tasks 5.1-5.5)

| Task | File(s) | What |
|:--|:--|:--|
| 5.1 | `src/layouts/BasicLayout.tsx`, `src/layouts/index.ts` | AntD 5 `Layout` (`Sider` + `Content`) with navigation Menu (Radiology/Ultrasound/Endoscopy -> `/radiology`, `/ultrasound`, `/endoscopy`), `<Outlet />` for routed content, wrapped in a `Suspense` covering all lazy children. Exported via the layouts index; rendered as the parent route element from `src/router.tsx` |
| 5.2 | `src/theme/tokens.ts`, `src/theme/index.ts`, `src/App.tsx` | `themeTokens` (typed `ThemeConfig['token']`) consumed by a single `<ConfigProvider theme={{ token: themeTokens }}>` wrapping `<RouterProvider>` |
| 5.3 | `src/router.tsx`, `src/pages/radiology/index.tsx`, `vite.config.ts` | `createBrowserRouter` data router, three `React.lazy(() => import('@/pages/<dept>'))` children, index route redirecting `/` -> `/radiology` |
| 5.4 | all three `src/pages/<dept>/index.tsx` | Identical self-contained placeholder shape: `Result status="info"` with explicit "not yet implemented" wording; zero network activity |
| 5.5 | `src/pages/ultrasound/index.tsx`, `src/pages/endoscopy/index.tsx` | Same lazy placeholder shape as radiology - pattern proven by three instances, >= 3 lazy chunks in build output |

`src/pages/.gitkeep` deleted (real modules exist now). `src/main.tsx` unchanged.

## 2. Theme token value + why it is not red (task 5.2, design D6)

Chosen primary: **teal `#0E7C86`** (the value suggested by the execution brief).

Hue reasoning: in HSL, `#0E7C86` is `hsl(185, 81%, 29%)` - hue ~185 deg sits in the cyan/teal band of the wheel. Red-family hues sit at 0 deg/360 deg (and the near-red oranges/magentas at ~330-15 deg); 185 deg is roughly opposite the red band, so the resolved primary can never read as an alarm color. Red stays reserved for clinical alarm semantics per `lumen-web/CLAUDE.md` ("avoid red as a primary color - keeps alarm semantics for clinical warnings") and `specs/web-app-shell` § Design Token Theming. `colorInfo` and `colorLink` are set to the same teal so informational UI (the placeholder `Result status="info"` icon) and links stay in the brand family rather than falling back to AntD's default blue.

`src/theme/tokens.ts` is the only module in `src/` containing hex literals (V3 evidence below); components read tokens through `ConfigProvider` / `theme.useToken()` (BasicLayout derives text color, font size, paddings, and the Suspense fallback margin from `token.*` - no hex anywhere else).

```ts
export const themeTokens: ThemeConfig['token'] = {
  colorPrimary: '#0E7C86',
  colorInfo: '#0E7C86',
  colorLink: '#0E7C86',
}
```

## 3. Build gate + chunk evidence (tasks 5.3/5.5)

`pnpm --dir lumen-web build` (triple: `tsc --noEmit` + `tsc -p tsconfig.node.json --noEmit` + `vite build`) exit **0**. Verbatim vite chunk list:

```
dist/index.html                                   0.33 kB │ gzip:   0.24 kB
dist/assets/pages/radiology/index-BSJ5nlUU.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/endoscopy/index-DiqSTr0i.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/ultrasound/index-iBpYCQ-h.js    0.29 kB │ gzip:   0.23 kB
dist/assets/index-BH0CyMoV.js                    35.67 kB │ gzip:  10.85 kB
dist/assets/index-ITPoENaJ.js                   514.52 kB │ gzip: 168.97 kB
```

Chunk-name grep on the build log (exit 0, three matches = three separate department chunks):

```
$ grep -E "pages/(radiology|ultrasound|endoscopy)/" <build log>
dist/assets/pages/radiology/index-BSJ5nlUU.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/endoscopy/index-DiqSTr0i.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/ultrasound/index-iBpYCQ-h.js    0.29 kB │ gzip:   0.23 kB
```

Also confirmed on disk: `dist/assets/pages/{radiology,endoscopy,ultrasound}/index-*.js`.

**Supporting vite.config.ts addition (see Deviations a):** all three page modules are `index.tsx` (paths pinned by the brief), so Rollup's default names would be three indistinguishable `index-*.js` chunks and the required log grep for department names would find nothing. `build.rollupOptions.output.chunkFileNames` derives the `src/pages/<department>/` folder into the output path (`assets/pages/<department>/[name]-[hash].js`); all non-department chunks keep the default layout. Proxy and alias are untouched.

## 4. Grep evidence (verbatim)

```
$ pnpm --dir lumen-web exec tsc --noEmit            # V1
TSC EXIT: 0

$ grep -rnE "#(f5[0-9a-f]{4}|ff[0-9a-f]{4}|e[0-9a-f]{5})" lumen-web/src    # V2 - red family
(no output - exit 1)

$ grep -rniE "#[0-9a-f]{3,8}\b" lumen-web/src                              # V3 - all hex
lumen-web/src/theme/tokens.ts:6: * Primary is **teal `#0E7C86`**, not red: on the HSL wheel its hue is ~185 deg
lumen-web/src/theme/tokens.ts:20:  colorPrimary: '#0E7C86',
lumen-web/src/theme/tokens.ts:21:  colorInfo: '#0E7C86',
lumen-web/src/theme/tokens.ts:22:  colorLink: '#0E7C86',

$ grep -rnE "fetch|axios|XMLHttpRequest|api/v1|@/api" lumen-web/src/pages  # V4 - placeholder honesty
(no output - exit 1)

$ grep -rnE "8848|localhost|127\.0\.0\.1|:80[0-9]{2}|:3[0-9]{3}|:5[0-9]{3}|:6[0-9]{3}" lumen-web/src   # V5 - service ports
(no output - exit 1)
```

V3 shows the only hex literals in `src/` live in `src/theme/tokens.ts` (including its doc comment). V2 (the specified red-family pattern) matches nothing. V4 proves every placeholder is free of network/transport references and `@/api` imports - the seam still has zero consumers. V5 proves no `8848` or service-port/host string anywhere in `src/` (the nginx-entry default lives only in `vite.config.ts`, outside `src/`, per task 4.2).

Spec behavior coverage: 3 Component Style (AntD via props only, function components + hooks), 4 Module Layout (route table in `src/router.tsx`, lazy department route modules, single SPA), 5 API Seam (nothing consumed, nothing invented), 6 Unified Entry (no per-service host/port), 11 Design Token Theming (single `ConfigProvider`, non-red primary, no inline hex), 12/13 Placeholder Routes (explicit not-implemented state, no request) - all satisfied with the evidence above.

## 5. Deviations / notes

a. **vite.config.ts `chunkFileNames` added.** Required to make the brief's chunk evidence greppable (three pinned `index.tsx` modules would otherwise all emit `index-*.js`). Commented in-file; proxy/alias behavior unchanged. `tsconfig.node.json` strict check passes over it.
b. **Index route redirect `/` -> `/radiology`.** Not explicitly listed in the tasks, but the shell Menu needs a landing state; it navigates to the placeholder, which itself declares "not yet implemented", so no capability is implied (`Navigate replace`).
c. **Suspense placement** inside `BasicLayout`'s content region (one boundary covering all lazy children) rather than per-route element - the brief allows either; the shell owns its content-region loading state.
d. **Comment wording.** The first draft of each page's doc comment quoted the spec's "MUST NOT fetch data", which tripped the task's own honesty grep (the word `fetch` in a comment). Reworded to "MUST NOT request data"; the code itself never had any network activity.
e. **Pre-existing warning, not a regression:** vite flags the 514 kB main chunk (antd in the entry chunk). Departments are already dynamically imported per the spec pattern; splitting the antd vendor chunk is out of scope for this batch and belongs to a later performance pass.

---

## Reviewer correction addendum (appended Batch 6, 2026-11-02)

Deviation note (e) above ("Pre-existing warning, not a regression") is **corrected by the B5 review**: the 514 kB antd chunk warning is **not pre-existing**. The reviewer rebuilt the base commit `3cfacbe` in a detached worktree: it builds at **142.78 kB** with no antd import and **no chunk-size warning**. The warning is new in Batch 5 because Batch 5 is the first batch to import antd. It is benign - vendor chunk splitting remains deferred to a later performance pass, as note (e) itself states - but the "pre-existing ... not a regression" characterization is wrong and is superseded by this correction. The original note (e) is preserved above unedited.
