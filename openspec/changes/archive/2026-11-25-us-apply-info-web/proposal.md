# Proposal: us-apply-info-web

> Cross-stack parent: `openspec/coordination/us-apply-info.md` (the shared design -
> token semantics, apply_time policy, form placement - lives THERE; this proposal
> carries the frontend implementation only).

## Why
请检信息 has no fields to record the exam application's HIS origin (申请科室/
申请医生/申请时间), so the origin is lost at registration time.

## What Changes
- Section 2 请检信息 gains three text inputs: 申请科室, 申请医生, 申请时间
  (placeholder `YYYY-MM-DD HH:mm:ss`), laid out as their own three-column row
  before 临床诊断.
- The create body's `ext` block carries `applyDept` / `applyDoctor` / `applyTime`
  (optional strings, blank-allowed); the view types gain the trio for display.
- `RegistrationFormValues` and the submit mapping extend accordingly.

## Affected Stacks
- single-stack: lumen-web (backend change: `us-apply-info-sev`)

## Depends-On
- coordination parent `us-apply-info`; builds against the REAL backend once
  us-apply-info-sev lands (backend-first ruling 2026-11-05)

## Scope
- In scope: `lumen-web` `src/api/us-orders.ts` (UsRegistrationExtBody + UsOrderView),
  `src/pages/us/registration/index.tsx` (form values, 请检信息 cells, submit mapping),
  `src/test/us-registration-page.test.tsx` (submit-body ext assertion).
- Out of scope: list-row/宽表 columns for the trio, HIS integration, department
  selects over org deps (free text per the parent design), 编辑 flows (create-only v1).

## Success Criteria
- The three fields render in 请检信息 and submit inside `ext` on 提交登记.
- Blank fields submit as null (no phantom strings).

## Constraints
- Text inputs (HIS-origin free text - the parent coordination ruling), no dict/
  deps coupling.
- Existing-page edit: no prototype round (skip per the web workflow).

## Risks
- None beyond form density - the section gains one three-cell row (the xl grid
  reflows per the existing 8/24 cells).
