# Proposal

## Why

`exam_body` carries two speculative device fields from exam-catalog-device-fields
(V53, archived 2026-11-25) and only one of them earns its keep. `device_name`
(the Latin body name) has no consumer anywhere in the MWL attribute map — the
only body-related device slot, Body Part Examined (0018,0015), is filled by
`device_token` itself. `device_token` is also poorly named: "token" reads as an
auth/session token, and the sibling `device_desc` fields are equally
device-facing DICOM text, so "token" fails to say what makes this column
special. Renaming it to `device_body_part` anchors the DICOM attribute name.

## What Changes

- **BREAKING** `exam_body.device_name` (拉丁名) is DROPPED — no MWL slot consumes
  it; the 34 seeded Latin names go with the column (V53's INSERT text stays in
  the migration history).
- **BREAKING** `exam_body.device_token` is RENAMED to `device_body_part` (JSON
  payload key `deviceToken` → `deviceBodyPart`) — same values, same semantics
  (the DICOM Body Part Examined defined term, sent verbatim).
- New migration **V54** (V53 is applied and archived — never rewritten): one
  `ALTER TABLE exam_body` with `DROP COLUMN device_name` +
  `RENAME COLUMN device_token TO device_body_part`.
- Backend: `ExamBody` entity, `BodyCreateRequest`/`BodyUpdateRequest` lose
  `deviceName` and gain `deviceBodyPart`; the uncommitted-but-orphaned device
  fields tests in `AuthExamBodyApiTests` (never landed in f1b6e96) are rewritten
  to the new field names and land with this change.
- Frontend: the 检查部位 page drops the 拉丁名 form item and its token·name echo,
  renames the token field to `deviceBodyPart` (label 部位token stays);
  `api/types.ts` and the page tests follow.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `exam-setting`: the Exam Body Columns requirement loses `device_name` and
  renames `device_token` → `device_body_part`; the device-fields scenario
  covers the single remaining field.
- `web-system-console`: Device-Facing Field Maintenance shrinks the 检查部位
  field set to 部位token (`deviceBodyPart`) — 拉丁名 is removed.

## Impact

- `lumen-sev/lumen-system`: V54 migration; `ExamBody`, `BodyCreateRequest`,
  `BodyUpdateRequest`; `AuthExamBodyApiTests` (orphaned device-fields test
  rewritten); `ExamCatalogDemoSeedTests` if it asserts body device fields.
- `lumen-web`: `src/pages/setting/exam-body/index.tsx` (form item, tree echo,
  payloads), `src/api/types.ts`, `src/test/exam-body-page.test.tsx`.
- Shared test DB: V54 applies on the next backend test run; the demo seed's
  body Latin names disappear (token values survive under the new name).
- No other consumer reads these columns (the MWL composer does not exist yet —
  grep confirms entity/dto/tests/frontend are the whole reference set).
