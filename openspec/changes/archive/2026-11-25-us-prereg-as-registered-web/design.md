# Design: us-prereg-as-registered-web

> Coordination parent: `openspec/coordination/us-prereg-as-registered.md`.
> Builds against the REAL sev backend (no mock layer — user ruling 2026-11-05).

## Context

`src/pages/us/registration/index.tsx` renders the list chips, the compact rail, the wide
table, the row actions, and the bench-foot hint; `src/api/us-orders.ts` types the view.
The sev sibling adds `registrationType` (and `createBy`) to the view and stops producing
BOOKED rows.

## Goals / Non-Goals

**Goals:**
- Remove the 登记 action; keep 取消
- Render the 现场/预约 category tag from `registrationType`
- Default chip selection 已登记; rewrite the hint; relabel the time filter 创建时间

**Non-Goals:**
- Operator (createBy) name display — the uid lands in the API; a name column needs a
  system-domain lookup and defers to a later proposal
- The 预登记时间 picker, the slot picker, and the refusal surfaces — untouched
- 我的查询 / column settings — untouched

## Decisions

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
  shared test DB, the operator can toggle its chip manually.
- **D4 — Filter relabel is label-only.** `applyTimeStart/End` params and the backend
  binding stay (us-station spec names them); only the visible label + the aria-labels
  change to 创建时间起/止. The reset handler and 我的查询 persistence keys are untouched
  (saved searches keep their param names).

## Risks / Trade-offs

- [Saved searches named under the old label still filter by create_time] → correct
  behavior, only the visible label changed; no migration.
- [Page tests pinning the 登记 flow] → rewritten to the new semantics in the same task
  (no characterization debt — the flow is being retired deliberately).

## Migration Plan

Ship after us-prereg-as-registered-sev is user-verified (frontend builds against the
real backend); no data migration.

## Open Questions

(none)
