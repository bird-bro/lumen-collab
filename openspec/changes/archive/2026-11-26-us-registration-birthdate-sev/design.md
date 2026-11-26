# Design: us-registration-birthdate-sev

> Cross-stack parent: `openspec/coordination/us-registration-birthdate.md`.
> Web-side counterpart: `us-registration-birthdate-web`.

## Context
The create chain today: `RegistrationCreateRequest` (web body) →
`RegistrationOrderServiceImpl.toPortRequest` (explicit builder) → kernel
`PatientRegistrationRequest` (lumen-core) →
`PatientRegistryPortAdapter.toContractRequest` (`BeanUtil.copyProperties`
1:1) → Feign `PatientResolveOrRegisterRequest` (birthDate already present) →
patient domain resolve-or-register. The Feign request javadoc names birthDate
a resolve soft key ("出生日期（软匹配键之一）"); the port request's javadoc
deliberately keeps age/patientSource OFF the contract as exam-time extras —
birthDate is the opposite kind of value (archive + match key), so it belongs on
the port request. No migration, no error codes, no controller edit.

## Goals / Non-Goals
**Goals:**
- An optional `birthDate` rides create → port → resolve-or-register → archive.
- Absent birthDate keeps today's behavior byte-for-byte.

**Non-Goals:**
- No snapshot birth date (`PatientSnapshot` stays age-only; list/report show age).
- No Feign or patient-domain edits (contract and consumer already carry/accept it).
- No lookup/resolve-key UI changes (the 键型 seg sends idNo/cardNo/phone only).

## Decisions
- **D1 — binding**: `birthDate` is `java.util.Date` with
  `@JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")` on
  `RegistrationCreateRequest` — the web submits a date-only string; nullable,
  no default. Mirrors the file's existing `bookingTime` annotation habit.
- **D2 — kernel port (lumen-core)**: `PatientRegistrationRequest` gains the
  `birthDate` field (additive; the builder class grows one member). It is a
  resolve key, so it rides the contract unlike the exam-time extras; the
  adapter javadoc's copied-keys list gains birthDate (doc-only edit).
- **D3 — pass-through**: `toPortRequest` adds
  `.birthDate(request.getBirthDate())` to the builder chain. The adapter's
  `copyProperties(request, contract, true)` picks it up unchanged.
- **D4 — snapshot stays age-only**: `toSnapshot`/`PatientSnapshot` untouched;
  the archive holds birth date and `patUid` can re-fetch it. Recorded as a
  coordination decision (2026-11-25).
- **D5 — tests**: `UsRegistrationApiTests` walks the existing create-path
  pattern against the suite-base `PatientApi` stub seam (`UsApiTestBase`):
  walk-in create with birthDate → the captured resolve-or-register body carries
  the date (soft-key path); walk-in create without → null (regression guard).
  Mirror the stub-capture mechanics already used for the ext-block tests.

## Risks / Trade-offs
- birthDate joining soft matching changes resolution outcomes for same
  name+gender+phone archives with different birth dates — that precision is the
  point (EIS parity); the absent-value path is untouched, so existing callers
  see no difference.
- `java.util.Date` timezone: GMT+8 fixed on the annotation; a date-only string
  binds to midnight GMT+8 and the archive stores the calendar date — consistent
  with the house `bookingTime` precedent.
