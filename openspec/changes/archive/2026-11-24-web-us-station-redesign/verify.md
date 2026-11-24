<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: web-us-station-redesign
  date: 2026-11-23
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "23/23"
      L3: PASS
---

# Verification: web-us-station-redesign

Overall: **PASS**

Single-stack change (lumen-web: the two-state workbench rewrite + the 预约设置 console
page + the API layer). Zero backend changes (①② frozen and verified).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS | 23/23 | PASS | logs/build-web.log, logs/test-web.log |

- **L1**: `pnpm build` (tsc + vite) — exit 0, built in 2.09s.
- **L3**: the FULL vitest run — **36 files / 476 tests, all green** (the rewrite broke
  nothing outside its own suites; the api-layer changes stayed backward-compatible).

## Scenario Alignment

Deltas: `specs/web-us-station/spec.md` (4 MODIFIED supersets), `specs/web-system-console/
spec.md` (1 ADDED). All page behaviors ride the api-SEAM mocks (the ruling-2026-11-05
contract: mocks never serve page data at runtime).

### web-us-station — Registration Page Shell (3)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Page renders under the activated US workstation | IMPLEMENTED | compact-by-default shell `us-registration-page.test.tsx:107` (rail + the three form sections, NO query row); the route mapping `:98` |
| Actions hide without perms | IMPLEMENTED | `us-registration-page.test.tsx:138` (no 提交登记 / 解析查询 without us:order:create) |
| The edge toggle switches the two states | IMPLEMENTED | `:121` (wide = query row + table + datenav hidden; back-toggle restores; the shared list renders in both) |

### web-us-station — Patient Resolution Flow (3)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Exact id_no match fills demographics | IMPLEMENTED | `us-registration-page.test.tsx:236` (MATCHED fills 姓名) |
| Soft-match candidates offer selection | IMPLEMENTED | `:236` (the 就诊人候选 dialog renders the cards; picking fills) |
| No match opens a new archive with placeholder path | IMPLEMENTED | `:236` (empty form for a new archive; the client 急诊占位 switch retired — `queryByLabelText('急诊占位')` null) |

### web-us-station — Order Form and Actions (6)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Create posts identity, items, and extension | IMPLEMENTED | `:320` (walk-in submit → createOrder without bookingTime; the picked refs + ext block; the list refresh) |
| Pre-registration time posts the slot anchor | IMPLEMENTED | `:333` (picker → body.bookingTime verbatim) + the four A0400 refusals verbatim keeping the form `:347` (该时段已满 pinned) |
| Item modality rides the create payload | IMPLEMENTED | `:302` (the picked list rows carry the catalog row's own token — the row-derived rule, `RegistrationOrderServiceImpl`-mirrored in the page save) |
| 登记 transitions the order | IMPLEMENTED | `:226` (the rail's 登记 on a BOOKED row → registerOrder(11) + refresh) |
| Cancel requires a reason and confirmation | IMPLEMENTED | the carried-over cancel modal (reason required, confirm-first) — pinned by the batch-2 suite's action wiring |
| Illegal transition surfaces verbatim | IMPLEMENTED | `ApiError.msg` renders verbatim through messageApi (the state-machine refusal path — the backend token pinned by ②'s tests; the page surfaces without altering the row) |

### web-us-station — Order List (4)

| Scenario | Status | Evidence |
|:--|:--|:--|
| List renders with state and identity | IMPLEMENTED | `:107` + `:226` (the compact rail dense rows) + the wide table `:121` (accession/identity/items/tags/急诊/预登记时间 columns) |
| Status chips carry the meter and multi-select | IMPLEMENTED | `:207` (the zero-filled counts render; the default BOOKED+REGISTERED rides the initial query; toggling narrows the IN-filter) |
| Wide conditions compose through the query row | IMPLEMENTED | `:407` (primary + the 更多条件 extras compose into one call: accession/patientName/patientId/cardNo/examItemName) + the apply-time range `:426` (the shared date state feeds the default range) |
| 我的查询 persists and auto-applies the default | IMPLEMENTED | `:443` (the default's conditions auto-apply on mount; save→createFilter; the 设默认 full-replace → setDefaultFilter(502)) + column settings localStorage `:492` |

### web-system-console — 预约设置 Page (5)

| Scenario | Status | Evidence |
|:--|:--|:--|
| The page lists the live policies | IMPLEMENTED | `us-slot-config-page.test.tsx:70` (modality/granularity/capacity + the decoded window pairs; the CT empty windows render the fail-closed note) |
| Create saves a trimmed policy | IMPLEMENTED | `:90` (the dict-1024 SELECT via the house combobox pattern; the trimmed token + the windows body asserted) |
| The modality token is immutable on edit | IMPLEMENTED | `:114` (the select renders DISABLED; the stored token echoes; only the policy fields submit) |
| Delete soft-deletes with confirmation | IMPLEMENTED | `:141` (the Popconfirm tooltip confirm → deleteSlotConfig(2)) |
| Affordances hide without perms | IMPLEMENTED | `:87` (no 新建/编辑/删除 without the setting:slot:{create,update,delete} perms) |

(The duplicate-token A0400 verbatim + drawer-kept behavior: `us-slot-config-page.test.tsx:131`.)

## Issues

None material. Two house-pattern learnings surfaced during TDD (recorded, no follow-up):
antd two-character button auto-spacing required the `两字按钮` regex on the drawer/modal
buttons (the known house rule), and the Segmented edge toggle was replaced by the
Radio.Group button-style at IMPLEMENTATION time (the rep-temp ex-Segmented precedent —
the Segmented input's pointer-events:none is test-hostile; the visual output is
equivalent).

## Feature gate

① kernel-pre-registration (PASS) + ② us-registration-redesign-api (PASS) + ③ this change
(PASS) — the batch-archive gate preflight is SATISFIED; all three credentials ready for
`/opsx:archive`.
