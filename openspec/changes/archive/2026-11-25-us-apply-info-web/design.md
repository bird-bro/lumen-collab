# Design: us-apply-info-web (retrospective closure)

> Written retrospectively at archive time — the implementation landed first (the
> 请检信息 origin trio + the ext submit, verified PASS 2026-11-25). Coordination
> parent: `openspec/coordination/us-apply-info.md`.

## Context

The sev sibling stores the HIS origin trio on `us_registration_ext`; the web side
collects it in the 请检信息 section and submits it inside the create body's ext block.

## Goals / Non-Goals

**Goals:** three text inputs (申请科室/申请医生/申请时间) with the form's section
chrome; optional strings, blank allowed; submitted via the ext block.

**Non-Goals:** list-row rendering of the trio (explicitly out of this change);
dict/vocab coupling (HIS identities are external free text); the reserved id
anchors stay unsent.

## Decisions

- **D1 — plain inputs in the 请检信息 section** (the form's three-column chrome,
  the 88px label ruling), no drawer/dialog of their own.
- **D2 — blank-tolerant submit**: empty strings trim to null on the sev side; the
  web sends the ext block with whatever the operator entered.
- **D3 — the api type mirrors the ext contract** (`UsRegistrationExtBody` in
  `src/api/us-orders.ts` — the self-contained module note predates the sibling's
  parallel work there).

## Risks / Trade-offs

- [The trio renders even when the operator has no HIS origin] → optional strings,
  blank allowed — the sev side stores NULLs and the view returns null.

## Open Questions

(none)
