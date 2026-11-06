# Design: workstation-w-nodes-api

## Context

The archived `workstation-context-api` built a per-user direct grant
(`user_workstation` + assignment endpoint + assign-ws pin + payload fields). The user
ruled it completely wrong (2026-11-06): permissions flow ONLY through roles; the
workstation is a menu node (type W). The chip/append frontend survives — its data
source becomes the granted tree's W nodes. See the coordination doc's REVISED section.

## Goals / Non-Goals

**Goals:**
- One tear-down + seed migration; removal of the direct-grant machinery; W accepted by
  the menu form validation.

**Non-Goals:**
- No frontend work here (workstation-context-web absorbs it); no per-station
  theming; no `remote` station; no dynamic station management beyond the menu tree.

## Decisions

- **D1 - W is a menu_info.type value, not new machinery.** The copied POJO lineage
  already documents an extra type (I 接口) — adding W follows the same open-enum
  shape. Validation: `@Pattern("M|C|F|W")` on the menu DTOs.
- **D2 - V22 = tear down + seed, one migration.** `DROP TABLE IF EXISTS
  user_workstation`; delete the assign-ws F node by perms + its role_menu rows;
  `ALTER TABLE menu_info DROP COLUMN IF EXISTS workstation`; delete the V21
  placeholder L1s (by id 298-300, they carry no children); content-guarded INSERT of
  the three W nodes (up_id=0, type='W') + admin auto-grant (NOT-EXISTS live-binding).
  Statement order respects FKs (role_menu before menu_info). Comment lines carry NO
  semicolons (the re-execution test splits on them).
- **D3 - Payload reverts; the tree is the source.** MeResponse/LoginResponse lose
  `workstations`; ProfileDto loses `primaryWorkstation`; AuthServiceImpl loses the
  assembler wiring; WorkstationService/UserWorkstation/mapper/DTO are deleted. The
  frontend derives stations from `menus.filter(type === 'W')`.
- **D4 - Tests: seed proof + removal proof.** AuthWorkstationSeedTests rewrites to
  assert the three W nodes (type/marker-by-type) + admin grants + re-run no-op;
  AuthWorkstationTests is DELETED (its scenarios are gone with the machinery); the
  404 proof for the removed endpoint lives in the controller-test sweep.

## Risks / Trade-offs

- [Primary-workstation UX is gone] → session-only selection (chip); a single granted
  W node auto-activates in the frontend; recorded in the coordination doc.
- [The archived change's synced specs are wrong] → this change's deltas REPLACE them
  at archive (REMOVED + ADDED); the archived artifacts stay as history.
- [menu_info rows 298-300 deleted with grants] → they are V21 test-era placeholders
  carrying no business data; their admin grants go with them.

## Migration Plan

Deploy = rebuild `lumen-system`; Flyway applies V22 (guarded statements). Rollback =
the dropped table holds no data worth keeping (test fixtures only).

## Open Questions

(none - the user ruled the direction explicitly)
