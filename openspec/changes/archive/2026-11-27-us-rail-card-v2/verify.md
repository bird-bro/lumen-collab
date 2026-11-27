<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-rail-card-v2
  date: 2026-11-27
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
    backend:
      L1: SKIP
      L2: "n/a"
      L3: SKIP
---

# Verification: us-rail-card-v2

Overall: **PASS** — spec-only reconciliation change (no code ships; the card landed
through commit `2295574` + the `us-order-exam-room-web` archive sweep).

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Evidence |
|:--|:--|:--|:--|:--|
| lumen-web | PASS (`pnpm build` exit=0) | 10/10 | PASS | page suite 54/54 (2026-11-26, commit `2cfd582`, code unchanged since) + `tsc --noEmit` exit=0; full suite today 536/539 with 3 full-run flake failures (menus-page / room-page / registration backfill) that pass isolated 88/88 exit=0 — the recorded `vitest-popconfirm-fullrun-flake` family, isolated-green protocol |
| backend | SKIP | n/a | SKIP | change touches no backend file (proposal Impact) |

## Scenario Alignment

### web-us-station — Order List (MODIFIED)

| Scenario | Status | Evidence |
|:--|:--|:--|
| List renders with state and identity | IMPLEMENTED | index.tsx:1415/1587 (compact/wide modes), :1456-1472 (head lead + cancel-only tail), :1499 (room segment only when set), :1647 (wide room '—'), :1648 (wide tag beside chip); tests us-registration-page.test.tsx:569-585 (stripe/ids/急/source placement), :625-650 (card chip dropped, wide keeps it) |
| The card anatomy carries the v2 layout | IMPLEMENTED | index.tsx:1425-1453 (dict-hue stripe, hatch, transparent fallback, 0.55 dim), :1476 (hairline), :1515-1542 (one-line clamp + chevron, aria-expanded), :1544-1574 (icon-prefixed ids, cardNo optional, 急 right edge), :1447 (red border live-only); tests :569, :585, :613-621; hatch/dim/border/icon facets via the 2026-11-26 真机 walkthrough (user visual confirmation) |
| The card keeps one icon and one bold discipline | IMPLEMENTED | tertiary glyphs index.tsx:1486/1502/1536/1551/1558; the 20px name the only bold :1456; time value + room regular weight normal ink :1491/:1503; facet recorded via the 真机 walkthrough |
| Status chips carry the meter and multi-select | IMPLEMENTED | carried-verbatim surface, unchanged: index.tsx:1139 chipsRow (dict-1031 order/colors), :334 statusCounts zero-fill; suite chip tests green |
| The rail quick-search narrows the list and the meter | IMPLEMENTED | carried-verbatim, unchanged: index.tsx:280-281 state, :317/:336 keyword rides page + status-count queries, :420 debounce |
| Wide conditions compose through the query row | IMPLEMENTED | carried-verbatim, unchanged: appendWideConditions + the wide query row |
| 我的查询 persists and auto-applies the default | IMPLEMENTED | carried-verbatim, unchanged: index.tsx:2390-2423 saved-search modal + search-filters REST default auto-apply |
| The anchor never shows the audit clock | IMPLEMENTED | index.tsx:1434 (`timeIsBooking`), :1491-1492 (slot ?: registration moment, full YYYY-MM-DD HH:mm), wide 创建时间 :1599; test :526-585 (booked renders slot, walk-in renders registration moment, audit value stays wide-only) |

### web-system-console — Cancel Verb Glyph (ADDED)

| Scenario | Status | Evidence |
|:--|:--|:--|
| 单据取消 actions ride the cancel glyph | IMPLEMENTED | console-shell.tsx:218 (`cancel: CloseCircleOutlined`), index.tsx:1102 (取消登记 `icon="cancel"` danger + verb Tooltip) |
| 删除 verbs keep the trash glyph | IMPLEMENTED | console-shell.tsx:193 (`remove: DeleteOutlined`); repo-wide sweep: 16 删除 call sites all `icon="remove"`, the single `cancel` call site is the 取消登记 verb — zero overlap |

## Reconciliation Note (task 1.1)

Four delta wording fixes landed before this verify (spec text, never code): the room
segment/'—' clause split per surface, the cancelled head-tail em-dash placeholder, the
cancelled-card 急-glyph/dim wording (the border is the alarm channel), and the
walk-in anchor = `create_time` instant clarification in the Card Rail Anchor Semantics
requirement + scenario.

## Issues

- None blocking. The 3 full-suite flake failures are out-of-change noise (files
  unrelated to the card surface), characterized by isolated re-run 88/88 green on
  identical code; see logs/test-lumen-web.log and the flake-family memory.
