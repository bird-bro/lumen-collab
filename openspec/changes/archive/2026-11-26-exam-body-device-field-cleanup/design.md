# Design: exam-body-device-field-cleanup

## Context

V53 (exam-catalog-device-fields, archived 2026-11-25) added `exam_body.device_token`
+ `device_name` plus `device_desc` on item/method. Post-hoc review (2026-11-26,
user-approved): `device_name` has no MWL consumer — the body's only device slot,
Body Part Examined (0018,0015), is filled by the token itself — and "token" is an
ambiguous name. The MWL composer does not exist yet, so the rename is free now and
expensive later. Reference set (grep-verified): `ExamBody`, `BodyCreateRequest`,
`BodyUpdateRequest`, `AuthExamBodyApiTests`, `ExamCatalogDemoSeedTests`, the
exam-body frontend page + `api/types.ts` + page tests. Nothing else.

## Goals / Non-Goals

**Goals:** one V54 migration (drop + rename); backend and frontend follow the new
names; the orphaned uncommitted device-fields tests (f1b6e96 never landed them)
are rewritten to the new names and committed with this change.

**Non-Goals:** touching `device_desc` on item/method (unaffected); editing V53;
touching any us-slot-options-window-fix file (in-flight in the shared worktree);
re-seeding or regenerating Latin names.

## Decisions

- **D1 — V54 as one ALTER, V53 untouched**: `ALTER TABLE exam_body DROP COLUMN
  device_name, RENAME COLUMN device_token TO device_body_part;` (Postgres allows
  both in one statement). V53 is applied and archived — never rewritten. Fresh
  installs replay V53's seed (writes `device_name`, then V54 drops it): harmless
  dead weight in the history, the shared DB converges on the next backend run.
- **D2 — JSON key `deviceBodyPart`**: payload/VO key follows the entity field, the
  same verbatim-echo contract as before. The admin UI label 部位token stays (user
  vocabulary, unchanged); only the wire name changes.
- **D3 — orphaned tests rewritten in place**: `AuthExamBodyApiTests`'s
  uncommitted `deviceFieldsEchoAndStayNullable` (+48 lines) switches to
  `deviceBodyPart` / drops the `deviceName` assertions and lands with this change
  (fixes the f1b6e96 path-scoped commit miss). `AuthExamItemApiTests` /
  `AuthExamMethodApiTests` (+54/+57, `deviceDesc` only) need no edits.
- **D4 — seed display drops the name half**: the exam-body page tree echo
  (`token · name` with the `·` joiner) collapses to the single term; `api/types.ts`
  loses `deviceName`, renames `deviceToken` → `deviceBodyPart`.

## Risks / Trade-offs

- [Shared test DB converges only when the backend runs] → the next test run
  applies V54; until then the old columns linger alongside the new code (no
  interference — MyBatis-Plus maps by entity property, and the running jar is
  rebuilt before tests).
- [Seeded Latin names are irrecoverable on the shared DB] → accepted by the user;
  V53's INSERT text in the migration history preserves the values if ever needed.
- [Parallel dirty worktree] → commits stay path-scoped to this change's files;
  us-slot-options-window-fix files are never staged.

## Migration Plan

1. V54 lands in the lumen-system ledger; backend code + tests renamed.
2. Frontend page + types + tests renamed.
3. Verify: backend `AuthExamBodyApiTests` (+ seed test) green against the shared
   DB after V54 applies; frontend page tests green.

## Open Questions

(none)
