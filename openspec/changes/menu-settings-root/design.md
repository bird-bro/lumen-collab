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

up_id = 0, type M, title 设置, icon `tune` (tasks 1.3/1.6, user rulings 2026-11-13:
the V28-inherited `medicine-box` was mapped by no console glyph - the rail fell back
to the department radar glyph; V31 shipped the AntD placeholder `sliders`, which task
1.6 swapped to the RCP reference glyph `tune` (RCP `icon-setting`, rcp-test dict_menu
mid 1018) ported in ops-icons.tsx - V34 forward fix, V31 frozen), sort 2
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
  the re-execution proof runs the V28→V32 TAIL (the realistic convergence unit once
  V30 mutates V28's rows, V31 re-points the icon, and V32 re-groups the pages) and
  counts LIVE rows only (soft-deleted dups must not count as state).
- `AuthLoginTests` / `AuthDataLayerTests`: root-title assertions 系统管理 → 系统;
  IA comments refreshed.

## D8 — setval tails

menu_info + role_menu setval tails close the migration (V2/V28 discipline — serial
inserts in the migration never collide with explicit-id history).

## D9 — The L2 group directories (task 1.4 + task 1.5)

User ruling during 2.1 verification (2026-11-13): the flat four-page L1 gains two
icon-bearing L2 directories — 检查配置 (sort 1: the catalog trio
检查项目/检查部位/检查方法) and 报告配置 (sort 2: 报告模板). Both are type M with
NULL perms (the V28 group shape), inserted content-guarded under the L1 设置 row
(V32). Grant chain: each role holding a re-grouped page gets its new L2 parent
(the D3 carry-over shape, per group, DISTINCT-collapsed since a role may hold
several catalog pages); pages re-parent BY PERMS (the D4 discipline); admin
auto-grant + D8 setval tails. Convergence per D2: a V30 re-run flattens the pages
back under the L1 row and the V32 re-run re-groups them — a live-row no-op both
ways.

Group ICONS (task 1.5): the AntD placeholders (medicine-box / file-text) read as
generic, so the icons swap to the RCP reference glyphs (V33) — RCP styles the
same console domain with its iconfont `exam` (检查 group, rcp-test dict_menu mid
1029) and `temp` (模板 group, mid 1025). Both symbols are ported verbatim into
`src/components/ops-icons.tsx` as `ExamCatalogIcon` / `TemplateBookIcon`
(monochrome currentColor, the V11 porting route), wired as
`exam → ExamCatalogIcon` / `temp → TemplateBookIcon` in MENU_ICON_BY_NAME.
