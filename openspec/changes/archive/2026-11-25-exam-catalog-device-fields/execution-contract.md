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

- **Change name**: exam-catalog-device-fields
- **Problem to solve**:
DICOM MWL maps catalog facts to device-visible attributes: the item's `code`
(US-ABD-01 …) already serves as the Procedure Code Value, but the device shows the
Scheduled Procedure Step Description and Body Part Examined as TEXT — both Chinese
today. Devices without Chinese font/charset support render garbage, so the catalog
must carry Latin/ASCII device fields maintained by the administrators, and the
future MWL composer (lumen-integration) reads them by itemId at send time
(id-anchored — the order flow itself stays untouched).

<!-- AI: summarized — the catalog's three tables gain nullable device-facing
Latin fields (item desc / body DICOM token + Latin name / method desc),
maintained in the three setting pages, with V53 seeding the demo catalog
complete with device values -->
- **In scope**: ExamItem.deviceDesc + ExamBody.deviceToken/deviceName +
  ExamMethod.deviceDesc (entities/VOs/CRUD echo); V53 = the three ALTERs + the
  demo seed (34/46/58 rows, V52 idiom) + the shared-DB device-value backfill keyed
  (modality, code); the three setting pages' forms/tables; the seed test.

- **Out of scope**: the MWL composer (lumen-integration, future); patient-name
  transliteration; making the fields required; the stray DR residue row; any
  order-flow change (the composer reads the catalog by itemId).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/exam-setting/spec.md`
3:### Requirement: Exam Body Columns
14:#### Scenario: Body belongs to a group and may nest
21:#### Scenario: Body carries the device-facing fields
30:### Requirement: Exam Method Columns
37:#### Scenario: Fresh deployment creates the method columns
43:#### Scenario: Method carries the device-facing description
51:### Requirement: Exam Item Columns
68:#### Scenario: Item stores the catalog facts and the score weights
74:#### Scenario: Item code uniqueness
80:#### Scenario: Item stores the external mapping code
87:#### Scenario: Duplicate external mapping codes are accepted
92:#### Scenario: Item carries the device-facing description
- `specs/web-system-console/spec.md`
3:### Requirement: Device-Facing Field Maintenance
14:#### Scenario: The catalog forms maintain the device fields
24:#### Scenario: The demo catalog ships the device values


## Design Constraints

- **Architecture/Decisions**:
- **D1 — three nullable columns, one migration (V53)**: `exam_item.device_desc`,
  `exam_body.device_token` + `device_name`, `exam_method.device_desc` — additive
  ALTERs; the item's existing `code` doubles as the Procedure Code Value (no new
  code column). The link table `exam_item_method` carries no text (the method
  dictionary owns the wording).
- **D2 — the seed rides the same V53** (ALTERs first, then the three INSERT
  blocks + setval tails, the V52 idiom: explicit ids + ON CONFLICT (id) DO
  NOTHING). Fresh installs get a complete device-facing catalog; the shared DB's
  catalog rows replay as no-ops.
- **D3 — shared-DB device-value backfill**: ON CONFLICT DO NOTHING cannot update
  existing rows, so the seed's device values land on the shared DB via a targeted
  `UPDATE ... FROM (VALUES ...)` keyed by (modality, code) in the same migration —
- **Technical constraints**: JDK 11 reactor; the V2/V36 lesson (no semicolons in
  comment lines); dataset source = the shared DB via pgsql-test-lumen MCP; the D4
  gate — the drafted Latin table goes to the user BEFORE task 3 embeds; the
  frontend ships after the backend user-verify gate (house flow); path-scoped
  commits (parallel-session worktree).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
9:- [ ] 1.1 Entities + VOs: `ExamItem.deviceDesc`, `ExamBody.deviceToken` +
13:- [ ] 1.2 The CRUD services persist and echo the fields verbatim (BeanUtil 1:1 —
15:- [ ] 1.3 `V53__exam_catalog_device_fields.sql`: the three additive ALTERs + the
24:- [ ] 2.1 Draft the Latin device values table (46 items / 34 bodies with DICOM
30:- [ ] 3.1 Embed the reviewed values into V53 (the seed blocks + backfill) and
35:- [ ] 3.2 `./mvnw -pl lumen-system -am test -q` exit 0 — verify: the seed rides
40:- [ ] 4.1 The three setting pages gain the device-field inputs (edit/create
47:- [ ] 5.1 `openspec validate exam-catalog-device-fields` passes (the exam-setting

## Test Obligations

<!-- AI: identified -->
- **Behaviors requiring failing-test-first**: the three setting pages' device-field
  assertions (task 4.1 RED→GREEN per the web TDD rule); the seed test's spot rows
  + counts (authored with the migration in the same batch — a seed has no classic
  RED).
- **Required edge cases**: blank device fields persist NULL + em-dash echo; the
  shared-DB replay is a no-op while the backfill converges the device values;
  idempotent re-run changes nothing; no dangling body_id; setval above MAX(id).
- **Regression-sensitive areas**: the catalog CRUD suites (exam-setting tests);
  the lumen-system auth seed family sharing the ledger; the three page suites'
  form-chrome assertions.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 7 tasks across lumen-system + lumen-web with a
  user-review gate mid-flow; sequential backend→frontend per the house flow —
  Batch Inline with the gate as a hard pause.

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
