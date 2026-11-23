# Batch 3 Report — lumen-web: number-rule scope UI + US registration item modality

Change: number-rule-modality-scope (tasks 3.1–3.2). Scope: lumen-web only; no commit
(coordinator commits after review).

## What was implemented

### Task 3.1 — 号码规则 scope UI
- `lumen-web/src/api/number-rules.ts`: `NumberRule.modality: string | null`;
  `CreateNumberRuleBody.modality?: string | null` (dict-1024 value token, NULL = 全局,
  pool identity echoed unchanged by the full-surface update). `UpdateNumberRuleBody`
  inherits via its existing `extends`.
- `lumen-web/src/pages/system/number-rule/index.tsx`:
  - 适用范围 table column right after 规则类型 — `全局` for NULL, raw token (Tag) otherwise;
    no dict-name lookup in the column (brief: token display only).
  - Drawer 适用范围 SELECT after the 规则类型 block (spec field order): create defaults to
    全局 (`''` sentinel in the form store, mapped to NULL by the save body's
    `values.modality || null`); modality options from `dictItemsById(MODALITY_DICT_ID)`
    (1024, id-anchored) — label = the single-text dict `value`, submitted value = the same
    token (exam-item page precedent; the slim `{code,value}` projection has no separate name).
  - ON EDIT the field renders DISABLED echoing the stored scope (design D2); a stored token
    outside the loaded catalog echoes raw (antd default) — pinned by test.
  - Third catalog (`1024`) rides the existing `Promise.allSettled` load, degrades
    independently; drawer chrome untouched (`closable={{placement:'end'}}`,
    `maskClosable={false}`, footer, no section restructure).
  - Save body carries `modality` on create and echoes the stored scope on edit.

### Task 3.2 — US 登记 item modality
- `lumen-web/src/api/us-orders.ts`: `UsExamItemRef.modality?: string | null` with the
  brief's comment (dict-1024 token riding to the issuer).
- `lumen-web/src/pages/us/registration/index.tsx`: the `examItems` payload map now resolves
  each picked id to its CATALOG row and adds `modality: item?.modality ?? null` —
  row-derived, never a hardcoded 'US'.

## TDD evidence
- RED: `pnpm vitest run src/test/number-rule-page.test.tsx src/test/us-registration-page.test.tsx`
  → exit 1, **7 failed | 20 passed** (`/tmp/b3-red.log`). Concrete failures: no
  适用范围 label (`Unable to find a label with the text of: 适用范围`), header order
  `expected [ '类型', '状态', '前缀' ] to deeply equal [ '类型', '适用范围', '状态' ]`,
  and the two US payload mismatches (refs lacked `modality`).
- GREEN: same command → exit 0, **27/27** (`/tmp/b3-green2.log`). One intermediate
  iteration (`/tmp/b3-green1.log`, 1 fail): scoped-row fixture prefix `CT` collided with
  the scope tag inside the same row — fixture fixed once, no retry_count escalation.
- Build: `pnpm build` → exit 0 (`tsc --noEmit` over src incl. tests + vite build;
  `/tmp/b3-build.log`; pre-existing chunk-size warning only).

## Files changed
- `lumen-web/src/api/number-rules.ts` (+6)
- `lumen-web/src/api/us-orders.ts` (+3)
- `lumen-web/src/pages/system/number-rule/index.tsx` (+60/-7 approx)
- `lumen-web/src/pages/us/registration/index.tsx` (+5/-2)
- `lumen-web/src/test/number-rule-page.test.tsx` (5 new tests + mock wiring)
- `lumen-web/src/test/us-registration-page.test.tsx` (assertion tightened + 1 new test)

## Self-review findings
- `''` sentinel vs `null` option value: rc-select renders placeholder for a `null` value, so
  the explicit 全局 option uses `''`; the wire body never sees `''` (`|| null`). The
  spec scenario "choosing 全局 submits NULL" is exercised by an explicit option click.
- Edit echo of an out-of-catalog token relies on antd rendering the raw value — that default
  is now pinned by the `DX` test so a regression fails loudly.
- Removed the committed `TEMP PROBE` console.log block from number-rule-page.test.tsx
  (debug leftover in the 'create drawer selects an available family' test) — test-file
  hygiene in a file already being edited.
- Dict mock dispatch extended by catalog id (1024 → DR/CT/MR/US per the V27 seed); the mock
  factory now exports `MODALITY_DICT_ID` (the page imports it).
- Verbatim A0714 scoped message quoted from the backend
  (`NumberRuleServiceImpl.duplicateActive`): `号码规则冲突: rule_type=登记号 modality=CT
  已存在同范围启用规则（同族同范围仅允许一条启用规则）`.
- Whole-suite run deferred to the final batch per coordinator instruction; the only other
  consumers of these clients are the two touched test files (grep-verified).
- `openspec/sdd/progress.md` shows pre-existing modifications from a parallel session —
  not touched by this batch.

## Issues or concerns
- None blocking. Note: `UsOrderView.examItems` refs may now also carry `modality` on reads
  (batch 2 widened the kernel jsonb); the optional field keeps the display path
  (itemName-only) unaffected.
