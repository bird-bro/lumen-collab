# Proposal — exam-setting-web

## Why

The backend change `exam-setting-api` delivers the 检查设置 service
(`/api/v1/setting/**`); the console needs pages to maintain the exam catalog and the
report-template knowledge base. RCP's `/setting/exam/*` + template pages prove the
interaction shapes; lumen re-hosts them under the existing console patterns with the
re-modeled domain (no org filter, group management in-page, one template page with
公/私 tabs). Per the frontend-backend-first ruling, this change starts only after the
backend API is user-verified.

## What Changes

- Console menu group 「检查设置」 gains four pages (menu rows come from the backend
  change's seeds; the web change consumes the IA):
  - 检查项目 — modality + 部位分组 filters, keyword search, table (名称/编码/部位/
    方法/计费/得分 columns), Drawer form (分组→部位 cascade, 检查方法 multi-select,
    price/rate/tag/score_rep/score_aud, 常用项目 switch).
  - 检查部位 — left column manages 部位分组 (CRUD), right table manages 部位
    (group filter, tree-shaped rows, Drawer form with group + optional parent).
  - 检查方法 — modality filter, keyword search (名称/编码), table, Drawer form.
  - 报告模板 — 公共/私有 tab toggle, left modality select + folder tree (add/edit/
    delete), right template table (名称/影像学表现/影像学意见) + Drawer form
    (目录/模板 by flag).
- API client modules under `src/api/setting/` typed against the real endpoints; no
  mock layer (2026-11-05 ruling) — pages render real `/api/v1/setting/**` data.
- Button affordances gated by the `setting:*` F-node perms (Button-Level Affordance
  Gating requirement).

Out of scope: 项目对照 page (deferred with the API), print-template page, booking-side
item pickers (workstation scope), UI library or design-system changes.

## Capabilities

### New Capabilities

<!-- none — the console capability already owns console page behavior -->

### Modified Capabilities

- `web-system-console`: new requirement blocks for the four 检查设置 pages (IA
  placement, filters, table columns, drawer forms, tabs, perms gating) following the
  device/room page delta precedent.

## Impact

- `lumen-web/src/pages/setting/**` — four new page modules (`exam-item/`,
  `exam-body/`, `exam-method/`, `rep-template/`).
- `lumen-web/src/api/setting/**` — typed API client modules.
- `lumen-web/src/pages/system/**` — untouched (menu-driven routing picks up the new
  component paths seeded by the backend change).
- Route/component registry follows the existing console registration pattern.
