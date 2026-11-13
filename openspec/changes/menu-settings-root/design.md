# Design — menu-settings-root

## D1 — Forward-fix migration, V28 frozen

V28 is applied (shared test DB + any deployed DB): it stays byte-identical. The IA
ruling lands as `V30__menu_settings_root.sql` (V29 = exam_setting_tables, applied).

## D2 — Convergent promote shape: soft-delete + fresh insert

The trap: V28's re-run guards key on live `up_id = 1 AND type = 'M' AND
title = '检查设置'`. Two candidate V30 shapes:

- **In-place rename** (`UPDATE ... SET up_id = 0, title = '设置'`): V30 re-runs fine,
  but a V28 re-run on the post-V30 state finds NO live L2 检查设置 → re-inserts one;
  a following V30 re-run promotes THAT → two live L1 设置 rows. Not convergent.
- **Soft-delete + fresh insert (chosen)**: the old row goes deleted = 1 (bindings
  cleaned first, grants carried over); a V28 re-run re-inserts a fresh L2 dup and a
  following V30 re-run soft-deletes it again — live state converges under BOTH
  re-run orders (V30 alone, and V28→V30 tail).

## D3 — Grant carry-over

role_menu bindings on the old L2 row re-point to the new L1 id before the cleanup
(NOT-EXISTS-guarded per role). Without it, any non-admin role that was granted the
setting pages loses the ancestor link (TreeUtils drops orphans — the whole subtree
disappears for that role). Admin also gets the explicit auto-grant (V27/V28 shape),
redundant after a carry-over but the guard makes it free.

## D4 — Pages re-parent BY PERMS

The four C pages move via `perms IN (setting:item:list, ...)` — the contract string,
never a serial id (V28 discipline). The twelve F nodes hang off the page ids and
follow untouched. Page path/component/perms/sort columns are untouched (the web
route map keys on `component` — tree position plays no role).

## D5 — The new L1 row

up_id = 0, type M, title 设置, icon `medicine-box` (kept from the V28 group), sort 2
— beside 系统 (sort 1), before the workstation W nodes (10/20/30). No path column:
L1 directories navigate to their first leaf (`firstLeafRouteOf`), and the rail's
active-node lookup matches by subtree membership, so a pathless L1 M directory is a
shape the shell already handles (V28's group had no path either).

## D6 — Rename of the system root

Pinned id 1 (the V2 seed), content-guarded (`id = 1 AND type = 'M' AND
title = '系统管理' AND deleted = 0`) — re-run no-op once renamed. Nothing keys on
the title server-side (menus are data; tests assert it, and they get updated).

## D7 — Test surface

- `AuthExamSettingSeedTests`: directory assertions move to the new IA (L1 设置);
  the re-execution proof runs the V28→V30 TAIL (the realistic convergence unit once
  V30 mutates V28's rows) and counts LIVE rows only (soft-deleted dups must not
  count as state).
- `AuthLoginTests` / `AuthDataLayerTests`: root-title assertions 系统管理 → 系统;
  IA comments refreshed.

## D8 — setval tails

menu_info + role_menu setval tails close the migration (V2/V28 discipline — serial
inserts in the migration never collide with explicit-id history).
