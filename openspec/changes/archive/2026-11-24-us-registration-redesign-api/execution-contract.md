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

- **Change name**: us-registration-redesign-api
- **Problem to solve**:
The kernel now owns the pre-registration substrate (①: `booking_time` slot anchor, derived
entry-status policy, per-modality slot capacity validation), but nothing can reach it. The US
create surface fixes every entry to `BOOKED` (`RegistrationOrderServiceImpl` — "entry state
FIXED BOOKED"), the list query exposes only three conditions (status/accessionNo/patientName),
no status counts, no saved searches, and the slot-capacity policy has no admin surface. The
redesigned two-state workbench (拍板 01-07) needs this entire backend contract before its
pages can build against the real backend (后端先行、禁 mock — user ruling 2026-11-05).

<!-- AI: summarize the above into 1-2 sentences -->
- **Summary**: proposal ② of us-registration-redesign — the US create composes ①'s
  pre-registration machinery (derived entry status + capacity refusals), the workbench gains
  its wide query + status counts, the slot policy gets its CRUD, saved searches get the
  generic system REST, and the menu seeds land 预约设置 — ALL of the feature's backend, so
  proposal ③ builds pages against real endpoints.
- **In scope**:
  - US V4: kernel V2 DDL vendored verbatim (booking_time + exam_slot_config into the us schema)
  - Create contract: optional bookingTime → derived entry status in ONE transaction
    (null → REGISTERED; future → capacity-guarded BOOKED); kernel A0400 refusals verbatim;
    result/view carry bookingTime; snapshot stores cardNo
  - Wide query (拍板 03-B): status multi-select + patient-id/patient-source/card-no (snapshot
    jsonb) + exam-item name fuzzy + apply-time (=create_time) range + status-count endpoint
    (zero-filled six statuses)
  - Slot config CRUD (拍板 06-A): /api/v1/us/slot-configs in lumen-us (the us schema owner —
    D7 same-schema lock), perms setting:slot:*, edit + soft-delete only
  - 我的查询 (拍板 07-A): search_filter table (system schema, V50) + /api/v1/system/search-filters
    (uid-owned, authenticated-only, default full-replace)
  - Menu V51: 预约设置 leaf + slot F buttons under the EXISTING 检查配置 directory (additive
    only — the rename already shipped; design D6 correction)
  - Kernel additive key: PatientSnapshot.cardNo (ExamItemRef.modality precedent)
- **Out of scope**:
  - All web/pages (proposal ③ web-us-station-redesign)
  - Radiology/endo adoption (future vendoring)
  - Kernel service/state-machine changes (no behavior delta beyond the additive snapshot key)
  - lumen-feign changes (no new inter-service contract)
  - 孕检 ext block changes (拍板 05 is UI-side)
  - Slot entities/reservation (v2 号池)

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/api/spec.md`
6:### Requirement: Saved Search Filter Endpoints
17:#### Scenario: List returns the caller's filters for a page
22:#### Scenario: Create stores a named filter
28:#### Scenario: Set default replaces the previous default
34:#### Scenario: Update and delete touch only the owner's rows
41:#### Scenario: Unauthenticated access is refused
- `specs/system-data/spec.md`
7:### Requirement: Exam Setting Menu Pages
36:#### Scenario: Fresh deployment seeds the exam-setting console group
43:#### Scenario: Migrated database keeps non-admin grants alive
51:#### Scenario: Re-execution is a no-op
57:#### Scenario: The 预约设置 leaf seeds additively
64:### Requirement: Exam Setting Button Perms
71:#### Scenario: Admin role receives the button grants
79:### Requirement: Search Filter Table
89:#### Scenario: The table lands with the house shape
95:#### Scenario: One default per user and page
- `specs/us-station/spec.md`
7:### Requirement: Registration Surface
27:#### Scenario: Create a booked order
40:#### Scenario: Invalid pre-registration is refused verbatim
49:#### Scenario: Below-capacity pre-registration occupies the slot
56:#### Scenario: The create result carries the derived state
62:#### Scenario: Illegal transition is refused verbatim
68:#### Scenario: Cancel carries a reason
74:#### Scenario: Modality reaches the issuer port
82:### Requirement: Workbench Query Surface
96:#### Scenario: Status multi-select composes with the wide conditions
101:#### Scenario: Snapshot conditions hit the jsonb document
107:#### Scenario: Exam-item name matches fuzzily across the jsonb array
113:#### Scenario: Apply-time range bounds the registration moment
118:#### Scenario: Status counts respect the filters
126:### Requirement: Slot Config Administration
140:#### Scenario: V4 vendoring lands both kernel artifacts
147:#### Scenario: Create stores one trimmed live row per modality
155:#### Scenario: Update edits the policy, never the pool identity
160:#### Scenario: Delete is soft
166:#### Scenario: List returns the live configs
171:#### Scenario: The perms family gates every action

All 27 scenarios confirmed — the 3 preserved Registration Surface scenarios (Illegal
transition / Cancel / Modality) are regression-guarded by the existing US suites; the menu
superset preserves the V28-V34 scenarios verbatim with the additive leaf scenario.

## Design Constraints

- **Architecture/Decisions**:
  - D1 composed create: TransactionTemplate wraps [policy guard → config FOR UPDATE →
    resolve → own-schema count → validate → derive → registerOrder]; the port resolve stays
    OUTSIDE the tx (kernel D8); null bookingTime walks the unchanged legacy path; the
    validation modality = the FIRST exam item's token (issuer-scope precedent, no token =
    fail-closed 未开通预登记); the count scopes the window by that modality
    (exam_items->0->>'modality' — single-项目 orders, D5 ① precedent); AMENDMENT:
    resolveSlotWindow(null) refuses A0400 fail-closed (the composed flow's resolve
    surface, TDD-caught — the ① unconfigured scenario on both public surfaces)
  - D2 slot CRUD in lumen-us (us schema owner — D7 same-schema lock forced); URL
    /api/v1/us/slot-configs; perms setting:slot:*; token trim + live-unique A0400; NO dict
    re-validation; NO enable column
  - D3 search domain org.birdbro.lumen.search in the system jar; URL /api/v1/system/search-filters;
    authenticated-only no-perm (dict-lookup precedent); set-default full-replace in-tx,
    partial unique index as backstop
  - D4 wide-query predicates: snapshot keys patId/patientSource/cardNo exact, name LIKE,
    exam_items per-element jsonb_array_elements_text LIKE, apply-time = create_time, status IN
  - D5 status counts zero-filled across all six states, filters minus status
  - D6 menu V51 additive-only — the 检查设置→检查配置 rename ALREADY shipped
    (menu-settings-root 2026-11-13); no rename rides ②
  - D7 PatientSnapshot.cardNo additive jsonb widening (no kernel spec delta — the shape has
    no dedicated requirement)
  - D8 bookingTime payload format yyyy-MM-dd HH:mm:ss (house @JsonFormat), Date; kernel floors
  - D9 transitional break ACCEPTED (old page: submit → REGISTERED; 登记 button A0400s until ③)
  - D10 migration numbering: US V4; system V50 (search_filter) + V51 (menu leaf/buttons)
- **Technical constraints**:
  - A0400 refusal wording is contract — the four D5(①) messages surface VERBATIM through the
    US surface; tests pin the tokens
  - The kernel V1/V2 sources stay UNTOUCHED; V4 is a verbatim copy (comment lines carry no
    semicolons — V36 lesson)
  - Never modify existing public methods — additive DTO fields/overloads only; the create
    service's new flow rides additive composition (TransactionTemplate), not signature changes
  - Entity Lombok combo; Date types; Hutool-first; JDK 11 reactor from lumen-sev/
  - Tests on the shared server (us/system schemas): unique tokens + @Transactional rollback
    where the harness allows, hard-delete sweeps otherwise; system V50/V51 seed tests follow
    the CONTENT-guard/no-op re-execution discipline
  - Both services' application.yml already pin their own flyway schemas (never-public ruling)
  - Hero scope: no jsonb expression indexes, no compat mode for the old page, no extra
    migration guards beyond the house seed discipline

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
- Batch 1 — US V4 + snapshot key (tasks 1.1–1.4): RED schema guard → V4 verbatim; kernel
  PatientSnapshot.cardNo widening + round-trip; US buildSnapshot sets cardNo
- Batch 2 — create contract (tasks 2.1–2.5): RED derive branches / refusals verbatim /
  below-capacity occupies / result carries state; GREEN DTO widening + TransactionTemplate
  composition + view layering
- Batch 3 — workbench query (tasks 3.1–3.6): RED per condition family + counts; GREEN query
  DTO + count endpoint
- Batch 4 — slot CRUD (tasks 4.1–4.3): RED service rules + perm gates; GREEN controller stack
- Batch 5 — system V50/V51 (tasks 5.1–5.4): RED table/default guard + REST rules + menu
  seeds; GREEN migrations + search domain
- Batch 6 — verification (tasks 6.1–6.3): reactor run; /opsx:verify credential; registry →
  ② verified

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**:
  - V4 schema artifacts land in the us schema (1.1 RED)
  - create derives REGISTERED (null) / BOOKED (future, below capacity) (2.1/2.2 RED)
  - the four A0400 refusal tokens verbatim, nothing persists (2.3 RED)
  - snapshot stores cardNo (1.4 RED); cardNo round-trip (1.3 RED)
  - every wide condition family + status counts zero-filled (3.1–3.5 RED)
  - slot CRUD rules incl. duplicate A0400 / token immutable / soft delete / perms (4.1–4.2 RED)
  - search_filter REST + one-default guard + menu leaf seeds (5.1–5.3 RED)
- **Required edge cases**:
  - bookingTime exactly at DB now → REGISTERED (the kernel boundary rides through)
  - exam-item fragment matching the itemName only (not other jsonb keys)
  - status-count zero-fill for a status with no rows
  - set-default twice → previous default off; other user's default untouched
  - duplicate modality create → A0400 (both API and index backstop)
  - old-shape snapshot rows (no cardNo key) read null and match nothing on card-no queries
- **Regression-sensitive areas**:
  - existing US suites (create/page/register/cancel/e2e) — the FIXED-BOOKED flip rewrites
    their entry-status expectations; every touched assertion must be re-pinned to the
    derived semantics, never blanket-deleted
  - kernel snapshot jsonb round-trip (patient domain poller + snapshot refresh unaffected)
  - system menu seeds (V28-V34 discipline; the additive leaf must not disturb the no-op
    re-execution scenario)

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: single agent (backend), 22 implementation tasks across two
  services with precise D1-D10 decisions and ①'s machinery already verified; batches
  implemented sequentially in-context with per-batch WHEN/THEN review checkpoints. The SDD
  subagent machinery earns its cost on parallel-stack coordination, not a one-agent delta.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after each batch — diff vs spec WHEN/THEN; after batch 2 —
  the transitional-break behavior explicitly re-confirmed (D9); before the reactor run —
  the contract constraint sweep (verbatim tokens, verbatim V4, no-semicolons, additive-only)
- **Blocking categories**: Critical / Important findings on the four D5(①) refusal tokens,
  the FIXED-BOOKED flip semantics, or the uid-scoping of search filters

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
