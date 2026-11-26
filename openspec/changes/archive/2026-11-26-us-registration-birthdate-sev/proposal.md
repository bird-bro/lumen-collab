# Proposal: us-registration-birthdate-sev

> Per-stack change of the cross-stack feature `us-registration-birthdate`
> (parent: `openspec/coordination/us-registration-birthdate.md`). Backend only —
> the web control/form work lives in `us-registration-birthdate-web`.

## Why
The US registration create chain stops at age: the walk-in archive it builds
keeps `birth_date` null because nothing in the chain carries the value
(`RegistrationCreateRequest` has no birthDate; the kernel port request has no
birthDate). The Feign contract already carries birthDate
(`PatientResolveOrRegisterRequest.birthDate`, a resolve soft key) and the
patient domain accepts it — the US chain simply never hands it over. EIS
precedent: 出生日期 is a first-class registration form field.

## What Changes
- `RegistrationCreateRequest` (lumen-us) gains an optional `birthDate` (Date).
- The kernel port request `PatientRegistrationRequest` (lumen-core) gains an
  optional `birthDate` — it is a resolve soft key per the Feign contract
  javadoc, so unlike age/patientSource (exam-time extras deliberately off the
  contract) it rides the resolve-or-register chain.
- `RegistrationOrderServiceImpl.toPortRequest` passes `request.getBirthDate()`
  into the port builder. The adapter's `BeanUtil.copyProperties` 1:1 copy needs
  no edit. No controller change (DTO binding is automatic).
- Matching semantics: when birthDate is present it participates as a soft-match
  key — same name+gender+phone with a different birth date stops colliding.
- The order snapshot stays age-only (`PatientSnapshot` unchanged) — the archive
  holds birth date; the list/report surface shows age.

## Capabilities

### New Capabilities

### Modified Capabilities
- `us-station`: new "Birth Date Capture" requirement on the Registration
  Surface chain — an optional birthDate rides the create → port →
  resolve-or-register chain into the archive; absent birthDate keeps today's
  behavior byte-for-byte.

## Impact
- `lumen-sev/lumen-us`: `dto/RegistrationCreateRequest.java`,
  `service/impl/RegistrationOrderServiceImpl.java` (toPortRequest builder).
- `lumen-sev/lumen-core`: `patient/PatientRegistrationRequest.java` (+1 field).
- `lumen-feign`: unchanged (contract already carries birthDate).
- `lumen-patient`: unchanged (accepts birthDate today).
- Tests: `UsRegistrationApiTests` (walk-in create with/without birthDate →
  port contract payload assertions), mirroring the existing create-path tests.
