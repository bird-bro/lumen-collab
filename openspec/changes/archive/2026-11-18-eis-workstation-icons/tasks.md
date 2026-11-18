# Tasks — eis-workstation-icons

## 1. Backend seed (lumen-sev)

- [x] 1.1 RED: `AuthWorkstationSeedTests` — `departmentIconsRideTheSeed` asserts the
      three W rows' icons (`radiology`/`us`/`endo`); `iconMigrationReExecutesAsANoOp`
      re-executes the V36 statements (comment lines carry NO semicolons) proving the
      row count and icons survive. Run: fails (no V36 yet).
- [x] 1.2 GREEN: `V36__workstation_department_icons.sql` — three content-guarded
      UPDATEs on the V22 W rows (up_id=0, type='W', title=…). Two build-time catches
      fixed in place (2026-11-16): a comment-line semicolon (`route);`) severed the
      re-execution test's `;`-split (the house rule exists for this), and the V33-style
      `icon <>` guard is unknown-on-NULL — the V22 rows carry NULL icons, so the guard
      is `IS DISTINCT FROM`. The first (pre-fix) application hit the shared test DB;
      per the flyway-never-rewrite ruling the V36 history row was deleted and the
      repaired migration re-applied (3 rows affected, one per station).
- [x] 1.3 lumen-system test suite green — 49 classes / 246 tests, 0 failures, 0 errors
      (exit 0; log `logs/test-backend.log`; 4 stale surefire XMLs from renamed/deleted
      pre-2026-11-13 classes ignored — fresh-run reports only counted).

## 2. Frontend glyphs (lumen-web)

- [x] 2.1 RED: `src/test/ops-icons.test.tsx` — MenuIcon resolves the three new keys
      to 1024-viewBox svg glyphs; unknown/null names still ride the fallback.
      Run: 3 failed / 365 passed.
- [x] 2.2 GREEN: `ops-icons.tsx` — `RadiologyFilmIcon` (paipianl, fills stripped),
      `UltrasoundProbeIcon` (chaosheng), `EndoscopyCameraIcon` (shipincaiji, 0.85
      optical scale wrapper) — path data generated straight from the sprite and
      verified byte-identical; `BasicLayout.tsx` — MENU_ICON_BY_NAME `radiology`/`us`/
      `endo` + fallback comment refresh. One hex-discipline catch: the docstring named
      the stripped brand fill as a hex literal — reworded (the scanner counts comments).
- [x] 2.3 Frontend suite green + typecheck — 32 files / 368 tests (log
      `logs/test-lumen-web.log`); `pnpm build` exit 0 (log `logs/build-lumen-web.log`).

## 3. Verify

- [x] 3.1 Backend + frontend suites pass (counts + log paths above).
- [x] 3.2 User visual confirm on the running dev server (chip button + dropdown) —
      backend on :8081 must restart to apply V36 first.
