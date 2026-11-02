# Implementer Report - init-web-skeleton Batch 6 (tasks 6.1-6.4), lumen-web

- **Status**: DONE
- **Commit**: `65b163a` - `📝 docs(openspec): Batch 6 - debt/spec hygiene verified, B5 report correction, registry updated (init-web-skeleton 6.1-6.4)` (4 files: `debt.md`, `tasks.md`, coordination doc, `web-b5-implementer.md` addendum)
- **Environment**: Node v22.16.0, pnpm 10.32; no test runner per `verify.config.yaml` (`test: null` -> L3 SKIP) - verification is the strict TS build + inspection commands (same method as B1-B5).

## 1. Per-task verification table

| Task | Verification | Result |
|:--|:--|:--|
| 6.1 | `grep -c "\[DEBT\]" openspec/changes/init-web-skeleton/debt.md` | **3** (>= 2 required); all three entries name a revisit trigger (first concrete api-spec endpoint / Vitest added / stack-upgrade proposal) |
| 6.1 | Debt claims vs built reality | Entry 1: D5 citation confirmed (design.md lines 78-84); `src/api/` seam typed but endpoint-free, zero mock layer - one stale phrase fixed (see §2). Entry 2: `verify.config.yaml` `lumen-web.test: null` confirmed; L3 SKIP recorded. Entry 3: pins `vite 7.3.7` / `@vitejs/plugin-react 5.2.0` confirmed in `package.json`; registry re-checked live today - `vite` latest **8.3.5**, `@vitejs/plugin-react` latest **6.1.2** with peer `vite: '^8.0.0'` - "registry latest is a major ahead" and "plugin-react 6.x peers Vite ^8 only" both still true; risk levels (med/med/low) and triggers all still accurate |
| 6.2 | Port table vs `vite.config.ts` | Match, no drift: doc says nginx **80/443** (only inbound) and `lumen-web` dev server **5173**; `vite.config.ts` sets no `server.port` (Vite default **5173**) and the `/api/v1` proxy target default is `http://localhost:80` (nginx entry) |
| 6.2 | Registry row | frontend `init-web-skeleton`: `proposing` -> **`applied (verify pending)`**; backend row untouched (`verified`) |
| 6.2 | Traceability line added | `chunkFileNames` note under the registry (see §3) |
| 6.2 | Mutation-control line added | Envelope mutation control QUEUED note under the registry (see §3) |
| 6.3 | L1 gate: `pnpm --dir lumen-web build` | **exit 0** (tsc --noEmit + tsc -p tsconfig.node.json --noEmit + vite build, 1452 modules, 1.10s); L3 **SKIP** (`test: null`) |
| 6.4 | `openspec validate init-web-skeleton --strict` | **exit 0** - `Change 'init-web-skeleton' is valid`, no errors, no warnings |
| 6.4 | `python3 openspec/validate-artifacts.py openspec/changes/init-web-skeleton` | **exit 0** - `All artifacts valid` |

## 2. Drift found and fixed (task 6.1)

One stale phrase in debt entry 1 (mock tool unselected): it said the change "delivers only the
**empty** `src/api/` seam". Built reality after B3/B4: the seam is typed and populated with
`ApiEnvelope` + `BASE_PATH` + `EntityId` (tasks 3.1-3.2), while remaining endpoint-free and
consumer-free. Reworded to "Batches 1-5 delivered only the typed `src/api/` seam
(`ApiEnvelope` + `BASE_PATH` + `EntityId` - no endpoints, no mock layer, zero consumers)."
Risk level (med) and revisit trigger (first concrete endpoint in `openspec/specs/api/spec.md`)
were re-verified and are unchanged. No new debt entries added (per brief: nothing recorded
elsewhere was duplicated). `lumen-web/CLAUDE.md` untouched (mock-first mandate stays in force).

## 3. B5 follow-ups folded in

1. **Report correction** - appended a `## Reviewer correction addendum` section to
   `openspec/sdd/reports/web-b5-implementer.md` (original deviation note (e) left unedited):
   the 514 kB antd chunk warning is **not** pre-existing - the B5 reviewer rebuilt base commit
   `3cfacbe` in a detached worktree at **142.78 kB**, no antd import, no warning; the warning is
   new in B5 because B5 is the first batch to import antd; benign, vendor splitting deferred.
2. **Traceability** - one note under the coordination doc's Change Registry:
   `vite.config.ts` gained `build.rollupOptions.output.chunkFileNames` in Batch 5 (commit
   `d00617a`), an evidence-enabler deriving `assets/pages/<department>/` output paths for
   greppable lazy department chunks; B5 review accepted as not scope creep.
3. **Mutation control carry-over** - one note under the registry: the type-level negative
   control on `ApiEnvelope` (TS2322 red probe) remains **QUEUED** for the first endpoint-owning
   change (B3+B4 review carry-over); vacuous by design while `src/api/` has zero envelope
   consumers.

## 4. Strict validation output (verbatim)

```
$ openspec validate init-web-skeleton --strict
Change 'init-web-skeleton' is valid
(exit 0 - no errors, no warnings)

$ python3 openspec/validate-artifacts.py openspec/changes/init-web-skeleton
  All artifacts valid
(exit 0)
```

## 5. Build gate output (task 6.3, verbatim tail)

```
vite v7.3.7 building client environment for production...
✓ 1452 modules transformed.
dist/index.html                                   0.33 kB │ gzip:   0.24 kB
dist/assets/pages/radiology/index-BSJ5nlUU.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/endoscopy/index-DiqSTr0i.js     0.28 kB │ gzip:   0.23 kB
dist/assets/pages/ultrasound/index-iBpYCQ-h.js    0.29 kB │ gzip:   0.23 kB
dist/assets/index-BH0CyMoV.js                    35.67 kB │ gzip:  10.85 kB
dist/assets/index-ITPoENaJ.js                   514.52 kB │ gzip: 168.97 kB
(!) Some chunks are larger than 500 kB after minification. ...
✓ built in 1.10s
(exit 0)
```

The 500 kB warning is the antd vendor chunk (per the corrected B5 addendum: new in B5, first
antd import, benign, splitting deferred). L1 **PASS**; L3 **SKIP** (`test: null`, recorded in debt).

## 6. Commit scope notes

Staged exactly: `openspec/changes/init-web-skeleton/debt.md`,
`openspec/changes/init-web-skeleton/tasks.md` (6.1-6.4 ticked),
`openspec/coordination/init-project-skeleton.md`,
`openspec/sdd/reports/web-b5-implementer.md` (first-time tracked; required to carry the
committed addendum). `openspec/loop-state.yaml` NOT committed (per brief). Left uncommitted as
out of scope: `openspec/sdd/progress.md` (orchestrator's tracker) and
`openspec/sdd/reports/web-b34-implementer.md` (untracked, from Batch 3-4) - both remain in the
working tree for the orchestrator's closing pass.
