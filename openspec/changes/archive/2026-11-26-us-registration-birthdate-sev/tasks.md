# Tasks: us-registration-birthdate-sev

## 1. Port chain (TDD)

- [x] 1.1 Red: `UsRegistrationApiTests` — walk-in create with `birthDate`
      (`yyyy-MM-dd` body) asserts the captured resolve-or-register contract body
      carries the birth date; walk-in create without `birthDate` asserts null
      (regression guard). Run → fail (field does not exist).
- [x] 1.2 Green: `RegistrationCreateRequest` + `birthDate` (`Date`,
      `@JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")`, nullable);
      kernel `PatientRegistrationRequest` + `birthDate`;
      `toPortRequest` builder passes it; adapter javadoc's copied-keys list
      gains birthDate. Run → pass.

## 2. Verify

- [x] 2.1 `./mvnw -pl lumen-us -am test -q` (JDK 11) — full lumen-us suite green
      (build/verify logs under `logs/`).
- [x] 2.2 Confirm no other consumer of `PatientRegistrationRequest` breaks
      (`grep` lumen-radiology/lumen-endo usages; additive field, builder-safe).
