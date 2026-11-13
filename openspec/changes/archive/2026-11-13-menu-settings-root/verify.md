<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: menu-settings-root
  date: 2026-11-13
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "3/3"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "3/3"
      L3: PASS
---

# Verification: menu-settings-root

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 3/3 | PASS | logs/build-backend.log · logs/test-backend.log |
| lumen-web | PASS | 3/3 | PASS | logs/build-lumen-web.log · logs/test-lumen-web.log |

- L3 backend: `./mvnw test -q` exit=0 — surefire: lumen-system 49 classes / 243 tests,
  common 1 class / 4 tests; 0 failures, 0 errors.
- L3 web: `pnpm test` exit=0 — 31 files / 362 tests passed.
- L2 note: all three delta scenarios are `system-data` (backend-owned, cross-domain rule);
  the web stack's obligations inside them are the icon-name rendering (`exam` / `temp` /
  `tune` resolve in the console) — evidenced by `MENU_ICON_BY_NAME` wiring, no route/logic
  change (routes resolve via the `component` column map).

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Fresh deployment seeds the exam-setting console group | IMPLEMENTED | V30__menu_settings_root.sql:30-38 (root rename + guarded L1 insert); V32__menu_settings_groups.sql:23-45 (guarded group inserts) + 106-135 (pages re-parent BY PERMS); icon chain V31/V33/V34; AuthExamSettingSeedTests (group/icon/path/perm assertions); AuthLoginTests.java:194 (root title 系统) |
| Migrated database keeps non-admin grants alive | IMPLEMENTED | V30__menu_settings_root.sql:41-53 (binding carry-over to the L1 row); V32__menu_settings_groups.sql:49-75 + 77-102 (per-group DISTINCT carry-over); TreeUtils orphan rule preserved |
| Re-execution is a no-op | IMPLEMENTED | AuthExamSettingSeedTests.java:198-216 (`seedReExecutionIsARowCountNoOp`, V28→V34 tail, live-row no-op); settingsGroupsSeededWithIcons asserts exactly 2 live group rows after V32/V33 re-run; setval tails V30:84-87 / V32:150-153 |

## Issues

None blocking. One documentation fix applied during verify (before spec sync): three
stale "V28→V32" tail references (proposal.md, design.md D7, spec scenario 3) updated
to V28→V34 to match the test's actual tail as extended by task 1.6.

## Next

- PASS → archive menu-settings-root.
