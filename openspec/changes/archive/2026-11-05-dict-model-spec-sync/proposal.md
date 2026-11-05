# Proposal

## Why

The `system-data` capability spec still describes the pre-V6 dictionary model, but the
shared test database has already converged on the slimmed model (user rulings
2026-11-05, migrations V6/V7/V9 applied by lumen-system boots): the detail table anchors
by `caId` alone, carries ONE text field (`value`), and its spare pair is
`tag`/`flag`. Spec and reality now disagree on the field list, the anchor, and the seed
set — the next change that reads the spec would re-implement the dropped columns.

## What Changes

- **Dict Detail Management requirement rewritten** (spec delta over `system-data`):
  anchor is `caId` alone (the denormalized `caCode` column is gone — V9); `value` is the
  single text field (display + stored text; the duplicated `label` column is gone);
  spare slots renamed `tag`/`flag` (from `back1`/`back2`); pinyin `initial` dropped from
  detail AND catalog; detail-level uniqueness = live `(caId, value)` pair (partial
  unique index, `A`-class rejection unchanged).
- **Dict Catalog Management requirement**: field list drops `initial` (code/name/
  enable/sort/remark/cls remain).
- **Dict Seed Data requirement updated**: the starter set is no longer V3's minimal
  `common_status` alone — fresh databases now carry 7 catalogs / 30 details:
  `common_status` (re-based to id 1000 by V7, its two details carry the display text in
  `value`: 启用/停用) plus six real person/org dictionaries curated from the rcp-test
  reference database by V6 (bloodType/marital/gender/jobTitle/idType/orgType).
- **Purpose statement** reworded: `label↔value` phrasing → single `value` text field.
- NO code changes: migrations V6/V7/V9 and the matching backend/frontend code are
  already implemented and verified (65 lumen-system tests green). This change only
  brings the spec back in sync; its scenarios map onto the existing
  `AuthSystemDataMigrationTests` / `AuthDictCrudTests` coverage.

Scope boundary (recorded assumption, confirmed with the user 2026-11-05): the console
MENU seed/IA reality (V5/V8/V10) is NOT specced anywhere today (auth-rbac's Seed Data
requirement only names admin + three departments; data spec has no menu section), so
there is nothing to sync for menus in this change — menu requirements land with the
queued `workstation-context` change.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `system-data`: Dict Detail Management / Dict Catalog Management / Dict Seed Data
  requirements re-specified to the V6–V9 landed model; Purpose reworded.

## Impact

- `openspec/specs/system-data/spec.md` — the only spec touched (via this change's delta,
  applied at archive/sync time).
- No production code, API paths, or dependencies affected: endpoints
  (`/api/v1/system/dicts/**`), error codes (`A0714` duplicate, `A0400` unknown catalog),
  and the detail query contract (`GET /list?caCode=` — the param resolves to `caId`
  internally) are unchanged; only the persisted field shape behind them differs.
- Existing test coverage (AuthSystemDataMigrationTests, AuthDictCrudTests,
  AuthDataLayerTests) already asserts the post-V9 behavior — they double as this
  change's L2 verification evidence.
