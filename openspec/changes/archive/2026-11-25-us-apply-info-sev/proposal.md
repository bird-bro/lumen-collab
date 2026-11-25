# Proposal: us-apply-info-sev

> Cross-stack parent: `openspec/coordination/us-apply-info.md` (the shared design -
> column shapes, token semantics, apply_time policy - lives THERE; this proposal
> carries the backend implementation only).

## Why
请检信息 records the exam application's HIS origin (申请科室/申请医生/申请时间 -
the clinic department, doctor, and moment that opened the 检查申请单). The ext table
has nowhere to store them.

## What Changes
- V5 migration: `us_registration_ext` gains `apply_dept varchar(64)` /
  `apply_doctor varchar(64)` / `apply_time varchar(19)` plus the RESERVED id anchors
  `apply_dept_id varchar(64)` / `apply_doctor_id varchar(64)` (user ruling 2026-11-24:
  the join anchors ride along with the names) — all nullable.
- `UsRegistrationExt` gains the five fields (the create body's ext block binds the
  entity 1:1, so the request contract extends automatically); `RegistrationOrderView`
  flattens them in `toView` like pregnancyStatus/note.

## Affected Stacks
- single-stack: lumen-sev (frontend change: `us-apply-info-web`)

## Depends-On
- coordination parent `us-apply-info`; none technically (additive columns)

## Scope
- In scope: `lumen-sev/lumen-us` V5 migration, `UsRegistrationExt`,
  `RegistrationOrderView`, `toView` flattening, `UsRegistrationExtTests` column-set
  ruling update, `UsRegistrationApiTests` ext round-trip extension.
- Out of scope: HIS integration, list/宽表 rendering, parse/format validation
  (raw-text stamp per the parent design), other departments' ext tables.

## Success Criteria
- The ext table carries the three columns; a create body with ext apply fields
  persists them 1:1 inside the registration transaction and the page query returns
  them flattened on the view.
- The ext column-set ruling test passes with the extended set.

## Constraints
- Column shapes verbatim from the parent coordination doc (varchar(64)/varchar(64)/
  varchar(19), all nullable).
- Comment lines carry no semicolons (the V2 house lesson, re-execution tests).

## Risks
- `UsRegistrationExtTests` pins the ruled column set exactly → update it with this
  change (the ruling extends, it does not drift).
