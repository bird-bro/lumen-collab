<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: number-rule-format-dict-sev
  date: 2026-11-19
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: number-rule-format-dict-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 2/2 | PASS | logs/build-backend.log · logs/test-backend.log |

- L1 `./mvnw compile -q` (JDK 11) exit=0.
- L3 full configured suite `./mvnw test -q` exit=0: 281 tests / 54 classes, 0 failures, 0 errors, 0 skipped (fresh surefire reports, exclusive shared-DB window verified via `pg_stat_activity`).

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Seeded formats are available by catalog id | IMPLEMENTED | `V43__number_stamp_format_dict.sql` catalog 1029 + details 1029001–1029006 (`yyMMdd`…`yyyyMMddHHmmss`), all enabled; asserted in `AuthSystemDataMigrationTests.java:393-430`. Consumers anchor by the stable id (frontend `dictItemsById(1029)` via `items-by-id/{catalId}`, `DictDetailController.java:114`); display name operator-editable — V43 seeded 「号码戳格式」, V45 lands the 「编号格式」 rename (live test DB confirms `编号格式`). |
| Migration replay is guarded | IMPLEMENTED | `V43__number_stamp_format_dict.sql:14-27` — `ON CONFLICT (id) DO NOTHING` on both inserts + `setval(MAX(id))` resync keeping both sequences past 1029/1029006; sequences asserted in `AuthSystemDataMigrationTests.java:422-429`. |

## History

- Round 1 FAIL: the seed test asserted the pre-rename catalog name (`号码戳格式`) which V45's rename (operator action 2026-11-19) invalidates; delta spec/proposal and the web change's artifacts still carried the stale name. Fixes (apply, 2026-11-19): test switched to the id-anchored contract (name deliberately unasserted — id-anchor ruling), all change artifacts + coordination doc synced to the id + final name.
- Round 2 PASS with one shared-DB data-drift red (`NumberRuleSeedTests.familyDefaultsAreSeeded`: seeded 「预约号」 row found `active=0` — operator toggle drift on the shared demo DB, backend tests never touch seeded rows). User ruling 2026-11-19: restore the seeded state via SQL → full-suite rerun green. Observation for a future ruling: a seed-proof test asserting live operator state on a shared demo DB will bite again on legitimate UI toggles.

## Next

- PASS → archive deferred to the coordination gate: batch-archive `number-rule-format-dict-sev` + `number-rule-format-dict-web` once the web change also holds a PASS credential.
