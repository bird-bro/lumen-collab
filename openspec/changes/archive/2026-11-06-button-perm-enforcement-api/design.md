# Design: button-perm-enforcement-api

## Context

The enforcement stack is in place (server-side-perm-enforcement: session perms →
authorities → `@PreAuthorize` → `AuthDenyAdvice` A0301) and the menu-management family
proved the F-node model end to end (auto-grant, binding cleanup, `/me` delivery). What
is missing is content and pins: zero F nodes seeded, write endpoints class-gated only.
The coordination doc's perms table is the single source of truth (22 buttons, 7
controllers); see proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- One idempotent seed migration; one method-level annotation per write endpoint; a
  permission-matrix test proving the deny/pass semantics at button granularity.

**Non-Goals:**
- No new endpoints, no request/response shape changes, no `UserLogin`/session changes,
  no frontend work (that is `button-perm-enforcement-web`), no menu-model changes, no
  workstation coupling.

## Decisions

- **D1 - One migration, CONTENT-guarded statements.** `V15__button_perm_nodes.sql`
  inserts each F node behind a `NOT EXISTS` guard on its live `(up_id, type, perms)`
  content, and the role-1 grants behind the NOT-EXISTS live-binding guard. Sequence-driven
  ids need no `setval`. (Implementation refinement recorded at apply time 2026-11-06: the
  first draft's explicit ids 40-61 + `ON CONFLICT (id)` silently inserted ZERO rows on the
  shared test server - soft-deleted ut-probe rows occupied the id block and ON CONFLICT
  yields to dead rows as readily as live ones. Content keying is idempotent by content,
  indifferent to dead-row history, and immune to id-block collisions; the aborted
  draft's history row was removed before re-applying - never repair-and-diverge.)
  Every statement is a no-op on an already-migrated database (shared test server can be
  hand-applied before the next boot).
- **D2 - Method-level annotation replaces the class gate for that method.** Spring's
  `@PreAuthorize` resolution takes the most specific annotation, so pinning
  `hasAuthority('system:user:create')` on `create` means the class-level
  `system:user:list` no longer applies to that method. This is the recorded model (the
  button node is the ACTION authority; the page node stays the VISIBILITY authority via
  the tree/list gates). Alternative rejected: `hasAnyAuthority('...list','...create')`
  would make a page-only grant pass writes (no button granularity) and a
  button-only grant fail (defeating the model).
- **D3 - Annotation placement is additive-only.** Method-level `@PreAuthorize` joins 21
  existing handler methods across 7 controllers (MenuController's three were born
  unpinned in menu-management-api). No signatures/bodies change - the sign-log-query b2
  method-level pins are the recorded precedent for annotating existing handlers.
- **D4 - Deny tests ride the existing matrix harness.** Extend
  `AuthPermEnforcementTests` with a button-level matrix: fixture role granted ONLY a
  page node → write denied A0301 (no row); fixture role granted ONLY a button node →
  write passes, list still denied; admin → passes everywhere. Fixture accounts/roles
  reuse the base-class sweep namespaces (register new prefixes if introduced).
- **D5 - Perms strings live in exactly one source** (the coordination inventory table);
  the migration, the annotations, the frontend gates, and the tests all quote it. A
  drift there is a spec bug, not a code bug.

## Risks / Trade-offs

- [Existing non-admin roles lose button affordances after deploy] → intended tightening;
  the seed note and the zh brief call it out so operators re-grant deliberately.
- [A missed pin leaves a write page-gated only] → the matrix test enumerates ALL 22
  endpoints from the inventory table; a missing pin fails the matrix, not production.
- [Annotation/class-gate interaction surprises future readers] → each pinned method
  carries a one-line javadoc pointer to the button-perm model (D2).

## Migration Plan

Deploy = rebuild `lumen-system`; Flyway applies V15 on boot (hand-apply on the shared
test server first, per the V5/V15 pattern). Rollback = redeploy previous artifact; the
V15 rows are additive data (F nodes + grants) and harmless to an older build - no
down migration needed.

## Open Questions

(none - scope pinned by the 2026-11-06 user ruling: full landing)
