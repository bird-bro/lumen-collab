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

- **Change name**: us-order-exam-room-web
- **Problem to solve**:
The backend (us-order-exam-room-sev) makes the exam room a required create field and
exposes the station's room directory; the workbench must collect it at BOTH entry
points — the booking dialog stamps 预登记单 with a room chosen at booking time (user
ruling 2026-11-26: 预约=登记, room chosen at booking, no arrival re-confirmation), and
the walk-in path picks the room in the 检查信息 section — and the list must show the
assignment, or the field would be untrustworthy at the desk.

<!-- AI: summarize the above into 1-2 sentences -->
- **In scope**:

- **Out of scope**:
<!-- AI: extract 'Out of scope' items from the scope section above -->

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/web-us-station/spec.md`
5:### Requirement: Order Form and Actions
37:#### Scenario: Create posts identity, items, and extension
44:#### Scenario: The room is required and rides the payload
54:#### Scenario: Pre-registration time posts the slot anchor
62:#### Scenario: Item modality rides the create payload
68:#### Scenario: 登记 transitions the order
77:#### Scenario: Cancel requires a reason and confirmation
83:#### Scenario: Illegal transition surfaces verbatim
88:### Requirement: Order List
120:#### Scenario: List renders with state and identity
129:#### Scenario: Status chips carry the meter and multi-select
141:#### Scenario: Wide conditions compose through the query row
148:#### Scenario: 我的查询 persists and auto-applies the default
155:### Requirement: Booking Slot Dialog
189:#### Scenario: The dialog opens on tomorrow with the grouped grid
198:#### Scenario: The full slot is grayed and the tight slot emphasized
205:#### Scenario: Confirming a slot writes the date+slot pair
212:#### Scenario: Confirm requires slot AND room
223:#### Scenario: Blank or cleared keeps the walk-in path
231:#### Scenario: Unconfigured or failed lookup degrades without blocking


## Design Constraints

- **Architecture/Decisions**: `design.md` D1–D5 — station-scoped client (no org/dep
  ids on the wire); empty-list + message on load failure (the required rule blocks
  independently); the section-3 select at the standard control height in the left
  column's top row (the summary bar stays input-free); one localStorage key
  (`us-last-room`) shared by both pickers, stale ids silently dropped; 确定 gates on
  `dlgSlot && roomId`; clearing the booking never clears the room; the card display
  rides an EXISTING meta line (card-flow ruling); stacking on
  registration-order-status-dict-web resolved (archived 2ac1a99).
- **Technical constraints**: TypeScript strict; AntD 5 + design tokens (no ad-hoc
  hex); tests stub API client modules at the seam (`vi.mock('@/api/...')`); no mock
  page data (backend-first ruling).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
- **Batch 1 (client, TDD)**: tasks 1.1–1.2 — DONE (RED export missing → GREEN).
- **Batch 2 (section-3 select, TDD)**: tasks 2.1–2.2 — DONE (RED ×4 → GREEN 4/4;
  the 4 pre-existing describes retrofitted with the room seam + seed).
- **Batch 3 (booking dialog, TDD)**: tasks 3.1–3.2 — DONE (RED ×3 → GREEN 3/3).
- **Batch 4 (list display, TDD)**: tasks 4.1–4.2 — DONE (RED ×2 → GREEN 2/2; the
  examRoom td realigned between items/status after an insertion-position slip).
- **Batch 5 (regression)**: task 5.1 — DONE (`pnpm test` exit 0; 534/534, 36 files).
8:- [x] 1.1 Client test FIRST: `listStationRooms()` GETs `/api/v1/us/rooms` and returns
10:- [x] 1.2 Implement the client in `src/api/us-orders.ts` — green
14:- [x] 2.1 Workbench test FIRST: submitting without a room refuses client-side
18:- [x] 2.2 Implement the section-3 top-row Select (options from the client; the
24:- [x] 3.1 Dialog test FIRST: 确定 disabled until slot AND room; confirming composes
26:- [x] 3.2 Implement the footer Select (same source + last-choice default; the
31:- [x] 4.1 List test FIRST: the compact card's meta line shows `roomName`, the wide
33:- [x] 4.2 Implement both renders (card meta line + wide column with ellipsis/title) —
38:- [x] 5.1 Run the page/unit suite once green — verify: `pnpm test` exit 0

## Test Obligations

- **Behaviors requiring failing-test-first**: client read shape (no params on the
  wire); client-side roomless refusal; roomId in the submitted payload; last-choice
  pre-select + write-back; 确定 gating (slot AND room); room surviving the booking
  clear; card/wide room renders with the '—' band.
- **Required edge cases**: stale localStorage id not in the directory → empty select
  + required rule; directory load failure → empty options + the failure message;
  null room row → '—'.
- **Regression-sensitive areas**: the booking dialog's existing slot machinery; the
  post-dict chip/status surfaces; the 4 retrofitted describes' submit paths.

## Execution Mode

- **Mode**: `Inline` | `Batch Inline` | `SDD`
- **Selection rationale**: <!-- AI: choose based on task count and complexity -->

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
