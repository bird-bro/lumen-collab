<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-apply-info-sev
  date: 2026-11-25
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: us-apply-info-sev

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 2/2 | PASS | logs/build-backend.log · logs/test-backend.log |

L1: `./mvnw compile -q` (explicit JDK 11) exit 0. L3: `./mvnw test -q` full reactor suite
exit 0 (quiet mode — surefire counts suppressed; exit code is the credential). Run window
was shared with the parallel session — no fixture-race signatures surfaced.

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Extension persists with the order | IMPLEMENTED | `RegistrationOrderServiceImpl.java:143-151` (ext rides the kernel RegistrationHook seam via UsRegistrationExtHolder thread-local) · `:305-315` batch ext re-query per page → `toView :459` explicit flattening · column set/type pins `UsRegistrationExtTests.java:44-106` |
| Apply origin persists and flattens onto the view | IMPLEMENTED | `V5__us_apply_info.sql:25-29` (5 nullable ADD COLUMN IF NOT EXISTS) · entity `UsRegistrationExt.java:52-64` · view `RegistrationOrderView.java:85-97` · `toView` copies applyDept/applyDoctor/applyTime + both reserved anchors verbatim, `ext == null` branch keeps view fields null · persist round-trip `UsRegistrationApiTests.java:113-115,177-184` |

## Issues

- None. (Note: the "absent ext → NULLs/null view" clause is proven by the code path
  (nullable columns + the `ext == null` guard in toView) and adjacent walk-in tests;
  no dedicated apply-NULL assertion exists — informational, not a gap against the
  WHEN/THEN as written.)

## Next

- PASS → archive us-apply-info-sev (batch with us-apply-info-web once its verify lands).
