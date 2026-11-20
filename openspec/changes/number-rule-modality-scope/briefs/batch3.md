# Batch 3 Brief — lumen-web: console page + registration form (tasks 3.1–3.2)

Change: number-rule-modality-scope. Batches 1–2 (sev, committed) delivered the backend:
`number_rule.modality` (dict-1024 value token, NULL=global), `/issue` optional
`modality`, US create passthrough preserving `ExamItemRef.modality`. You touch ONLY
`lumen-web/`. Dispatch only after the user has verified sev on the real machine
(the workflow gate). Do not touch sev files.

## Read first

1. Delta specs (acceptance): `openspec/changes/number-rule-modality-scope/specs/web-system-console/spec.md`
   (Number Rule Page Shell + Number Rule Form and Delete — the three scope scenarios)
   and `specs/web-us-station/spec.md` ("Item modality rides the create payload").
2. `src/api/number-rules.ts` (client + DTO shapes), `src/pages/system/number-rule/index.tsx`
   (page + drawer), `src/test/number-rule-page.test.tsx` (test conventions, option-matching
   rules below).
3. `src/api/us-orders.ts` (`UsExamItemRef` line ~26), `src/pages/us/registration/index.tsx`
   (catalog fetch line ~125 `pageExamItems({ modality: 'US' })`, payload map line ~184),
   `src/test/us-registration-page.test.tsx`.
4. `src/api/dicts.ts` line ~36 — `itemsById(catalId)` GET `/system/dicts/detail/items-by-id/{catalId}`
   (the ID-ANCHORED dict lookup — the scope select uses `itemsById(1024)`).

## Task 3.1 — 号码规则 scope UI

TDD RED first in `src/test/number-rule-page.test.tsx` (follow its existing fetch-mock
conventions):

- Client: `NumberRule` interface + `modality: string | null`;
  `CreateNumberRuleBody`/`UpdateNumberRuleBody` + `modality?: string | null`.
- Table: new 适用范围 column right after 规则类型 — renders `全局` when NULL, the raw
  token otherwise (token display only; no dict-name lookup needed in the column).
- Drawer form: new 适用范围 SELECT defaulting to 全局 (value undefined/null);
  modality options loaded through `itemsById(1024)` — option label = item name,
  option value = item `value` (the dict-1024 token submitted in `modality`);
  choosing 全局 submits no modality (NULL). ON EDIT the field is DISABLED and echoes
  the stored scope (scope = pool identity, immutable after create — design D2); if the
  stored token is not among the loaded options, echo the raw token as the label.
- Duplicate-active backend rejection (A0714 naming the scope) surfaces verbatim — the
  page's existing backend-refusal pattern; the drawer stays open.
- Drawer chrome follows the system-console conventions already in this page (no new
  layout inventions — one field added).

## Task 3.2 — US 登记 item modality

TDD RED first in `src/test/us-registration-page.test.tsx`:

- Client: `UsExamItemRef` + `modality?: string | null` (comment: the dict-1024 token
  riding to the issuer for scoped rule resolution).
- Page: the `examItems` payload map (line ~184) resolves each picked id to its CATALOG
  row and adds `modality: <row's modality>` — row-derived, never a hardcoded 'US'
  string (the catalog fetch filters US, but the token must come from the row).
- Existing tests stay green; the new assertion pins the create payload's item refs
  carry the catalog modality.

## House rules (non-negotiable)

- Existing-page edits — NO prototype, NO frontend-design pass.
- Page-test option matching: use node `textContent` substring matching on rendered
  option nodes (rc-select internal markup pollutes `title`/exact-word matches — the
  three-generation precedent in these test files; follow the latest pattern there).
- Drawer: `closable={{ placement: 'end' }}` + `maskClosable={false}` already in the
  page's drawer — preserve; do not restructure sections.
- No mock layer for the DEV app (pnpm dev proxies the real backend) — vitest fetch
  mocks in tests are fine.
- `pnpm build` + `pnpm test` from `lumen-web/`; redirect output to a log; report exit
  code + counts. DO NOT commit — coordinator commits after review.

## Report

Write `openspec/sdd/reports/number-rule-modality-scope-b3.md` (<80 lines): implemented
items, RED/GREEN evidence (command + assertions + counts + exit), files changed,
self-review findings, concerns. Reply ≤15 lines: Status / summary / report path.
