<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: us-station-registration-web
  date: 2026-11-20
  overall: PASS
  stacks:
    lumen-web:
      L1: PASS
      L2: "10/10"
      L3: PASS
---

# Verification: us-station-registration-web

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| lumen-web | PASS | 10/10 | PASS | logs/build.log · logs/test.log |

- L1 `pnpm build` exit=0 (tsc + vite, tests type-checked in the pass).
- L3 full `pnpm test` exit=0: **456 tests / 35 files, all passed** (the page's own 9 scenarios among them; api seam mocked per the no-mock-at-runtime ruling — page data flows through the real `/api/v1/us/**` at runtime).

## Scenario Alignment

All evidence in `src/pages/us/registration/index.tsx` unless noted.

| Scenario | Status | Evidence |
|:--|:--|:--|
| Page renders under the activated US workstation | IMPLEMENTED | Component key `us/registration/index` → `/us/registration` in the static route map (`src/routes/menu-paths.ts:44-46`) + route registered (`src/router.tsx`); the V3 seed's W-child click now navigates (the user's click-dead-end was the pre-web-apply boundary); perm gate `us:order:list` renders the page |
| Actions hide without perms | IMPLEMENTED | 新建登记/解析查询/输入框 all ride `hasPerm('us:order:create')`; row actions ride `us:order:update/cancel`; test asserts absence with a list-only perm set |
| Exact id_no match fills demographics | IMPLEMENTED | resolve passthrough → MATCHED → `identityToForm` fills the drawer; test asserts displayValues + the resolve call carried the idNo |
| Soft-match candidates offer selection | IMPLEMENTED | CANDIDATES → `Modal` lists name+phone/gender/birth rows; pick fills the form + echoes the matched patId; test drives pick 李四B |
| No match opens a new archive with placeholder path | IMPLEMENTED | NONE → empty drawer + 急诊占位 toggle; toggle disables 姓名 (unnamed PROVISIONAL); `stripPlaceholder` posts `name: null` for the placeholder |
| Create posts identity, items, and extension | IMPLEMENTED | Body = demographics (whitelist) + `examItems` refs (`{itemId, itemName: item.exam}` — the setting catalog's name snapshot) + `emgFlag` + `ext {pregnancyStatus, gestationalWeeks, note}`; success toast carries the returned accession; payload asserted in test |
| 登记 transitions the order | IMPLEMENTED | `registerOrder(id)` + reload; test asserts the call and the second page load |
| Cancel requires a reason and confirmation | IMPLEMENTED | Cancel modal with 必填 reason TextArea → `cancelOrder(id, reason)`; test asserts the posted reason |
| Illegal transition surfaces verbatim | IMPLEMENTED | `ApiError.msg` rendered verbatim, row unchanged; test pins the backend message |
| List renders with state and identity | IMPLEMENTED | Paged list: accession (mono), patient name+patId from the snapshot, item summary, STATUS_LABELS tags, orange 急诊 marker; test asserts against the seeded row |

## Notes

- Layout ruling landed with the page (user, 2026-11-20): a single-page workstation renders WITHOUT the secondary sider (`BasicLayout.tsx` `singlePageStation` — no `ant-layout-sider-children` wrapper; multi-page stations keep it).
- Batch Inline run in-context (this session): TDD per task; the option-picker test went through three matcher generations (title-attr → whole-word content selector → node-textContent substring) — rc-select internals pollute both the title attribute and the option root text; the substring matcher is the durable shape.
- The parallel session's `lumen-api → lumen-feign` rename swept this change's sibling artifacts mid-flight; the web change has zero coupling to that module.

## Next

- PASS → coordination gate satisfied (sev PASS + web PASS): batch-archive both changes.
