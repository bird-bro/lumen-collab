# Proposal — menu-settings-root

## Why

User ruling 2026-11-13: the exam-setting console group (检查设置, seeded by V28 as an
L2 directory under 系统管理) deserves its own first-level entry named **设置**, a
sibling of the system group — and 系统管理 renames to **系统**. The console IA then
reads: 系统 (admin console) · 设置 (hospital-wide settings) · the department
workstation W nodes.

## What Changes

- **lumen-sev only** — one new Flyway migration `V30__menu_settings_root.sql`
  (never rewrite the applied V28):
  1. Rename the system root (pinned id 1, the V2 seed): 系统管理 → 系统
     (content-guarded UPDATE).
  2. Convergent promote-and-rename of the V28 L2 directory: soft-clean its
     role_menu bindings, soft-delete the L2 检查设置 row, insert a fresh L1 「设置」
     directory (up_id = 0, icon `medicine-box` kept, sort 2 — between 系统 (1) and
     the workstation W nodes (10/20/30)), re-point every old binding to the new row
     (grant carry-over so non-admin roles keep their chain), then re-parent the
     four C pages BY PERMS (V28 discipline; the twelve F nodes hang off the pages
     and follow untouched). Admin auto-grant + setval tails.
  3. The soft-delete-plus-fresh-insert shape (NOT an in-place rename) is what keeps
     the applied V28's re-run convergent: V28's guard matches live
     `up_id = 1 AND title = '检查设置'`, so an in-place rename would let a V28
     re-insert duplicate the directory; with the soft-delete shape a V28 re-run's
     dup is cleaned by the V30 re-run (live-state no-op both ways).
- Tests re-pointed to the new IA: `AuthExamSettingSeedTests` (directory assertions +
  the re-execution proof becomes the V28→V30 tail, live-row counting),
  `AuthLoginTests` / `AuthDataLayerTests` (root title 系统).
- **lumen-web: zero code change** — routes resolve via the `component` column map
  (`src/routes/menu-paths.ts`), independent of tree position; the rail highlights
  设置 through subtree membership; fixtures in web tests are arbitrary data.

## Affected Stacks

- single-stack: lumen-sev

## Depends-On

- none - standalone (post-dates merge-setting-into-system, whose V29 is applied)

## Scope

- In scope: menu seed rows for the two rulings (rename + promote/rename), affected
  backend tests, system-data spec delta.
- Out of scope: any API contract, the exam-setting pages/perms (untouched),
  web-app-shell code, workstation W nodes.

## Success Criteria

- After migration: one live L1 `设置` directory carrying the four setting pages
  (their perms/paths/components unchanged); no live L2 检查设置; menu 1 titled 系统;
  the admin role sees the full chain; non-admin grants on the old directory survive
  on the new row.
- Full `./mvnw test -q` exit 0 (lumen-system suite green on the shared DB).
- V28→V30 tail re-run is a live-row no-op (updated re-execution proof).

## Constraints

- Never rewrite an applied migration (V28 stays byte-identical).
- Idempotence discipline: content-guarded statements, comments without semicolons,
  setval tails (V14/V15/V27/V28 shape).
- No API contract change; no schema change (data migration only).

## Risks

- Non-admin roles granted the setting pages before V30 would orphan under a new
  directory id → mitigated by the grant carry-over statement.
- Fresh DB vs migrated DB divergence → both paths converge (V28 inserts, V30
  moves; serial ids never assumed — perms/titles are the contract).
