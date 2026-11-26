# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: us-registration-birthdate-web
- **Problem to solve**:
The US registration form records the patient's age as 岁/月/天 strings and has
no 出生日期 control; matched identities deliver `birthDate` (the candidate card
already shows it) but `fillFromIdentity` backfills neither birth date nor age.
EIS precedent (`CheckInfo.vue`): 出生日期 is a first-class form control with
two-way age conversion. User ruling 2026-11-25: 方案 B — editable control,
participates in archive creation.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: 出生日期 DatePicker in 患者信息 (after 年龄), EIS two-way
  conversion (picker→岁/月/天 exact; 岁→picker year-pin), future-date guard,
  `fillFromIdentity` birthDate+age backfill, submit `birthDate` `YYYY-MM-DD`/null,
  additive `createOrder` body field; TDD in `us-registration-page.test.tsx`.
- **Out of scope**: backend chain (us-registration-birthdate-sev); 年龄
  requiredness change; resolve-key (键型 seg) changes; list/snapshot birth-date
  display; new API client modules.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
3:### Requirement: Birth Date Field
13:#### Scenario: Birth date and age convert both ways
20:#### Scenario: Matched identity backfills birth date and age
26:#### Scenario: Create posts the birth date
33:#### Scenario: Future birth dates are refused


## Design Constraints

- **Architecture/Decisions**:
- **D1 — placement**: DatePicker sits immediately after 年龄 in the 患者信息
  Row (the conversion pair adjacent, EIS order); label 出生日期; Field wrapper
  like its siblings.
- **D2 — conversion semantics (EIS parity)**: picker change → recompute
  岁/月/天 exactly (full years/months/days against today) via
  `form.setFieldsValue`; 岁 input change → year-pin the picker only (EIS
  "只关注年": keep the picker's month/day, replace the year; empty 岁 leaves
  the picker). 月/天 edits do not touch the picker — month/day are the
  picker's domain. Handlers are additive `onChange` props on the existing
  Form.Item children; no field definitions are rewritten.
- **D3 — value + format**: the form value is a Dayjs; submit formats
  `YYYY-MM-DD` (null when unset); identity `birthDate` parses through
- **Technical constraints**: no mock layer (real backend via vite proxy; unit
  tests stub `@/api/...` at the seam only); additive `onChange` props — no
  existing field definitions rewritten (never-modify rule); antd DatePicker +
  dayjs (already page-family deps); test commit pattern open→change→Enter
  (jsdom picker precedent); existing-page edit — no UI prototype step.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Red: `us-registration-page.test.tsx` — picker open→change→Enter
9:- [ ] 1.2 Green: 患者信息 Row gains the 出生日期 Field + DatePicker after 年龄
12:- [ ] 1.3 Red: MATCHED resolve with `identity.birthDate` backfills picker +
15:- [ ] 1.4 Green: extend `fillFromIdentity` (parse via dayjs, converted age
20:- [ ] 2.1 Red: submit with a picked date posts `birthDate: 'YYYY-MM-DD'`;
22:- [ ] 2.2 Green: `RegistrationFormValues` + `birthDate`; submit assembly
28:- [ ] 3.1 `pnpm vitest run src/test/us-registration-page.test.tsx` green;

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: all four spec scenarios —
  two-way conversion (1.1), matched backfill (1.3), submit body (2.1) get red
  tests before their green steps (1.2/1.4/2.2).
- **Required edge cases**: identity without `birthDate` → no-op backfill;
  unset picker submits null and validation still passes (age rule untouched);
  月/天 edits leave the picker untouched; empty 岁 leaves the picker.
- **Regression-sensitive areas**: existing submit assembly (`ageParts` join),
  `fillFromIdentity`'s six current fields, wide/narrow filter + 列显隐 tests
  (page suite 27 cases), jsdom picker interactions family.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 7 tasks, one page + one test file, no risk
  indicators — matches the us-apply-info-web / us-booking-slot-picker-web
  precedent mode.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: red→green ordering (1.1→1.2, 1.3→1.4, 2.1→2.2);
  full page suite green before verify.
- **Blocking categories**: conversion semantics drift from D2 (month/day
  preservation, 月/天 no-op); mock-layer regression (page data must stay real
  backend).

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
