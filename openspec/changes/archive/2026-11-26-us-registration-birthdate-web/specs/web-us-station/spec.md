## ADDED Requirements

### Requirement: Birth Date Field

The registration form SHALL offer an optional editable 出生日期 DatePicker in
the 患者信息 section next to 年龄, with two-way conversion per the EIS
precedent — picking a date recomputes 岁/月/天 exactly against today; editing
年龄 re-pins the picker by year only (month/day preserved). Future dates are
disabled. A matched identity backfills both the picker and the converted age
fields. Submission carries the date as `yyyy-MM-dd`, null when unset; 年龄
stays required (岁 or 月 or 天) and the birth date stays optional.

#### Scenario: Birth date and age convert both ways

- **WHEN** the operator picks a birth date
- **THEN** the 岁/月/天 inputs recompute exactly against today
- **WHEN** the operator edits the 岁 value
- **THEN** the picker re-pins its year to match while month/day are preserved

#### Scenario: Matched identity backfills birth date and age

- **WHEN** the resolution flow matches an identity carrying `birthDate`
- **THEN** the form fills the picker with that date and the 岁/月/天 inputs
  with the converted values

#### Scenario: Create posts the birth date

- **WHEN** the form submits with a birth date set
- **THEN** the create body carries it as `yyyy-MM-dd`
- **AND** an unset birth date submits as null and the form still passes
  validation (age remains the required demographic)

#### Scenario: Future birth dates are refused

- **WHEN** the picker renders
- **THEN** dates after today are disabled and cannot be committed
