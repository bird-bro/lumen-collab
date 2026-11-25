# Design: system-drawer-status-switch-web (retrospective closure)

> Written retrospectively at archive time — the drawer controls landed with the
> system console page batches, verified PASS 2026-11-25. UI pattern: the settled
> number-rule drawer Switch row (the drawer-switch-row-inline ruling 2026-11-19:
> label left, switch right, no dedicated row).

## Decisions

- **D1 — create drawers hide status entirely** and submit the record enabled (a new
  record starts alive; the field adds nothing at creation).
- **D2 — edit drawers render status as a Switch** in the common boolean-switch row,
  persisting through each page's existing save path (org/dept profile update APIs;
  the room status endpoint with `system:room:status`).
- **D3 — wire format**: the Switch boolean maps true/false to the 1/0 wire values
  the string/int columns already hold — no API change.

## Open Questions

(none)
