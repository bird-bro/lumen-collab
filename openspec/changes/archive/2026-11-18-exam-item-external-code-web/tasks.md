# Tasks — exam-item-external-code-web

## 1. Tests first

- [x] 1.1 `exam-item-page.test.tsx`: drawer exposes labeled 对照编码 field right
      after 项目编码; create/update payloads carry `externalCode`; edit echo fills
      it from the detail
- [x] 1.2 `exam-item-page.test.tsx`: table renders 对照编码 column (— for null)
      right after 项目编码
- [x] 1.3 `setting-api.test.ts`: `CreateExamItemBody`/`ExamItem` types carry
      `externalCode` (type-level assertions per the file's existing style)

## 2. Implementation

- [x] 2.1 `api/types.ts`: `ExamItem.externalCode` + `CreateExamItemBody.externalCode`
- [x] 2.2 Page: drawer 对照编码 field (Input, maxLength 32, after 项目编码);
      table 对照编码 column (— for null, after 项目编码); save payloads carry
      `externalCode ?? null`; header comment updated

## 3. Verification

- [x] 3.1 `pnpm test` green (full suite) — ledger in the apply log
- [ ] 3.2 Visual check against the REAL backend (`pnpm dev`, local lumen-system
      with the sev change applied) — user confirms before archive
- [ ] 3.3 `/opsx:verify` PASS — verify.md written
