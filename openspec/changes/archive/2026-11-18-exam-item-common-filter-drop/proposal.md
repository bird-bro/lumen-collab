# Proposal — exam-item-common-filter-drop

## Why

The 检查项目 page's filter-bar 常用项目 checkbox was never adopted (user ruling
2026-11-16, coordination doc `exam-item-polish.md` D1): delete it. The frontend half
lands in the sibling change `exam-item-polish-web`; this change removes the backend
half — the dead page-query parameter and its filter branch. The entity capability
STAYS: `exam_item.is_common`, `ExamItem.isCommon`, both write-request fields, and the
drawer switch (kept and relocated by the web change).

## What Changes

- **BREAKING** (internal console only, consumed by the same repo's web): `GET
  /api/v1/setting/exam-items/page` drops the `isCommon` query parameter.
- `ItemPageQuery`: remove the `isCommon` field; class javadoc loses the toggle
  mention.
- `ExamItemServiceImpl.getPage`: remove the 常用项目 filter branch (3 lines).
- `ExamItemService` + `ExamItemController`: javadoc/`@ApiOperation` prose loses the
  isCommon mention.
- Spec delta: exam-setting `Exam Item Endpoints` — page filters become `modality,
  groupId, keyword over name/code; paged`.
- **No DB change** (coordination D5): `is_common` column and
  `exam_item_common_idx` stay (V29 untouched, per the never-rewrite ruling).

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**: `exam-setting` — the `Exam Item Endpoints` requirement's
  page-filter list loses `isCommon`.

## Impact

- `lumen-system` setting domain: `ItemPageQuery`, `ExamItemServiceImpl`,
  `ExamItemService` (javadoc), `ExamItemController` (javadoc) — 4 files, no schema,
  no new tests needed (no test ever drove the filter; the existing suite is the
  guard).
- Lockstep: the web change removes the only caller in the same archive batch.
