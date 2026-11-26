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

- **Change name**: exam-body-device-field-cleanup
- **Problem to solve**:
`exam_body` carries two speculative device fields from exam-catalog-device-fields
(V53, archived 2026-11-25) and only one of them earns its keep. `device_name`
(the Latin body name) has no consumer anywhere in the MWL attribute map — the
only body-related device slot, Body Part Examined (0018,0015), is filled by
`device_token` itself. `device_token` is also poorly named: "token" reads as an
auth/session token, and the sibling `device_desc` fields are equally
device-facing DICOM text, so "token" fails to say what makes this column
special. Renaming it to `device_body_part` anchors the DICOM attribute name.

- **Intent**: drop `exam_body.device_name` (no MWL consumer) and rename
  `device_token` → `device_body_part` (token is ambiguous; the new name anchors
  the DICOM Body Part Examined attribute) via one V54 migration, backend
  entity/dto/tests, and the exam-body frontend page.
- **In scope**:
  - V54: `DROP COLUMN device_name` + `RENAME COLUMN device_token TO device_body_part`
  - Backend: `ExamBody` / `BodyCreateRequest` / `BodyUpdateRequest` field rename
    + removal; orphaned `AuthExamBodyApiTests` device-fields test rewritten;
    `ExamCatalogDemoSeedTests` body assertions if any
  - Frontend: `api/types.ts` + exam-body page (drop 拉丁名, rename token wire
    name, single-term echo) + page tests; full frontend suite once green
- **Out of scope**:
  - `device_desc` on `exam_item`/`exam_method` (unaffected)
  - V53 edits (applied + archived — never rewritten)
  - any us-slot-options-window-fix file (in-flight in the shared worktree)
  - re-seeding or regenerating Latin names (the 34 seeded names die with the column)

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
5:### Requirement: Exam Body Columns
16:#### Scenario: Body belongs to a group and may nest
23:#### Scenario: Body carries the device-facing field
- `specs/web-system-console/spec.md`
5:### Requirement: Device-Facing Field Maintenance
16:#### Scenario: The catalog forms maintain the device fields
26:#### Scenario: The demo catalog ships the device values


## Design Constraints

- **Architecture/Decisions**:
- **D1 — V54 as one ALTER, V53 untouched**: `ALTER TABLE exam_body DROP COLUMN
  device_name, RENAME COLUMN device_token TO device_body_part;` (Postgres allows
  both in one statement). V53 is applied and archived — never rewritten. Fresh
  installs replay V53's seed (writes `device_name`, then V54 drops it): harmless
  dead weight in the history, the shared DB converges on the next backend run.
- **D2 — JSON key `deviceBodyPart`**: payload/VO key follows the entity field, the
  same verbatim-echo contract as before. The admin UI label 部位token stays (user
  vocabulary, unchanged); only the wire name changes.
- **D3 — orphaned tests rewritten in place**: `AuthExamBodyApiTests`'s
  uncommitted `deviceFieldsEchoAndStayNullable` (+48 lines) switches to
  `deviceBodyPart` / drops the `deviceName` assertions and lands with this change
  (fixes the f1b6e96 path-scoped commit miss). `AuthExamItemApiTests` /
  `AuthExamMethodApiTests` (+54/+57, `deviceDesc` only) need no edits.
- **D4 — seed display drops the name half**: the exam-body page tree echo
  (`token · name`) collapses to the single term; `api/types.ts` loses
  `deviceName`, renames `deviceToken` → `deviceBodyPart`.
- **Technical constraints**:
  - lumen-system builds run with JDK 11 from `lumen-sev/` (reactor), output
    redirected to files
  - commits path-scoped to this change's files (shared dirty worktree)

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 Add `V54__exam_body_device_field_cleanup.sql` to the lumen-system
9:- [ ] 1.2 Rename `ExamBody.deviceToken` → `deviceBodyPart`, delete
13:- [ ] 1.3 Rewrite the orphaned `deviceFieldsEchoAndStayNullable` in
21:- [ ] 2.1 `src/api/types.ts`: exam-body types lose `deviceName`, rename
24:- [ ] 2.2 `src/pages/setting/exam-body/index.tsx`: remove the 拉丁名 form item and
32:- [ ] 3.1 Run the full frontend suite once the page tests are green —

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: the rewritten
  `deviceFieldsEchoAndStayNullable` asserts `deviceBodyPart` echo + `deviceName`
  absence — red against the old entity, green after 1.2 (the rename is the fix;
  tests are edited first).
- **Required edge cases**: body created without the device field echoes NULL;
  provided value echoes verbatim; the JSON no longer carries `deviceName`.
- **Regression-sensitive areas**: `AuthExamItemApiTests`/`AuthExamMethodApiTests`
  (`deviceDesc` contract untouched); frontend full suite (page types ripple).

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 6 tasks across 2 stacks but one linear thread, no
  cross-module coupling beyond the shared wire name; SDD ceremony unjustified.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: 
- **Blocking categories**: 

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
