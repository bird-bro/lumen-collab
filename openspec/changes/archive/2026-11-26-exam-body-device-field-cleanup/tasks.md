# Tasks

## 1. Migration + backend rename

- [x] 1.1 Add `V54__exam_body_device_field_cleanup.sql` to the lumen-system
  ledger: `ALTER TABLE exam_body DROP COLUMN device_name, RENAME COLUMN
  device_token TO device_body_part;` — verify by running the lumen-system tests
  (V54 applies on the shared DB; a second run replays as a no-op error-free).
- [x] 1.2 Rename `ExamBody.deviceToken` → `deviceBodyPart`, delete
  `deviceName` (javadoc updated to the Body Part Examined anchor); rename/delete
  the same fields in `BodyCreateRequest` + `BodyUpdateRequest` — verify:
  `./mvnw compile -q` exits 0 (only tests still reference the old names).
- [x] 1.3 Rewrite the orphaned `deviceFieldsEchoAndStayNullable` in
  `AuthExamBodyApiTests` to `deviceBodyPart` (drop the `deviceName` echo
  assertions); update `ExamCatalogDemoSeedTests` body assertions if any —
  verify: `./mvnw test -q -pl lumen-system -Dtest='AuthExamBodyApiTests,ExamCatalogDemoSeedTests'`
  exits 0 (report exit code + counts, log to file).

## 2. Frontend rename

- [x] 2.1 `src/api/types.ts`: exam-body types lose `deviceName`, rename
  `deviceToken` → `deviceBodyPart` — verify: `pnpm build` (or `tsc -p .`) exits 0
  with the page edits from 2.2 in place.
- [x] 2.2 `src/pages/setting/exam-body/index.tsx`: remove the 拉丁名 form item and
  the `token · name` tree echo (single term renders, em-dash when NULL), rename
  payload keys to `deviceBodyPart`; update `src/test/exam-body-page.test.tsx` to
  the new wire name and the absent 拉丁名 field — verify:
  `pnpm vitest run src/test/exam-body-page.test.tsx` exits 0.

## 3. Change-scoped regression

- [x] 3.1 Run the full frontend suite once the page tests are green —
  verify: `pnpm vitest run` exit 0 (counts + log path reported).
