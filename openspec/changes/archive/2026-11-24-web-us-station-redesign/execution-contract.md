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

- **Change name**: web-us-station-redesign
- **Problem to solve**:
The current `/us/registration` page is unusable as a registration-desk workbench (no list
filters beyond three conditions, cramped 560px drawer, the lookup bar detached from the
form) and knows nothing of the pre-registration substrate the backend now ships: the
derived entry status, the slot capacity, the wide query conditions, the status meter, or
the saved searches. The 拍板 01-07 redesign replaces it with the two-state EIS-form
workbench and adds the 预约设置 console page — the last leg of the feature, which also
lights up the V51-seeded 预约设置 menu leaf and satisfies the batch-archive gate.

<!-- AI: summarize the above into 1-2 sentences -->
- **Summary**: proposal ③ — the FRONTEND leg: the /us/registration page rebuilt as the
  two-state workbench (rail+form ↔ wide query table) with pre-registration, the chips
  meter, 我的查询 + column settings, plus the 预约设置 console page — all against the
  verified ①② backends, no mock layer.
- **In scope**:
  - The two-state workbench shell (one route, edge toggle; compact = 392px rail + form;
    wide = full-width table + query row; the drawer retires)
  - The patient resolution flow (top resolve bar + the 就诊人候选 RCP-card dialog + the
    add-item dialog; the 急诊占位 client switch retired — the empty-name rule)
  - The 3-section form per the prototype (merges, gender-conditional pregnancy, 急诊 in
    the section title, 预登记时间 → bookingTime, 临床诊断 → ext.note, common chips + picked
    list; submit registers)
  - The four backend A0400 slot refusals verbatim near the picker; the 登记 arrival
    confirmation + cancel with reason; state-machine refusals verbatim
  - The chips meter (statusCounts, zero-filled, multi-select default 预登记+已登记) + the
    wide query row (150px controls, 更多条件 collapse) + apply-time range
  - 我的查询 (search-filters REST: save/apply/set-default/delete; default auto-applies) +
    column settings localStorage (`us-registration.columns`)
  - The 预约设置 console page (setting/exam-slot/index): the CRUD against
    /api/v1/us/slot-configs, the dict-1024 token SELECT immutable on edit, the windows
    pair editor, ConsoleFormHint fail-closed hint, setting:slot:* affordance gating
  - The API layer: us-orders.ts widened + us-slot-configs.ts / search-filters.ts new
