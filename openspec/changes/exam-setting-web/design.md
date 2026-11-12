# Design

## Context

Four console pages under the new 「检查设置」 menu group, built against the real
`exam-setting-api` endpoints after user verification (frontend-backend-first, no mock
layer — 2026-11-05 ruling). Page patterns, routing registration, dict lookup, and
perm gating all reuse the existing console implementation (org-device-room-web is the
fresh precedent).

## Goals / Non-Goals

- Goals: the four pages render real backend data, follow the console patterns, and
  gate affordances by the `setting:*` F-node perms.
- Non-Goals: mock layer, new UI components outside the console kit, 项目对照 page,
  print-template page, workstation-side item pickers.

## Decisions

### D1: Page skeletons reuse the console list+drawer pattern

Each page = filter bar + table + Drawer form per the existing console pages
(`system/org/**`, `system/logs`): AntD 5 table with fixed 操作 column, Drawer forms
(540-class width), confirm dialogs for deletes, message feedback on success. No new
layout primitives.

### D2: 检查部位 page hosts group management in-page

Left column (检查类型 select above it) lists the modality's groups with inline
新建/编辑/删除; the right table lists bodies filtered by the selected group. One menu
item instead of two — the coordination doc's menu ruling. A refused group delete
surfaces the backend's blocking message verbatim (backend names the reference).

### D3: Item drawer cascades 分组 → 部位, methods multi-select

Drawer holds 部位分组 select (feeds from `exam-body-groups/list`), 检查部位 tree
select (feeds from `exam-bodies/list?groupId=`), clearing the stale body on group
change; 检查方法 is a multi-select from `exam-methods/list` — the join table replaces
RCP's comma-string editing, so the form binds an id array. 价格/折扣 are numeric
inputs (2 dp), 诊断得分/审核得分 optional numeric (empty = excluded from weighting),
常用项目 a switch bound to `isCommon`.

### D4: 报告模板 page is one page with 公共/私有 tabs

Tab state = scope `type` (1/2) threaded into every tree/page call; switching tabs
reloads both panes. Left pane: modality select + tree with node-level 新建/编辑/
删除; right pane: paged template table under the selected node + name search. Drawer
flag radio 目录/模板 toggles the finding/impression textareas (目录 rows hide them).
No drag-reordering in v1 (RCP parity: parent re-pointing via the edit form).

### D5: API clients + types under src/api/setting/

One module per resource (`exam-item.ts`, `exam-body.ts`, `exam-method.ts`,
`exam-body-group.ts`, `rep-temp.ts`) typed from the `exam-setting` spec deltas
(request/response shapes incl. server-resolved display names); request layer reuses
the existing console client (envelope unwrapping, error toast passthrough so backend
refusals render verbatim).

### D6: Dict labels via the existing lookup; perms via existing gating

检查类型 options come from dict 1021 through the console's existing dict hook
(Dict Detail Management semantics — never hardcoded labels). Buttons hide on missing
`setting:<resource>:<action>` perms per the Button-Level Affordance Gating
requirement; routes/menus arrive from the backend seeds — no hardcoded route table
edits beyond the component registry the router already consumes.

## Risks / Trade-offs

- Backend-dependent verification: pages cannot be fully exercised until
  `exam-setting-api` is user-verified — the sequencing is the workflow gate, not a
  shortcut around it.
- In-page group management is a slightly denser page than two separate menus; chosen
  to keep the menu tree shallow (coordination ruling 9).

## Migration Plan

Component registry entries → API modules → pages (prototype first for user visual
confirmation, then wiring to the real API) → text/DOM checks against the running
backend.

## Open Questions

None — visual specifics are settled at prototype time per the workflow (UI prototype
first; no automatic screenshots).
