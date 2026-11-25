# Design: us-apply-info-sev (retrospective closure)

> Written retrospectively at archive time — the implementation landed first (V5 +
> view fields, verified PASS 2026-11-25); this records the decisions the code
> already embodies. Coordination parent: `openspec/coordination/us-apply-info.md`.

## Context

lumen v1 has no HIS feed — the 检查申请单 origin (who opened it, where, when) arrives
as operator-entered free text on the registration workbench's 请检信息 section. The
US extension table is the US-owned home; the kernel aggregate stays department-clean.

## Goals / Non-Goals

**Goals:** the three display-origin fields persist with the order and flatten onto
the view; reserved id anchors ride along once an HIS feed surfaces them.

**Non-Goals:** dict catalogs or id anchoring for HIS identities (they are external —
no lumen org_dep / user rows exist for them); any parse policy on apply_time.

## Decisions

- **D1 — ext table, not the kernel aggregate.** The origin is display-only and
  US-hosted (radiology will host its own); the kernel aggregate rule forbids
  department fields on exam_order.
- **D2 — free text by coordination ruling.** `apply_dept/apply_doctor varchar(64)`,
  `apply_time varchar(19)` raw text (no parse, no state semantics in v1); the
  reserved `apply_dept_id/apply_doctor_id varchar(64)` anchors ride along.
- **D3 — V5 additive ALTER** on the shared-DB hand-apply discipline (nullable ADD
  COLUMN IF NOT EXISTS, appends after the audit tail — the column-set ruling test
  pins that order: D2 set, audit tail, origin trio, reserved ids).
- **D4 — audit columns stay server-owned**: the stashed ext is stripped of
  deleted/create/update stamps before the hook persists it (B3 review Minor-1).

## Risks / Trade-offs

- [Free-text HIS identities cannot join lumen catalogs] → accepted for v1; the id
  anchors reserve the upgrade path without a schema change.

## Open Questions

(none)
