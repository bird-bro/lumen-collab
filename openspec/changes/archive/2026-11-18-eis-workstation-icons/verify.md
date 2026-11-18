---
verify:
  change: eis-workstation-icons
  date: 2026-11-18
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "5/5"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "-"
      L3: PASS
---

# Verification — eis-workstation-icons

Dual-stack change: backend V36 forward migration (lumen-system, auth-rbac delta) +
frontend ops-icons glyphs (lumen-web, no delta scenarios of its own — covered by
`ops-icons.test.tsx` in the green suite + task 3.2 user visual confirm). All five
delta scenarios are backend scenarios; scored against backend evidence only.

Pre-verify fix (2026-11-18): the delta's MODIFIED block omitted two scenarios the
main spec still carries ("Station access is role-driven", "Wrong machinery removed")
and held a placeholder body for "W nodes seeded and role-granted" — both copied
verbatim from `openspec/specs/auth-rbac/spec.md`, and the stale guard name in the
no-op scenario's THEN (`icon <>` → `IS DISTINCT FROM`, matching the shipped V36)
corrected. `openspec validate` now reports valid.

## Stacks

| Stack | Layer | Status | Detail |
|:---|:---|:---|:---|
| backend | L1 build | PASS | `./mvnw compile -q` (JDK 11); exit=0; logs/build-backend.log |
| backend | L2 spec | 5/5 | all IMPLEMENTED (below) |
| backend | L3 tests | PASS | `./mvnw test -q`; exit=0; 59 fresh surefire files / 318 tests / 0 failures+errors; logs/test-backend.log |
| lumen-web | L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| lumen-web | L3 tests | PASS | `pnpm test`; exit=0; 33 files / 392 tests green; logs/test-lumen-web.log |

## Scenario alignment (delta specs/auth-rbac § Workstation Nodes in the Menu Tree)

| Scenario | Status | Evidence |
|:---|:---|:---|
| W nodes seeded and role-granted | IMPLEMENTED | AuthWorkstationSeedTests.java:70-84 (`wNodesSeededAtL1WithAdminGrants`: three W rows at L1 `up_id=0` + admin role holds all three W grants via `role_menu`) |
| Station access is role-driven | IMPLEMENTED | mechanism tests: AuthRoleMenuTests.java:81-135 (`roleMenuAssignmentFlowsToUserPermsOnMe` — role_menu grant flows to the bound user's `/auth/me` tree + perms), :137 (`menuAssignmentIsFullReplace` — an ungranted node drops out on full-replace); W-node instantiation: AuthWorkstationSeedTests.java:78-83 |
| Wrong machinery removed | IMPLEMENTED | AuthWorkstationSeedTests.java:47-67 (`wrongMachineryTornDown`: `user_workstation` absent, `menu_info.workstation` column absent, `system:user:assign-ws` node + grants both zero) |
| Department icons ride the seed | IMPLEMENTED | V36__workstation_department_icons.sql:21-31 (three content-guarded UPDATEs keyed `up_id=0 AND type='W' AND title=… AND deleted=0`, `IS DISTINCT FROM` guard for the NULL-icon V22 rows), AuthWorkstationSeedTests.java:117-128 (`departmentIconsRideTheSeed` asserts `radiology`/`us`/`endo`) |
| Icon migration re-execution is a no-op | IMPLEMENTED | AuthWorkstationSeedTests.java:131-149 (`iconMigrationReExecutesAsANoOp` re-executes the V36 statements; W row count unchanged, icons survive), guard at V36 sql:23/27/31 makes every re-run a row-count no-op |

Frontend support (change scope, no delta scenario): ops-icons.tsx:145/160/178
(`RadiologyFilmIcon` / `UltrasoundProbeIcon` / `EndoscopyCameraIcon`, monochrome
currentColor EIS ports) + BasicLayout.tsx:113-118 (`MENU_ICON_BY_NAME` keys
`radiology`/`us`/`endo` with fallback intact).

## Issues

- None blocking. Remaining change task 3.2 (user visual confirm on the running dev
  server — chip button + dropdown) is the user's gate; note the local backend on
  :8081 must restart to apply V36 first.

Overall: **PASS** — 5/5 scenarios implemented, both stacks L1 + L3 green.
Next: user visual confirm (task 3.2), then `/opsx:archive eis-workstation-icons`.
