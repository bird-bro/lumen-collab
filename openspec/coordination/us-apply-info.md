# Coordination: us-apply-info

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "us-apply-info"`.

## Why
The US registration workbench records the exam application's HIS origin: an
application (检查申请单/临床检查医嘱) is normally opened by an outpatient or
inpatient doctor in the HIS system. Section 2 请检信息 gains three display-origin
fields — 申请科室 / 申请医生 / 申请时间 (the department, doctor, and moment the
HIS application was opened). lumen v1 has no HIS feed, so the workbench operator
records them from the HIS application form; they persist on the order and surface
through the view.

## Shared Design
- Storage: `us_registration_ext` (the US 1:1 extension table) gains five nullable
  columns (V5, backend change): `apply_dept varchar(64)`, `apply_doctor varchar(64)`,
  `apply_time varchar(19)`, plus the RESERVED id anchors `apply_dept_id varchar(64)`
  and `apply_doctor_id varchar(64)` (user ruling 2026-11-24: store the ids alongside
  the names so the join anchors ride along once an HIS feed or a lumen picker
  surfaces them).
- Contract: the create body's `ext` block carries the fields 1:1 (the ext entity binds
  the client body 1:1 by design); the view flattens them like pregnancyStatus/note.
  All optional; absent/blank = the operator had no HIS form data. The v1 UI sends the
  name/time trio only — the id anchors exist in the contract and stay null until an
  HIS feed or a picker surfaces them.
- `apply_time` is a RAW TEXT stamp (`yyyy-MM-dd HH:mm:ss` as entered) — informational
  only (no state-machine or capacity semantics; nothing sorts/filters on it in v1),
  so no parse policy and no timestamp coercion. A future HIS integration (structured
  HL7 origin) may re-home it; the ext table is re-extractable.
- Token semantics: free text (HIS identities are external — HIS departments/doctors
  are not lumen org_dep/user rows; no dict catalog, no id anchoring). The reserved id
  columns are varchar(64): HIS codes are arbitrary token strings and lumen integer ids
  serialize into them losslessly.
- Frontend: three text inputs in 请检信息 (申请科室 / 申请医生 / 申请时间 with the
  format placeholder), submitted inside `ext`; no list-row/宽表 rendering in this
  change (form-section display only).

## Decisions
- ext-table columns, not the patient snapshot - the origin describes the APPLICATION,
  not the patient (the snapshot stays the demographics projection) - 2026-11-24
- apply_time as raw varchar(19), not timestamp - informational stamp, zero parse
  policy in v1; re-homing is a later migration if HIS integration lands - 2026-11-24
- free text, no deps/users selects - the values originate in HIS, not in lumen's
  org directory - 2026-11-24
- reserve apply_dept_id / apply_doctor_id varchar(64) alongside the names - names
  alone lose the join anchor; the v1 UI does not send them - 2026-11-24
- item picker split layout (方案 C, user-approved HTML prototype
  `docs/prototypes/us-exam-item-picker.html`, implemented same day): picked table
  left + common-item rail right (232px, NO price tags - user ruling), chips
  toggle add/remove (solid primary + ✓ replaces the gray-out), header AutoComplete
  seek (name/code fuzzy, adds without opening the dialog), trash-icon 删除 +
  ops-header right-edge refresh glyph (ConsoleTable family chrome), dashed-block
  添加项目 entry (the rep-template 添加测量项 pattern) - 2026-11-24
- item picker dialog redesign (the "precision picker", implemented on-page
  2026-11-24): live-filter search (the dead 查询 button removed) + 检查部位
  Select condition (client-side slice, distinct body names of the loaded
  catalog), full-row toggle via antd Checkbox (role=checkbox) + row click,
  picked rows tint + 常用 chip (flex-cell: the name ellipsizes, the chip never
  clips), fixed table layout with sticky header (name column ellipsis + title
  attr - horizontal scrollbar impossible), footer band with the live
  已选/合计 summary; width 760 and 14px rows (user ruling: crowding is solved
  by width, never by shrinking the body font) - 2026-11-24

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | us-apply-info-sev | implemented (backend suites 34/34) | **PASS** (2026-11-25) |
| frontend | us-apply-info-web | implemented (full suite 504/504, build clean) | **PASS** (2026-11-25) |

## Gate
Verify each stack independently. When ALL registered changes have an acceptable verify credential
(`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and batch-archive them.
