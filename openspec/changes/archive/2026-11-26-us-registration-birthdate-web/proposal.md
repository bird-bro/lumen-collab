# Proposal: us-registration-birthdate-web

> Per-stack change of the cross-stack feature `us-registration-birthdate`
> (parent: `openspec/coordination/us-registration-birthdate.md`). Frontend only —
> the backend chain work lives in `us-registration-birthdate-sev`.

## Why
The US registration form records the patient's age as 岁/月/天 strings and has
no 出生日期 control; matched identities deliver `birthDate` (the candidate card
already shows it) but `fillFromIdentity` backfills neither birth date nor age.
EIS precedent (`CheckInfo.vue`): 出生日期 is a first-class form control with
two-way age conversion. User ruling 2026-11-25: 方案 B — editable control,
participates in archive creation.

## What Changes
- 患者信息 section gains a 出生日期 DatePicker (antd), placed next to 年龄.
- Two-way conversion per the EIS precedent: picker → 岁/月/天 recomputed exact
  (reference = today); age edit → picker re-pinned by year only ("只关注年",
  month/day preserved).
- Future dates disabled on the picker.
- `fillFromIdentity` extends: a matched identity's `birthDate` fills the picker
  AND the converted 岁/月/天.
- Submit: `birthDate` as `yyyy-MM-dd` (null when unset) on the create body —
  the backend change (`us-registration-birthdate-sev`) carries it to the archive.
- 年龄 stays required exactly as today (岁 or 月 or 天); birth date stays
  optional.

## Capabilities

### New Capabilities

### Modified Capabilities
- `web-us-station`: new "Birth Date Field" requirement on the registration
  form — an optional editable birth date with two-way age conversion, matched
  backfill, and `yyyy-MM-dd` submission.

## Impact
- `lumen-web/src/pages/us/registration/index.tsx`: form values type, 患者信息
  Row, submit assembly, `fillFromIdentity`.
- Tests: `lumen-web/src/test/us-registration-page.test.tsx` — picker
  open→change→Enter commit, two-way conversion assertions, backfill assertion,
  submit-body birthDate assertion.
- API client: no change (`RegistrationCreateRequest` body field arrives with
  the sev change; the web `createOrder` body type gains the optional field).
