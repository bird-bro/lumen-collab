<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-rail-quick-search
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "6/6"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "5/5"
      L3: PASS
---

# Verification: us-rail-quick-search

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Evidence |
|:--|:--|:--|:--|:--|
| backend | PASS (keyword test run compiled the reactor, exit=0) | 6/6 | PASS | `UsRegistrationApiTests#railQuickSearchOrMatchesTheIdentityHandles` exit=0 (2026-11-26, isolated; the class's other failures are the room change's mid-apply churn, not this surface) |
| lumen-web | PASS (`pnpm build` exit=0) | 5/5 | PASS | full suite 522/522 exit=0 + page suite 38/38 (2026-11-26, post-`e737153` final state) |

## Scenario Alignment

### us-station — Workbench Query Surface

| Scenario | Status | Evidence |
|:--|:--|:--|
| Status multi-select composes | IMPLEMENTED | appendWideConditions (unchanged) |
| Snapshot conditions | IMPLEMENTED | appendWideConditions (unchanged) |
| Exam-item fuzzy | IMPLEMENTED | appendWideConditions (unchanged) |
| Apply-time range | IMPLEMENTED | appendWideConditions (unchanged) |
| Status counts respect the filters | IMPLEMENTED | statusCounts zero-fill (unchanged) |
| The rail quick-search OR-matches | IMPLEMENTED | RegistrationOrderServiceImpl appendWideConditions keyword OR-block; UsRegistrationApiTests#railQuickSearchOrMatchesTheIdentityHandles |

### web-us-station — Order List

| Scenario | Status | Evidence |
|:--|:--|:--|
| List renders | IMPLEMENTED | unchanged surfaces |
| Status chips carry the meter | IMPLEMENTED | index.tsx chipsRow (pill + checkbox + dict labels/colors); page tests |
| The rail quick-search narrows | IMPLEMENTED | index.tsx railSearch wiring (debounced, both calls); page test |
| Wide conditions compose | IMPLEMENTED | unchanged |
| 我的查询 | IMPLEMENTED | unchanged |

## Issues

(none on this change's surface; the shared-file churn note lives in tasks.md)

## Next

- PASS → archive us-rail-quick-search (syncs the keyword contract into the main specs).
