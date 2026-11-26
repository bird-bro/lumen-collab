# Design: us-registration-birthdate-web

> Cross-stack parent: `openspec/coordination/us-registration-birthdate.md`.
> Backend counterpart: `us-registration-birthdate-sev` (create body field +
> archive chain).

## Context
The form today: 患者信息 Row holds 姓名/性别/年龄(岁·月·天)/就诊卡号/证件/手机
号/患者来源/住院床号/身高体重; submit joins the age parts into one `age`
string (`ageParts`, index.tsx:723-735) and sends no birth date.
`fillFromIdentity` backfills six fields and neither birth date nor age.
Matched identities already deliver `birthDate` (candidate card renders it).
antd DatePicker + dayjs are house-standard; picker test commit pattern:
open→change→Enter (jsdom precedent).

## Goals / Non-Goals
**Goals:**
- Optional editable 出生日期 next to 年龄, EIS two-way conversion, matched
  backfill, `yyyy-MM-dd` submission.

**Non-Goals:**
- No 年龄 requiredness change (岁 or 月 or 天 validator untouched).
- No resolve-key (键型 seg) changes; no list/snapshot birth-date display.
- No new API client module (additive body field only).

## Decisions
- **D1 — placement**: DatePicker sits immediately after 年龄 in the 患者信息
  Row (the conversion pair adjacent, EIS order); label 出生日期; Field wrapper
  like its siblings.
- **D2 — conversion semantics (EIS parity, revised during apply)**: picker
  change → recompute 岁/月/天 exactly (full years/months/days against today)
  via `form.setFieldsValue`; 岁 input change → year-pin the picker ONLY when a
  picker value already exists (EIS "只关注年": keep the picker's month/day,
  replace the year). An empty picker + age edit is a NO-OP — the EIS
  else-branch (fabricate a full date from today's month/day) would write
  invented month/day data into the archive; the unset path must stay null
  (spec scenario "Create posts the birth date"/"unset submits as null").
  月/天 edits do not touch the picker — month/day are the picker's domain.
  Handlers are additive `onChange`/`onValuesChange` props; no field
  definitions are rewritten. A picker CLEAR resets the derived age fields (the
  fields are the picker's shadow — no stale derived age may ship without its
  birth date); `diffYmd` normalizes a 12-month remainder into the years slot
  (the Feb-29 anniversary asymmetry — months never leave [0,11]).
  (Review revisions, 2026-11-25.)
- **D3 — value + format**: the form value is a Dayjs; submit formats
  `YYYY-MM-DD` (null when unset); identity `birthDate` parses through
  `dayjs()` (accepts both `YYYY-MM-DD` and the timestamped backend form).
- **D4 — backfill**: `fillFromIdentity` gains birthDate (Dayjs or null) plus
  the converted 岁/月/天 when the identity carries a date; absent date keeps
  today's behavior (no age fill). The archive's birth date backfills only when
  parseable and not in the future — a future/malformed archive row no-ops
  (disabledDate governs the panel, not programmatic values; a bad row must
  never poison the create body). (Review revision, 2026-11-25.)
- **D5 — guard**: `disabledDate` blocks anything after today (end-of-day
  compare); no min bound (open-ended past).
- **D6 — body type**: `createOrder` body gains optional `birthDate?: string |
  null` (additive; rides with the sev change's `RegistrationCreateRequest`).
- **D7 — tests** (`us-registration-page.test.tsx`): picker open→change→Enter
  commits a date and the 岁/月/天 inputs reflect the conversion; editing 岁
  re-pins the picker year; MATCHED resolve backfills picker + age; submit body
  carries `yyyy-MM-dd` / null; future dates are disabled.

## Risks / Trade-offs
- Age→year-pin is lossy by design (EIS precedent): 月/天 precision survives
  only from the picker side. Acceptable — the archive stores the picker value;
  age strings remain the snapshot demographic.
- dayjs locale/method availability matches the existing form usage (dayjs is
  already a dependency of the page family); no new libs.
