---
verify:
  change: number-rule-unification-web
  date: 2026-11-19
  overall: PASS_WITH_ISSUES
  stacks:
    lumen-web:
      L1: PASS
      L2: "6/6"
      L3: PASS
---

# Verification — number-rule-unification-web

Single-stack change (lumen-web). All scenario evidence from the page test suite
(`src/test/number-rule-page.test.tsx`, the api-seam stub — no page data ever flows
through a mock at runtime, ruling 2026-11-05).

## Stacks

| Stack | Layer | Status | Detail |
|:---|:---|:---|:---|
| lumen-web | L1 build | PASS | `pnpm build`; exit=0; logs/build-lumen-web.log |
| lumen-web | L2 spec | 6/6 | all IMPLEMENTED (below) |
| lumen-web | L3 tests | PASS | `pnpm test`; exit=0; 33 files / 412 tests green; logs/test-lumen-web.log |

## Scenario alignment (delta specs/web-system-console)

| Scenario | Status | Evidence |
|:---|:---|:---|
| Page loads against the real backend | IMPLEMENTED | page test `pages the real list endpoint on mount` (pageNumberRules called with paging; the client path is `/system/number-rules` — api/number-rules.ts:44-54); the route is `/system/number-rules` (router.tsx system block) and the component lives at `pages/system/number-rule` |
| Toolbar follows button perms | IMPLEMENTED | page tests: 新建 hidden without `system:number:create` (read-only ops column), 删除 hidden without `system:number:delete` |
| Create a stamped rule | IMPLEMENTED | page test `creates a stamped rule through the drawer with the default 影像号 family` (POST payload ruleType pat_id + prefix + serialLength 4 + isStamp 1; drawer closes; table re-pages) |
| Invalid serial length surfaces on the field | IMPLEMENTED | page test `surfaces the out-of-range serial length on the field` (1–10 message on the field, drawer stays open, no POST) |
| Delete requires confirmation | IMPLEMENTED | page test `delete confirms, calls the API, and surfaces the active-rule refusal verbatim` (Popconfirm → DELETE → A0400 refusal surfaces) |
| Rule type selects the platform family | IMPLEMENTED | page test `create drawer selects the platform family - 检查号 rides the payload` (影像号/检查号/预约号 options; selecting 检查号 → payload ruleType accession); the edit echo stays a Tag (family immutable) |

## Issues

- **PASS_WITH_ISSUES reason**: task 2.1's USER VISUAL CONFIRM is pending — the page
  under 系统管理 with the family select and drawer flows must be eyeballed on the
  real backend (requires the :8081 restart that applies V40). All 6 delta scenarios
  are test-verified; the visual gate is the only open item before archive.
- One unrelated flake re-confirmed: `exam-method-page.test.tsx` "multiple tooltip"
  (known, passed on rerun; same flake as the 2026-11-18 menu-root verification).

Overall: **PASS_WITH_ISSUES** — 6/6 scenarios implemented, L1 + L3 green; the archive
waits on the user visual confirm (task 2.1), then proceeds together with the sev
change per the coordination gate.
