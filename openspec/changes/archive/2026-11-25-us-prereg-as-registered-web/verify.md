<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-prereg-as-registered-web
  date: 2026-11-25
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# Verification: us-prereg-as-registered-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS | 10/10 | PASS | logs/build-lumen-web.log · logs/test-lumen-web.log |

- L1: `pnpm build` — exit 0.
- L3: `pnpm test` (full Vitest suite) — exit 0, **36 files / 504 tests / 0 failures**.

## Scenario Alignment

### specs/web-us-station/spec.md — Order Form and Actions (6 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Create posts identity, items, and extension | IMPLEMENTED | save() posts the create body + resets + refreshes (index.tsx:733-766); submit tests green |
| Pre-registration time posts the slot anchor (superseded → row lands 已登记 + 预约 tag) | IMPLEMENTED | bookingTime posts; the refreshed row renders the category tag (index.tsx:894/915/1173) + tag test (test:375-399); the four A0400 refusals surface verbatim near the picker (picker tests green) |
| Item modality rides the create payload | IMPLEMENTED | index.tsx:748 (`modality: item.modality ?? null`); modality test green |
| 登记 transitions the order (superseded → the action is GONE) | IMPLEMENTED | no 登记 affordance on any row — registerOrder import/handler/action removed; pinned by the NO-登记 test (test:365-374) |
| Cancel requires a reason and confirmation | IMPLEMENTED | cancel confirm modal + reason gate (index.tsx:1753+); cancelOrder test (test:342) |
| Illegal transition surfaces verbatim | IMPLEMENTED | the cancel/transition error path renders the backend msg verbatim (messageApi.error(error.msg)) |

### specs/web-us-station/spec.md — Order List (4 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| List renders with state and identity (superseded → + the 现场/预约 category tag) | IMPLEMENTED | compact rail tag (index.tsx:915), wide-table tag beside the status chip (index.tsx:1173); tag test covers both states |
| Status chips carry the meter and multi-select (superseded → default 已登记) | IMPLEMENTED | DEFAULT_STATUS_FILTER ['REGISTERED'] (index.tsx:89, the BOOKED chip still renders zero); chips test pins the default + toggle join |
| Wide conditions compose through the query row (superseded → the time filter LABELED 创建时间) | IMPLEMENTED | 创建时间起/止 aria-labels bind applyTimeStart/End unchanged (index.tsx:1015-1017); relabel test (test:817-818) |
| 我的查询 persists and auto-applies the default | IMPLEMENTED | saved-search suite unchanged and green in L3 |

## Issues

- Task 4.2 (manual visual pass against the real backend) was waved through by the user
  (继续, 2026-11-25) rather than performed in a browser; the DOM behaviors it would have
  checked are pinned by the page tests against the real backend's contract shapes
  (proven by the sev sibling's integration tests).

## Next

- PASS → archive us-prereg-as-registered-web (batch with us-prereg-as-registered-sev per
  the coordination doc's archive gate).
