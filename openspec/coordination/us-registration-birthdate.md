# Coordination: us-registration-birthdate

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "us-registration-birthdate"`.

## Why
The US registration workbench records the patient's age only (岁/月/天 strings);
the archive created for a walk-in keeps `birth_date` null. EIS precedent
(`RegisterAndAppointment/Info/CheckInfo.vue`): 出生日期 is a first-class form
field with two-way age conversion. birthDate is a patient-domain soft-match key
and archive field; feeding it makes walk-in matching more precise and the
archive complete. User ruling 2026-11-25: 方案 B — an editable control that
participates in archive creation (not the backfill-only 方案 A).

## Shared Design
- Feign contract: NO change. `PatientResolveOrRegisterRequest.birthDate` and
  `PatientIdentityResponse.birthDate` already exist (patient domain accepts and
  delivers birth date today).
- Kernel port (lumen-core): `PatientRegistrationRequest` gains `birthDate`
  (Date, optional). birthDate is a RESOLVE soft key (contract javadoc:
  "出生日期（软匹配键之一）"), unlike age/patientSource which are exam-time extras
  deliberately kept off the contract. The adapter's `BeanUtil.copyProperties`
  1:1 copy picks it up with no adapter edit.
- US create: `RegistrationCreateRequest` gains `birthDate` (optional); the
  create service's `toPortRequest` builder passes it through. No controller
  change (DTO binding is automatic).
- Snapshot: stays age-only. `PatientSnapshot` gains no birthDate — the list and
  report header show age (EIS lists do not show birth date either); the archive
  holds the truth and `patUid` can re-fetch it. The resolve identity response
  already delivers birthDate to the web (candidate card shows it today).
- Matching semantics: with birthDate present, resolve-or-register treats it as a
  soft key — same name+gender+phone with a different birth date stops colliding.
- Frontend: 出生日期 DatePicker (antd) in 患者信息 next to 年龄; two-way
  conversion per the EIS precedent — picker → 岁/月/天 exact (reference = today);
  age edit → picker year-pinned only ("只关注年"). Future dates disabled.
  Matched-identity backfill extends `fillFromIdentity`: birthDate fills the
  picker AND the converted 岁/月/天 (today the match fills neither). Submit
  `birthDate` as `yyyy-MM-dd`; absent → null (walk-ins without birth date stay
  legal — current norm).

## Decisions
- 方案 B: editable control + archive participation, not backfill-only 方案 A -
  user ruling - 2026-11-25
- birthDate lives on the ARCHIVE only; order snapshot stays age-only - the list
  and report header show age, no snapshot column/field needed - 2026-11-25
- birthDate is optional (nullable end to end) - walk-ins without birth date are
  the current norm and stay legal - 2026-11-25
- two-way conversion follows the EIS CheckInfo precedent: picker→age exact,
  age→picker year-pinned ("只关注年") - 2026-11-25
- kernel port request carries birthDate (resolve key), age/patientSource stay
  off the contract - the existing adapter copy contract is untouched - 2026-11-25

## Change Registry
| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | us-registration-birthdate-sev | proposing | - |
| frontend | us-registration-birthdate-web | proposing | - |

## Gate
Verify each stack independently. When ALL registered changes have an acceptable verify credential
(`PASS`, or user-confirmed `PASS_WITH_ISSUES`), preflight all credentials and batch-archive them.
