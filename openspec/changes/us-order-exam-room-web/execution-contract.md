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
- **Problem to solve**: The backend half (us-order-exam-room-sev) makes the exam room a
  required create field and exposes the station room directory; the workbench must
  collect it at BOTH entry points (booking dialog for pre-registered orders, section-3
  for walk-ins) and show it on the list, or the field is untrustworthy at the desk.
- **In scope**: `listStationRooms()` client; the section-3 required 检查室 Select
  (top of the left column, last-choice default via localStorage `us-last-room`); the
  booking-dialog footer Select gating 确定 on slot AND room; list display (card meta
  line + wide 检查室 column, '—' fallback).
- **Out of scope**: any backend change; editing rooms on existing orders (future
  nurse-edit proposal); room↔slot binding; org/dep ids crossing the wire.

## Approved Behavior

- `specs/web-us-station/spec.md`
  - Order Form and Actions (MODIFIED superset) — "The room is required and rides the
    payload" (client-side refusal; `roomId` posted; last-choice persistence)
  - Order List (MODIFIED superset, post-status-dict text) — "List renders with state
    and identity" now includes the 检查室 renders
  - Booking Slot Dialog (MODIFIED superset) — "Confirm requires slot AND room"; the
    cleared-booking scenario pins the room choice surviving the clear

## Design Constraints

- **Architecture/Decisions**: `design.md` D1–D5 — station-scoped client (no params);
  empty-list + message on load failure (the required rule blocks independently);
  section-3 select at the standard 32px control height in the left column top row (the
  summary bar stays input-free); one localStorage key shared by both pickers, stale ids
  silently dropped; 确定 gates on `dlgSlot && roomId`; clearing the booking never clears
  the room; card display rides an EXISTING meta line (card-flow ruling); stacking on
  registration-order-status-dict-web.
- **Technical constraints**: TypeScript strict; AntD 5 + design tokens (no ad-hoc hex);
  tests stub API client modules at the seam (`vi.mock('@/api/...')`); no mock page
  data (backend-first ruling).

## Task Batches

- **Batch 1 (client, TDD)**: tasks 1.1–1.2 — client test → `listStationRooms()`.
- **Batch 2 (section-3 select, TDD)**: tasks 2.1–2.2 — workbench refusal/payload/
  default tests → the select implementation.
- **Batch 3 (booking dialog, TDD)**: tasks 3.1–3.2 — gating/confirm/clear tests → the
  footer Select.
- **Batch 4 (list display, TDD)**: tasks 4.1–4.2 — render tests → card meta line +
  wide column.
- **Batch 5 (regression)**: task 5.1 — `pnpm test` exit 0.

## Test Obligations

- **Behaviors requiring failing-test-first**: client read shape; client-side roomless
  refusal; `roomId` in both payloads; last-choice default + write-back; 确定 gating;
  room surviving the booking clear; card/wide room renders with '—' fallback.
- **Required edge cases**: stale localStorage id not in the directory → empty Select +
  required rule; load failure → empty options + message; null room row → '—'.
- **Regression-sensitive areas**: the booking dialog's existing slot machinery
  (badges/capacity/confirm composition untouched); the post-dict chip/status surfaces
  this page already migrated.

## Execution Mode

- **Mode**: `Batch Inline`
- **Selection rationale**: 5 batches, single module (lumen-web), no cross-module
  dependencies, no risk indicators beyond the sequencing note (apply after
  status-dict-web) which is a coordination gate, not a task-risk.

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: the stacking gate (status-dict-web landed before
  touching the page); DP-3 user approval of this contract.
- **Blocking categories**: page-level behavioral changes; new API client.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong
  (e.g. the placement ruling changes); interfaces change materially.
- **When NOT to continue**: contract intent lock no longer matches proposal scope.
