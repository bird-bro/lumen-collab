# Design: web-us-station-redesign

> Parent coordination: `openspec/coordination/us-registration-redesign.md` (拍板 01-07).
> UI authority: `docs/prototypes/us-registration-redesign.html` — the visual contract; this
> design records the IMPLEMENTATION decisions the prototype does not pin. Consumes ①+②
> verified contracts; zero backend changes.

## Context

`pages/us/registration/index.tsx` today: a 543-line single-state page (the RCP list + the
560px registration drawer + a detached lookup bar) against `api/us-orders.ts` (resolve/
create/page/register/cancel). The backend ② shipped the wide query, the status meter, the
saved searches, and the slot-config CRUD. The console pages (exam-item/body/method) are the
house CRUD-page precedents.

## Goals / Non-Goals

**Goals**
- The two-state workbench (compact ↔ wide) per the prototype, on the real backend.
- Pre-registration through the picker + verbatim refusals; 登记 as the arrival confirmation.
- The chips meter, the wide query row, 我的查询, column settings.
- The 预约设置 console page (lights the V51 leaf).

**Non-Goals**
- Any backend change (①② are frozen and verified).
- Column settings backend persistence (localStorage per 拍板 07-A).
- New routes/menu code (the leaf ② seeded; the component key maps through the existing
  dynamic-routing convention).

## Decisions

- **D1 — One route, one page component, an edge toggle.** `us/registration/index.tsx` holds
  BOTH states (compact/wide) in one component tree; the edge toggle swaps the layout shell
  (rail+form ↔ full-width table) with the shared state (filters, list data, autorefresh)
  lifted to the page root. The compact state carries NO query row (拍板: the vertical space
  belongs to the list) — querying lives only in the wide state, and the date navigation
  (datenav) shows in the compact head and hides in the wide head (time owned by the
  apply-time range; the shared date state stays).

- **D2 — The rail is a rendered list, not a mini table.** 今日登记单 rows are hand-laid
  compact rows (the prototype's 密度: patient composite two-line + state tag + row actions
  登记/取消), NOT ConsoleTable — the console table's chrome invariants apply to the WIDE
  table only. The rail rows ride the same page data (one fetch, two renderings) with the
  status chips' multi-select feeding the same IN-filter. The autorefresh mini switch lives
  in the rail head (compact) / the wide head (wide), one shared interval.

- **D3 — The form follows the prototype's sections literally.** 1 患者信息 (3×3 grid,
  value-type merges per the 拍板 rule: 证件/身高体重/住院床号/妊娠 merge, 性别/年龄 do not;
  the pregnancy cell hides unless gender=female and 未孕/未知 disables the weeks), 2 请检
  信息 (the 急诊 Switch in the section TITLE row — the drawer-switch-inline ruling; 预登记
  时间 DatePicker.ShowTime; 临床诊断 → ext.note), 3 检查信息 (the common-item chips loaded
  from the setting catalog's isCommon US-modality items + the picked list + the 添加项目
  dialog table). Form density: label 72px right-aligned, three columns, 9px rhythm — plain
  antd layout props, no new design tokens.

- **D4 — Pre-registration posts the raw picker value.** The picker emits
  `yyyy-MM-dd HH:mm:ss`; the payload sends it verbatim (the backend floors onto the slot
  grid). The four A0400 refusal messages render verbatim (messageApi.error) anchored near
  the picker — the ② integration tests pin the tokens. A successful pre-registration
  refreshes the list (the row lands 预登记) and clears the form; a successful walk-in submit
  does the same (提交即登记 — the ② transitional break ends here).

- **D5 — The chips meter + the wide row.** The chips fetch `/orders/status-counts` with the
  CURRENT filter set minus status (the endpoint ignores status anyway) on load and after
  every mutation; multi-select toggles build the comma-separated `status` param; the default
  selection is BOOKED+REGISTERED. The wide query row: uniform 150px controls (selects too),
  atomic label+control pairs, one flex-wrap row with the 更多条件 collapse (the RCP
  PlusSearch flow) — patientId/patientSource/cardNo/examItemName/applyTime range ride the
  collapse, the primary five stay visible. The apply-time range uses the same
  `yyyy-MM-dd HH:mm:ss` format the backend binds.

- **D6 — 我的查询 + column settings.** The saved-search bar (wide state): save (named,
  current conditions minus the saved search itself), apply (the query jsonb feeds the form
  controls — the shape contract is this page's), set-default (the star), delete. The
  default auto-applies on mount AFTER the first load. Column settings stay localStorage
  keyed `us-registration.columns` (the EIS useHeaderColumn pattern — no backend table).

- **D7 — The 预约设置 page is a house console CRUD page.** `pages/setting/exam-slot/index.tsx`
  mirrors the exam-item page skeleton: ConsoleTable (the ops column via actions/onRefresh —
  NO hand-written key:'ops'; ConsoleAction edit glyph default, delete with an explicit
  remove icon), the drawer (平铺单列, closable end, maskClosable false; the modality token a
  dict-1024 SELECT — dictItemsById id-anchored load — disabled on edit with the stored
  token; granularity/capacity InputNumber with the defaults 30/4; the windows editor = a
  start/end TimePicker pair list with add/remove), ConsoleFormHint (info blue: the fail-closed
  semantics — an empty window set means the modality refuses pre-registration). Perms gate
  the affordances via the house button-gating pattern.

- **D8 — API layer.** `us-orders.ts`: the create body gains bookingTime, the page query gains
  the wide params + the status array param, the view gains bookingTime, new
  `statusCounts(params)`. New `us-slot-configs.ts` (list/create/update/delete) +
  `search-filters.ts` (list/create/update/setDefault/delete). All through the existing
  client/envelope conventions — no new fetch machinery.

- **D9 — Tests.** Vitest per the house harness: the workbench renders both states, the
  chips meter renders the counts, the pre-registration picker posts bookingTime and surfaces
  a verbatim refusal, the wide conditions compose the query params, 我的查询 save/apply/
  default; the 预约设置 page lists/creates/edits/deletes with the perm gates. The antd
  Select matching discipline (textContent substrings) and the Popconfirm full-run flake
  protocol apply.

## Risks / Trade-offs

- [The rewrite touches a live page] → the prototype is the user-confirmed contract (拍板
  settled); the old drawer code is removed wholesale (no dual-form maintenance).
- [The rail + wide table share one fetch] → v1 volume makes one paged fetch (pageSize
  sized for the rail) adequate; the wide table paginates normally.
- [Saved-search shape drift] → the query jsonb is this page's own contract (② stores it
  verbatim); a shape change is a page-local migration, the backend never breaks.

## Migration Plan

Pure frontend delivery — no data migration. Deploy order: after ② (already verified); the
transitional break (②'s dead 登记 button, the inert 预约设置 leaf) resolves on landing.

## Open Questions

- None — the 拍板 01-07 + the prototype settle the surface; D1-D9 pin the implementation.
