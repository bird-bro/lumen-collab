# Coordination: workstation-context

> Cross-stack parent. Shared design/decisions + per-stack change registry.
> Per-stack changes are soft-tagged via `openspec new change <name> --goal "workstation-context"`.

## Why

The three-layer orthogonal assembly (patient-domain decision #8, 2026-11-04) is two
thirds done: role_menu landed with the auth foundation, deployment exists as a runtime
fact - but the WORKSTATION layer (科室工作站的会话级激活台) is missing. Department pages
(radiology 登记/检查/报告) cannot open without it: a user authorized for multiple
departments would see every department's pages at once. This change lands the
workstation increment end to end (user ruling 2026-11-06: full-stack closed loop,
assignment UI included).

## Shared Design

> **REVISED 2026-11-06 (user ruling: "你完全彻底的做错了")** — the `user_workstation`
> direct user-grant design is WRONG and is torn down by `workstation-w-nodes-api`:
> **the workstation IS a menu node** (`menu_info.type = 'W'`, L1 level), permissions
> flow ONLY through roles (`role_menu` grants the W node to roles; users bind roles) —
> never directly to users. The chip/append UI survives; its data source becomes the
> granted tree's W nodes. The primary-workstation concept is dropped (session-only
> selection; a single granted station auto-activates).

Single source of truth for both stacks.

### Workstation model

- **Typed enum** (DB `varchar` + frontend string union): `admin` (console/系统管理),
  `radiology`, `us`, `endo` — v2 adds `remote` (RCP 优化重构). No free-text stations.
- **Table `system.user_workstation`**: `id` PK auto, `user_id` (FK user_info),
  `workstation` varchar(16), `is_default` 1/0 (the 主台), + the `EntityBase` audit block.
  Partial unique: one live `is_default=1` row per user (like role_menu's grant
  discipline). A user may hold several stations; exactly one is the default.
- **Grant semantics**: a user with zero workstation rows is treated as holding `admin`
  only (bootstrap/console is not department work) — existing accounts keep working.
- **/me + login payloads** gain `workstations: string[]` (live rows for the user) and
  `profile.primaryWorkstation: string` (the `is_default=1` row, else `admin`).

### Switching semantics (frontend) — REVISED 2026-11-06: workstation = menu-tree root grouping

- **Workstation sits ONE LEVEL ABOVE L1 menus** (user rulings 2026-11-06, two-step
  refinement): NOT as fake tree nodes — as an OWNERSHIP FIELD on L1 menus
  (`menu_info.workstation`, L1-only) rendered as a GROUP HEADER row in the 菜单管理
  page (工作分组标题行 above its L1 menus — visually one level above, but not a
  menu_info node: no fake hierarchy, no tree re-parenting migration, the role_menu
  ancestor-chain discipline is untouched). The station set is a product enum (fixed
  4-item dropdown in the L1 form; admins cannot create stations). A child menu's
  station = its ancestor L1's value. 放射科/超声科/内镜科 exist as placeholder L1
  menus (type M, no children yet).
- **Header chip** in zone 2, LEFT of the product title (the 2026-11-04 confirmed
  定式), lists the granted stations (icon + name + description, check on the active).
- **Switch = APPEND, not replace** (user description 2026-11-06): the admin-domain L1
  menus (系统管理…) stay in the sidebar for anyone holding them — switching to a
  department station APPENDS that station's L1 menus beside 系统管理; switching back to
  控制台 removes them. The sidebar shows both groups flat (`系统管理` + the active
  department's L1s). Zero extra requests (the /me tree already carries every L1 with
  its marker).
- **Menu management shows the grouped tree** — 分组标题行 per station above its L1
  menus; the administrator never switches stations to manage menus (the pain point
  that triggered the revision).
- **Session persistence**: the active station lives in the auth store (memory) —
  re-login lands on the primary workstation. Unsaved drafts ride the existing
  route-leave guard.
- **Where the root↔station binding lives**: `menu_info.workstation` (nullable varchar
  enum) — set ONLY on the four L1 roots by V19; a child's station = its ancestor
  root's value (the tree structure itself is the mapping). The earlier "no
  workstation column" ruling targeted per-menu configuration; a root-only marker is
  the tree structure made explicit, and the EIS 404 lesson stays covered (roots are
  seeded enum rows, not operator-typed strings).

### Assignment UI (users management)

- 用户管理 page: per-user workstation assignment affordance (grant/check stations +
  set primary), backed by `PUT /api/v1/system/users/{id}/workstations` (full-replace,
  mirroring the role-menu assignment pattern).
- **Button perm**: `system:user:assign-ws` — a NEW F node (V19 seed, V15 discipline:
  content-guarded insert + admin auto-grant) pinned server-side, gated client-side.
  The assignment endpoint is additionally pinned by it.

## Decisions

- Full-stack closed loop incl. assignment UI — user ruling 2026-11-06.
- Zero workstation rows ⇒ `admin` only (no migration backfill needed; existing users
  unaffected) — 2026-11-06.
- Static router-domain mapping, client-side refilter, zero-request switching — per
  patient-domain decision #8 (EIS lesson) — 2026-11-04.
- v1 ships mechanism + chip + assignment with console-only menus; the filter becomes
  observable when department domains land — 2026-11-06.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | workstation-context-api | **archived 2026-11-06 — DESIGN SUPERSEDED** (user_workstation direct-grant ruled wrong) | superseded by workstation-w-nodes-api |
| backend | workstation-w-nodes-api | **archived 2026-11-06** | PASS (3/3, L1/L3 PASS, 100/100) |
| frontend | workstation-context-web | **archived 2026-11-06** | PASS (3/3, L1/L3 PASS, 234/234) |

## Gate

Feature done = ALL registered changes verify PASS -> archive each.

**Feature closed 2026-11-06** — workstation-w-nodes-api + workstation-context-web both
archived; the W-node model is live (`menu_info.type='W'` L1 nodes, V22; role_menu
grants; chip + promotion in the shell; 工作站 type in menu management). The per-user
direct-grant machinery from the first draft is fully torn down.
