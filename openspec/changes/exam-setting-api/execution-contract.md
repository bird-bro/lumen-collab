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

- **Change name**: exam-setting-api
- **Problem to solve**:
The exam workflow (booking → registration → exam → report) has no dictionary layer:
开单 needs exam items, items need body-part grouping, report writing needs phrase
templates. The RCP reference system proves the feature family (`/setting/*`), and the
2026-11-12 brainstorm re-modeled it for lumen (decisions in
`openspec/coordination/exam-setting.md`): a standalone hospital-wide service instead
of RCP's platform-scoped pile-up, split body/group tables instead of the synthetic
root tree, a join table instead of comma-joined methods, and two-scope report
templates. v1 radiology lands first; us/endo inherit the same catalog later.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**: new `lumen-setting` service (module+schema+6 tables), exam-catalog and
  report-template CRUD endpoints with delete guards and scope enforcement; lumen-system
  migrations seeding the 检查设置 menus/button perms and the dict-1021 DR row; pg-init
  schema/role; full Auth* test family.
- **Out of scope**: 项目对照/HIS mapping (integration domain), print templates,
  performance calculation, booking-side consumption APIs, MWL mapping, 科室-scope
  templates, MQ events, Feign contracts, frontend pages (exam-setting-web).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/data/spec.md`
5:### Requirement: Schema-per-Service Isolation
16:#### Scenario: Role isolation denies cross-schema access
22:#### Scenario: Schemas exist after initialization
27:#### Scenario: Shared system tables are reachable from every role
- `specs/exam-setting/spec.md`
12:### Requirement: Setting Schema Ownership
20:#### Scenario: Service migrates only its own schema
27:#### Scenario: Role isolation denies cross-schema access
33:### Requirement: Exam Body Group Columns
40:#### Scenario: Fresh deployment creates the body-group columns
46:### Requirement: Exam Body Columns
54:#### Scenario: Body belongs to a group and may nest
61:### Requirement: Exam Method Columns
67:#### Scenario: Fresh deployment creates the method columns
72:### Requirement: Exam Item Columns
82:#### Scenario: Item stores the catalog facts and the score weights
88:#### Scenario: Item code uniqueness
94:### Requirement: Exam Item Method Binding
100:#### Scenario: Item update rewrites method bindings atomically
107:### Requirement: Report Template Columns
116:#### Scenario: Public and private templates share one tree model
124:### Requirement: Modality Vocabulary
130:#### Scenario: Unknown modality rejected
135:### Requirement: Referenced-Row Delete Guards
141:#### Scenario: Group with bodies refused
146:#### Scenario: Method in use refused
151:### Requirement: Exam Item Endpoints
158:#### Scenario: Page filter by group
164:#### Scenario: Perm-gated write
169:### Requirement: Body Group and Body Endpoints
177:#### Scenario: Group list feeds the item page cascade
182:#### Scenario: Body tree scoped to a group
187:### Requirement: Exam Method Endpoints
193:#### Scenario: Method list feeds the item form
198:### Requirement: Report Template Endpoints
- `specs/service-topology/spec.md`
5:### Requirement: Foundation Module Set
13:#### Scenario: Foundation modules resolve
20:#### Scenario: Deferred module requested
- `specs/system-data/spec.md`
5:### Requirement: Exam Setting Menu Pages
21:#### Scenario: Fresh deployment seeds the exam-setting console group
27:#### Scenario: Re-execution is a no-op
32:### Requirement: Exam Setting Button Perms
39:#### Scenario: Admin role receives the button grants
44:### Requirement: Exam Modality Vocabulary Completion
51:#### Scenario: DR value available after migration


## Design Constraints

- **Architecture/Decisions**:
### D1: Module shape (naming.md service-package-layout)
`lumen-sev/lumen-setting` executable jar, `org.birdbro.lumen.setting.SettingApplication`
scanning `org.birdbro`; layering `controller/service/mapper/entity/dto`; catalog
services are concrete classes (MenuService precedent) — no interface+impl ceremony;
application class in the service root package. `pom.xml` inherits `lumen-bom` versions
1:1 (no version edits). Aggregator gains `<module>lumen-setting</module>`.
### D2: Splits and joins (what RCP got wrong, kept fixed)
- Group and body are separate tables (brainstorm Option A): `exam_body.group_id`
  anchors the group, `up_id` nests anatomy within the group — no synthetic root.
- Item carries `body_id` only; group filtering joins `exam_body`. NO `group_id`
  denormalization on items, NO `methodValue`/`bodyValue` strings anywhere; list
  endpoints resolve display names server-side (joins inside one schema are cheap;
- **Technical constraints**: OPS-verified version train via `lumen-bom` (no version
  edits); Flyway-only schema changes pinned to the `setting` schema (never public);
  serial Integer PKs + EntityBase audit tail; `@Data @Builder @AllArgsConstructor
  @NoArgsConstructor` on entities; Hutool-first utilities; zero physical FKs
  (convention references + service-layer guards); never-modify-existing-methods
  (additive DI); JDK 11 reactor builds from `lumen-sev/`.

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
11:- [ ] 1.1 Add `lumen-sev/lumen-setting`: pom (bom-inherited versions), `SettingApplication`
18:- [ ] 1.2 Write `lumen-setting/src/main/resources/db/migration/V1__setting_schema.sql`:
27:- [ ] 1.3 Entities (6, `@Data @Builder @AllArgsConstructor @NoArgsConstructor`,
33:- [ ] 2.1 DTOs (`dto/`: `BodyGroupCreateRequest`, `BodyGroupUpdateRequest`,
39:- [ ] 2.2 `ExamBodyService`: `GET /list` returns up_id-nested tree scoped by
46:- [ ] 2.3 Perm pins: `@PreAuthorize` `setting:body-group:*` / `setting:body:*` strings
51:- [ ] 3.1 DTOs + `ExamMethodService`: page (modality + name/code keyword), list
56:- [ ] 3.2 Perm pins `setting:method:*` (same annotation-test pattern).
60:- [ ] 4.1 DTOs (`ItemCreateRequest`, `ItemUpdateRequest`, `ItemPageQuery`) +
66:- [ ] 4.2 Controller `ExamItemController` (`/api/v1/setting/exam-items`) + perm pins
71:- [ ] 5.1 DTOs + `RepTempService`: tree (modality+type, private scoped to requester),
77:- [ ] 5.2 Perm pins `setting:temp:*` (annotation tests).
81:- [ ] 6.1 `lumen-system/.../db/migration/V2x__exam_setting_menu.sql`: L2 「检查设置」
87:- [ ] 6.2 Register the new test fixture prefix family in the smoke-sweep hard-delete

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: item page group-filter join semantics;
  item create/update duplicate-code rejection; item+binding transactional rewrite
  (failure injection — mid-transaction failure leaves item+bindings unchanged);
  modality-vocabulary rejection of unknown 1021 values; delete guards naming the
  blocking resource (group→bodies, body→children/items, method→bindings);
  rep-temp owner binding server-side on private create + non-owner write refusal
  without content leak; unknown-modality write rejection.
- **Required edge cases**: soft-deleted rows free codes/names for reuse (partial
  unique `WHERE deleted = 0`); body parent must belong to the same group; body
  modality must match its group's; null-clears on full item update; private tree
  listing never leaks other users' rows.
- **Regression-sensitive areas**: migration idempotence (V1 re-run + menu/dict seed
  re-run are row-count no-ops — AuthExamSettingSeedTests); dict 1021 must gain DR
  without disturbing CT/MR/US/ENDO rows; aggregator reactor still builds when
  `lumen-setting` is absent from a stale checkout's target dirs.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 14 tasks, single module (plus the one same-stack seed touch
  in lumen-system migrations), no cross-module runtime deps, zero-infrastructure test
  suite — same profile as org-device-room-api (Batch Inline, 12 tasks).

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
