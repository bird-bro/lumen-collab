<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: registration-order-status-dict-web
  date: 2026-11-26
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "8/8"
      L3: PASS
---

# Verification: registration-order-status-dict-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build` exit=0) | 8/8 | PASS (full suite 521/521 across two runs; 1 known load-pollution flake in an untouched page — isolated rerun green) | logs/build-lumen-web.log, logs/test-lumen-web.log |

## Scenario Alignment

### web-us-station delta — Order List

| Scenario | Status | Evidence |
|:--|:--|:--|
| List renders with state and identity | IMPLEMENTED | src/pages/us/registration/index.tsx (compact `.us-ocard` flow + wide table, unchanged surfaces riding the six-state seam); UsRegistrationApiTests web twin us-registration-page.test.tsx |
| Status chips carry the meter and multi-select | IMPLEMENTED | index.tsx:84 (six statuses), :1104+ (RAIL_HEAD_STATUSES chips in resolver order + `data-status-hue`); us-registration-page.test.tsx chips/multi-select tests |
| Wide conditions compose through the query row | IMPLEMENTED | unchanged wide query row; status IN-filter fed by the six chips |
| 我的查询 persists and auto-applies the default | IMPLEMENTED | unchanged search-filters wiring (out of this change's diff) |

### web-us-station delta — Registration Workbench Dict-Resolved Vocabularies

| Scenario | Status | Evidence |
|:--|:--|:--|
| Form selects render dict-driven options | IMPLEMENTED | unchanged 1004/1006/1023/1002 lookups; dict-items.ts resolvers |
| Pregnancy cell conditions on the dict female code | IMPLEMENTED | unchanged GENDER_FEMALE_CODE conditioning |
| Status chips resolve labels from catalog 1031 | IMPLEMENTED | dict-items.ts:151/164/214 (id-anchored resolver + the 103101–103106 bridge); index.tsx:496 (resolver effect), :944-955 (statusHue/statusLabel + pills); labels+colors asserted in us-registration-page.test.tsx |
| Dict failure degrades without blocking | IMPLEMENTED | index.tsx:496-507 (catch → one message + bridge-fallback meter); us-registration-page.test.tsx dict-failure test (fallback labels, hue-less, `状态字典加载失败`) |

## Issues

- L3 flake (non-blocking): the full-suite run carried 1 failure in `src/test/menus-page.test.tsx`
  ("用户权限" not found) — a page this change never touched, green at 521/521 during
  apply; isolated rerun 17/17 green (the recorded vitest load-pollution family:
  isolated定性 + 静机换绿).

## Next

- PASS → archive registration-order-status-dict-web (batch with the sev change per the
  coordination doc gate).
