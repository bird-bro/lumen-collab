# Design: workstation-context-web

## Context

The auth store holds the session (perms drive `usePerm`); `BasicLayout` renders the
OPS three-zone shell (zone 2 header carries the product title at its left edge) and
the secondary sidebar renders the active menu tree. The backend payloads gain
`workstations` + `profile.primaryWorkstation` (workstation-context-api). See
proposal.md for the why.

## Goals / Non-Goals

**Goals:**
- Chip + switch + filter mechanism with the mapping locked at compile time; the
  assignment affordance in 用户管理; all of it test-first.

**Non-Goals:**
- No department page routes (none exist); no per-station theming (v2); no server-side
  work; no changes to existing gating behavior.

## Decisions

- **D1 - Stations derive from the granted tree** (revised 2026-11-06, user ruling:
  workstation = type-W menu node; per-user grant machinery torn down): the chip's
  station list is `menus.filter(m => m.type === 'W')` — no payload array, no closed
  frontend enum, no assignment surface in 用户管理. workstations.ts shrinks to the
  APPEND filter + type helpers.
- **D2 - The auth store keeps `activeWorkstation: number | null`** (the W node's mid;
  null = console view). Initialized to null at login; auto-activates when exactly one
  W node is granted; session-scoped (re-login returns to console). The sidebar APPEND
  filter: admin-domain L1s (type M) resident + the active W node's children beside
  them; 控制台 byte-identical.
- **D3 - The chip is a small component composed into zone 2** left of the title;
  entries carry the W node's title/icon; selecting navigates to the station's first
  leaf route (`firstLeafRouteOf` — already in menu-paths).
- **D4 - roles 授权 covers W nodes automatically** (GrantTree renders them with a
  工作站 tag) — no new grant surface. The users-page drawer is DROPPED.
- **D5 - Tests: registry/filter units, chip render, APPEND integration, menu-form W
  option** — mock seam as established.

## Risks / Trade-offs

- [Filter dead code until department domains land] → accepted and recorded: the
  mechanism ships with v1 so department pages need zero auth rework; the admin
  short-circuit keeps today's behavior byte-identical.
- [Chip in the header touches the shared layout] → one composed component, additive;
  layout tests assert the chip's presence/absence by payload.
- [Store shape change ripples to tests] → additive fields with defaults; existing
  tests set `perms` only and keep passing.

## Migration Plan

Deploy after `workstation-context-api` (backend-first). Rollback = revert the frontend
commits; the payloads' extra fields are ignored by an older build.

## Open Questions

(none - scope pinned by the 2026-11-06 user ruling: full stack incl. assignment UI)