- **Out of scope**:
  - Any backend change (①② frozen and verified)
  - Column settings backend persistence (localStorage per 拍板 07-A)
  - New routes/menu code (the leaf ② seeded; the component key maps through the existing
    dynamic-routing convention)
  - The old registration drawer's code path (removed wholesale — no dual-form maintenance)

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-system-console/spec.md`
6:### Requirement: 预约设置 Page
21:#### Scenario: The page lists the live policies
27:#### Scenario: Create saves a trimmed policy
34:#### Scenario: The modality token is immutable on edit
40:#### Scenario: Delete soft-deletes with confirmation
46:#### Scenario: Affordances hide without perms
- `specs/web-us-station/spec.md`
8:### Requirement: Registration Page Shell
19:#### Scenario: Page renders under the activated US workstation
25:#### Scenario: Actions hide without perms
30:#### Scenario: The edge toggle switches the two states
36:### Requirement: Patient Resolution Flow
48:#### Scenario: Exact id_no match fills demographics
54:#### Scenario: Soft-match candidates offer selection
60:#### Scenario: No match opens a new archive with placeholder path
66:### Requirement: Order Form and Actions
88:#### Scenario: Create posts identity, items, and extension
95:#### Scenario: Pre-registration time posts the slot anchor
102:#### Scenario: Item modality rides the create payload
108:#### Scenario: 登记 transitions the order
114:#### Scenario: Cancel requires a reason and confirmation
120:#### Scenario: Illegal transition surfaces verbatim
125:### Requirement: Order List
142:#### Scenario: List renders with state and identity
149:#### Scenario: Status chips carry the meter and multi-select
156:#### Scenario: Wide conditions compose through the query row
163:#### Scenario: 我的查询 persists and auto-applies the default

All 23 scenarios confirmed — the preserved-flow scenarios (resolution/登记/cancel/refusal)
restate their main-spec titles verbatim with the workbench-surface wording; the four
US-station requirements are supersets of every existing scenario.

## Design Constraints

- **Architecture/Decisions**:
  - D1 one route/one component/edge toggle; shared state lifted to the page root; compact
    = no query row; datenav hides in the wide state
  - D2 the rail = hand-laid compact rows (NOT ConsoleTable — the chrome invariants apply
    to the WIDE table only); one fetch, two renderings; one shared autorefresh interval
  - D3 the form sections per the prototype literally (merges, gender-conditional pregnancy,
    急诊 in the title row, 72px labels, three columns); common chips + picked list + dialog
  - D4 the picker posts yyyy-MM-dd HH:mm:ss verbatim; the four refusal tokens verbatim near
    the picker; success clears the form + refreshes
  - D5 the chips fetch counts minus status on load + after mutations; the wide row = 150px
    uniform controls + 更多条件 collapse; apply-time format matches the backend binder
  - D6 saved-search bar (save/apply/star/delete; default auto-applies after first load) +
    localStorage column settings
  - D7 the 预约设置 page = the house console CRUD skeleton (ConsoleTable ops via
    actions/onRefresh; ConsoleAction edit glyph default / delete explicit remove; drawer
    平铺单列 closable end maskClosable false; dict-1024 SELECT id-anchored disabled on
    edit; the windows pair editor; ConsoleFormHint fail-closed hint)
  - D8 the API layer through the existing client/envelope conventions — no new machinery
  - D9 vitest per the house harness; the antd Select textContent matching + the Popconfirm
    flake protocol apply
- **Technical constraints**:
  - REAL backend only — no mock layer, no src/api demo data (the 2026-11-05 ruling)
  - Existing-page edit (the registration page) — the prototype already user-confirmed, no
    new prototype round
  - The console drawer/table invariants (the 2026-11-20 three-time ruling) + the drawer
    rulings (close end top-right, sections not tabs, Switch inline) + ConsoleFormHint
    semantic colors
  - antd icons primary (@ant-design/icons; remix only as the documented fallback)
  - IDs serialize as numbers; the envelope code/data shape; the existing client instance
  - Vitest: page/DOM tests per the house harness; the full-run Popconfirm flake protocol

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
- Batch 1 — API layer + two-state shell (tasks 1.1–1.3): us-orders.ts widened; the shell
  RED→GREEN (compact default, edge toggle, no compact query row, datenav hide)
- Batch 2 — compact state (tasks 2.1–2.8): rail/chips/resolve/3 sections/submit RED→GREEN
  (the form behaviors TDD'd; the refusals verbatim pinned)
- Batch 3 — wide state (tasks 3.1–3.3): the query row + composed params + table RED→GREEN
- Batch 4 — 我的查询 + columns (tasks 4.1–4.3): the bar RED→GREEN + localStorage
- Batch 5 — 预约设置 page (tasks 5.1–5.4): list/drawer/delete RED→GREEN + the API bindings
- Batch 6 — verification (tasks 6.1–6.3): pnpm build + vitest; /opsx:verify credential;
  the batch-archive gate preflight (①②③)

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**:
  - the two-state shell toggle + the no-compact-query-row rule (1.2 RED)
  - the chips meter zero-fill + multi-select default (2.2 RED)
  - the resolution flow's three outcomes (2.3 RED)
  - submit=register (walk-in REGISTERED) vs pre-registration (bookingTime posts; the four
    refusal tokens verbatim) (2.7 RED)
  - the wide conditions compose the query params (3.1/3.2 RED)
  - 我的查询 save/apply/default auto-apply (4.1 RED); column settings persist (4.2 RED)
  - the 预约设置 page list/perm-gates/drawer/token-immutability/delete-soft (5.1–5.3 RED)
- **Required edge cases**:
  - the empty windows set renders the fail-closed hint and still saves
  - the token change attempt on edit is impossible (disabled SELECT — pinned)
  - a saved search with collapsed extras restores the collapsed fields
  - the default search auto-applies exactly once on mount (no double-fetch loops)
  - 登记 on a REGISTERED row surfaces the verbatim A0400 (the ② transitional pin)
- **Regression-sensitive areas**:
  - the existing us-orders api bindings (resolve/create/page/register/cancel signatures
    used by the surviving tests)
  - the PatientDemographicsForm shared component reuse (patient-domain forms)
  - the console shell kit conventions (the other pages unaffected)

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: single-agent frontend delta with the prototype as the visual
  contract and ②'s contracts pinned; batches sequential in-context with per-batch
  WHEN/THEN reviews. SDD earns nothing here — the surface is one page rewrite + one CRUD
  page.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: after each batch — diff vs the prototype section + the spec
  WHEN/THEN; before the build — the constraint sweep (no mocks, console invariants, refusal
  tokens verbatim)
- **Blocking categories**: Critical/Important on the pre-registration refusals, the chips
  meter semantics, or the console drawer invariants

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
