# Design: us-slot-list-filter (retrospective closure)

> Written retrospectively at archive time — the filter landed with the exam-slot
> page batch (commits 99d588e/8e4416c), verified PASS 2026-11-25.

## Decisions

- **D1 — client-side narrowing**: the filter rides the mount-time
  listSlotConfigs payload; no additional list request (the scenario pins the call
  count).
- **D2 — dict-1024 sourcing**: the filter options come from the same dict items
  the drawer's select uses (the id-anchor discipline).

## Open Questions

(none)
