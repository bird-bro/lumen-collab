<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-apply-info-web
  date: 2026-11-25
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: us-apply-info-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS | 2/2 | PASS | logs/build-lumen-web.log · logs/test-lumen-web.log |

L1: `pnpm build` (tsc --noEmit ×2 + vite build) exit 0. L3: `pnpm test` — Vitest
36 files / **504 tests passed**, exit 0.

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| The origin trio submits with the ext block | IMPLEMENTED | three text inputs `index.tsx:1399` (申请科室) `:1404` (申请医生) `:1414` (申请时间, raw-text placeholder, no parsing) · save() ext block `:752-759` passes non-blank values verbatim · applyDeptId/applyDoctorId omitted from the v1 body (reserved anchors stay null) · list rows render none of the trio (the workbench's only applyTime* usages are the 创建时间 filter pair) |
| Blank origin fields submit as nulls | IMPLEMENTED | blank→null normalization `index.tsx:756-758` (`(values.applyDept ?? '').trim() \|\| null` per field) · test `us-registration-page.test.tsx:477-480` asserts `body.ext.applyDept/applyDoctor/applyTime` all null on the untouched form and the order still registers |

## Issues

- None.

## Next

- PASS → archive us-apply-info-web (batch with us-apply-info-sev, whose verify is also PASS).
