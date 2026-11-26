# Tasks: us-registration-birthdate-web

## 1. Form field + conversions (TDD)

- [x] 1.1 Red: `us-registration-page.test.tsx` — picker open→change→Enter
      commits a birth date and the 岁/月/天 inputs show the exact conversion;
      editing 岁 re-pins the picker year (month/day kept); future dates
      disabled. Run → fail.
- [x] 1.2 Green: 患者信息 Row gains the 出生日期 Field + DatePicker after 年龄
      (Dayjs value, `disabledDate` future guard, additive `onChange`
      conversions per design D2). Run → pass.
- [x] 1.3 Red: MATCHED resolve with `identity.birthDate` backfills picker +
      converted 岁/月/天; identity without `birthDate` leaves the form as
      today. Run → fail.
- [x] 1.4 Green: extend `fillFromIdentity` (parse via dayjs, converted age
      fill, absent date = no-op). Run → pass.

## 2. Submit wiring (TDD)

- [x] 2.1 Red: submit with a picked date posts `birthDate: 'YYYY-MM-DD'`;
      unset posts null. Run → fail.
- [x] 2.2 Green: `RegistrationFormValues` + `birthDate`; submit assembly
      formats `YYYY-MM-DD`/null; `createOrder` body type gains the optional
      field. Run → pass.

## 3. Verify

- [x] 3.1 `pnpm vitest run src/test/us-registration-page.test.tsx` green;
      then full `pnpm test` (logs under `logs/`).
