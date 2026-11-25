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

- **Change name**: us-prereg-as-registered-web
- **Problem to solve**:
With 预约=预登记 (the pre-registered order enters 已登记 at creation), the US
registration page's arrival-confirmation flow is wrong: the 登记 action, the
BOOKED-derived 预约 display inference, and the "到检后点登记确认" hint all encode the
retired two-stage model. The backend now returns `registrationType` — a business fact —
so the page stops inferring the category from `bookingTime`.

<!-- AI: summarized — the page retires the arrival-confirmation flow: drop the 登记
action, render the 现场/预约 category from the backend's registrationType, redefault the
chips, rewrite the hint, relabel the time filter 创建时间 -->
- **In scope**: `src/api/us-orders.ts` view type (+registrationType/createBy); the
category tag in wide table + compact rail; 登记 action + handler removal (取消 stays);
default chip selection 已登记; bench-foot hint rewrite; 申请时间→创建时间 filter relabel;
page-test updates in `src/test/us-registration-page.test.tsx`.

- **Out of scope**: operator NAME display (uid lands in the API; name lookup defers);
the 预登记时间 picker, slot picker, and refusal surfaces; 我的查询 / column settings;
any BOOKED-chip hiding beyond the default selection change.

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
3:### Requirement: Order Form and Actions
27:#### Scenario: Create posts identity, items, and extension
34:#### Scenario: Pre-registration time posts the slot anchor
42:#### Scenario: Item modality rides the create payload
48:#### Scenario: 登记 transitions the order
57:#### Scenario: Cancel requires a reason and confirmation
63:#### Scenario: Illegal transition surfaces verbatim
68:### Requirement: Order List
88:#### Scenario: List renders with state and identity
95:#### Scenario: Status chips carry the meter and multi-select
102:#### Scenario: Wide conditions compose through the query row
109:#### Scenario: 我的查询 persists and auto-applies the default


## Design Constraints

- **Architecture/Decisions**:
- **D1 — Category tag is a plain derived label.** `row.registrationType === 'WALK_IN' ?
  '现场' : '预约'` rendered as the existing state-tag chrome beside the status tag; the
  compact rail's inline `bookingTime ? 预约 HH:mm : HH:mm` collapses to the category tag +
  the time. No new component (the drawer-sections/chrome rulings don't apply to inline
  tags).
- **D2 — The 登记 action disappears entirely, not conditionally.** No US row can be
  BOOKED after the sev swap, so a conditional render would be dead code; the action
  constant and its handler go. The 取消 action and its Popconfirm flow stay (the
  Popconfirm test-flake family is already characterized).
- **D3 — Chip default: `['REGISTERED']`.** The zero-filled meter still renders the
  BOOKED chip (count 0) — hiding it is a bigger IA change than the ruling needs; the
  DEFAULT selection simply stops including it. If a legacy BOOKED row still shows in the
- **Technical constraints**: builds against the REAL backend (`pnpm dev` proxies
`VITE_API_ENTRY_URL`; no mock layer — user ruling 2026-11-05); ship AFTER
us-prereg-as-registered-sev is user-verified; page tests run in vitest/jsdom with the
house picker/Popconfirm patterns; the visible filter label changes ONLY — the
applyTimeStart/End param names and saved-search persistence keys stay (us-station spec
names the params).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
7:- [ ] 1.1 Extend the us view type in `src/api/us-orders.ts` with `registrationType` (and `createBy`) — additive; verify `pnpm build` exits 0
8:- [ ] 1.2 Render the 现场/预约 category tag from `registrationType` in the wide table and the compact rail, retiring the `bookingTime ? 预约 : time` inference; RED→GREEN in `src/test/us-registration-page.test.tsx` (rows with WALK_IN vs PRE_REGISTERED render the right tag)
12:- [ ] 2.1 Remove the 登记 row action and its handler (取消 stays with its Popconfirm flow); update the page tests — no 登记 affordance exists, cancel flow still passes, green
13:- [ ] 2.2 Change the default chip selection to 已登记; the BOOKED chip still renders its (zero) count; RED→GREEN in the chips test
14:- [ ] 2.3 Rewrite the bench-foot hint to 选未来时段即预约(预登记),患者按时段到检即可直接检查 (no confirmation step); assert the text in the page test
18:- [ ] 3.1 Relabel the wide time filter 申请时间 → 创建时间 (visible label + aria-labels 创建时间起/止; params applyTimeStart/End unchanged); RED→GREEN asserting the new labels and that the query params still bind
22:- [ ] 4.1 Full page-suite run (`pnpm test`) exits 0 — no regression in layout/menu-paths/require-auth suites
23:- [ ] 4.2 Manual pass against the real backend: create a walk-in (现场 tag) and a future-slot pre-registration (预约 tag, state 已登记), confirm no 登记 action on either row
24:- [ ] 4.3 `openspec validate us-prereg-as-registered-web` passes

## Test Obligations

<!-- AI: identified from the scenarios + task plan -->
- **Behaviors requiring failing-test-first**: category tag rendering per
registrationType (task 1.2); chip default 已登记 (task 2.2); filter relabel with params
still binding (task 3.1).
- **Required edge cases**: no 登记 affordance on any row while the cancel Popconfirm
flow still passes (task 2.1); the BOOKED chip renders its zero count; the hint text
pin (task 2.3); PRE_REGISTERED + WALK_IN rows both render.
- **Regression-sensitive areas**: full suite incl. layout/menu-paths/require-auth
(task 4.1); the Popconfirm full-run flake family; jsdom picker portal quirks stay
inside the untouched slot-picker tests.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 8 tasks, one module (lumen-web), one page surface with
sequential type→tag→actions→relabel ordering, no risk indicators — Batch Inline.

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
