<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-registration-birthdate-web
  date: 2026-11-26
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "4/4"
      L3: PASS
---

# Verification: us-registration-birthdate-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS | 4/4 | PASS | logs/build-lumen-web.log, logs/test-lumen-web.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Birth date and age convert both ways | IMPLEMENTED | index.tsx:100 `diffYmd` (leap-day normalized, exported + fixed-oracle unit tests) → ageFieldsFromBirth derives 岁/月/天 on pick; index.tsx:1312 `onValuesChange` year-pins the picker (no-op on empty picker — no fabricated dates); page tests conversion + year-pin green |
| Matched identity backfills birth date and age | IMPLEMENTED | index.tsx:419 `fillFromIdentity` parse guard (valid + not-future, else no-op) backfills picker + converted age; MATCHED test green, future/malformed identity no-op test green |
| Create posts the birth date | IMPLEMENTED | index.tsx:784 submit formats `YYYY-MM-DD` / null; submit test asserts `1990-05-20` then `null` on the reset workbench |
| Future birth dates are refused | IMPLEMENTED | DatePicker `disabledDate` (index.tsx:1377-1383 block) greys future cells (panel test green); the programmatic path is guarded by the :419 parse/future guard (defense covers the disabledDate bypass) |

## L3 Notes

- Full Vitest run post-parallel-session window: 36 files, 516/516 green (exit 0)
  — includes this change's 8 birthdate tests plus the parallel session's new
  workstation tests, no interplay.
