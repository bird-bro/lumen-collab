# Design

## Context

Migrations V6/V7/V9 (plus the matching backend/frontend code) already shipped the
slimmed dictionary model to the shared test database — user rulings 2026-11-05, applied
through normal lumen-system boots and verified by the green 65-test lumen-system suite.
The `system-data` spec predates them and still describes the double-anchor/label model.
See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- One delta file re-speccing the three dict requirements to the landed model, worded so
  each scenario maps onto an existing test (L2 evidence without new test code).
- Keep the endpoint/error-code contract untouched (`?caCode=` param survives as the
  query key; `A0714`/`A0400` semantics unchanged).

**Non-Goals:**
- No menu/IA spec content — nothing today enumerates the menu seed; menu requirements
  belong to the queued `workstation-context` change.
- No code, migration, or test changes; no new capability directories.

## Decisions

- **D1 — Spec catches up to shipped reality (sync-not-revert).** The migrations are the
  ground truth: the shared test DB already converged, the frontend types/pages already
  match, and the suite is green. Alternative readings rejected: reverting V9 (throws
  away a user ruling), leaving the spec stale (the next spec-reading change
  re-implements dropped columns), or re-speccing menus here (no existing menu
  requirement to modify; would invent one just to satisfy the delta — the instructions
  forbid that).
- **D2 — Scenario wording keyed to observable behavior, not column archaeology.** The
  delta states the model (fields/anchor/uniqueness/seed counts) and keeps the
  `?caCode=` query contract at the spec level; the caId-resolution mechanism stays in
  design/tasks land (it is already implemented).

## Risks / Trade-offs

- Parallel-session migrations (V11+) could touch dict tables and re-stale the delta —
  task 1.1 re-checks before apply.
- The seed-count scenario (7/30) is data-shaped; if a later seed migration adds
  catalogs, the delta needs a follow-up tick — acceptable for a sync change, flagged in
  tasks 2.2.
