# Proposal — exam-setting-api

## Why

The exam workflow (booking → registration → exam → report) has no dictionary layer:
开单 needs exam items, items need body-part grouping, report writing needs phrase
templates. The RCP reference system proves the feature family (`/setting/*`), and the
2026-11-12 brainstorm re-modeled it for lumen (decisions in
`openspec/coordination/exam-setting.md`): a standalone hospital-wide service instead
of RCP's platform-scoped pile-up, split body/group tables instead of the synthetic
root tree, a join table instead of comma-joined methods, and two-scope report
templates. v1 radiology lands first; us/endo inherit the same catalog later.

## What Changes

- New backend service `lumen-setting` (package `org.birdbro.lumen.setting`): Maven
  module + aggregator declaration + `application.yml` pinning Flyway to the new
  `setting` schema; nginx exposes it under `/api/v1/setting/**`.
- New schema `setting` (own DB role) with six tables: `exam_body_group`, `exam_body`,
  `exam_method`, `exam_item`, `exam_item_method`, `rep_temp` — all serial Integer PK
  + EntityBase audit tail, soft delete.
- Exam catalog CRUD APIs: items (page/detail/create/update/delete, with
  price/rate/tag/score_rep/score_aud/is_common), body-groups (flat list CRUD),
  bodies (tree CRUD under a group), methods (page/list CRUD); item↔method join
  rewritten transactionally on item update; service-layer delete guards for
  referenced rows.
- Report-template APIs: folder/template tree by modality+scope, file page, CRUD;
  个人 rows owner-enforced, 公共 rows perms-gated.
- lumen-system migrations (same backend stack): menu seeds — L2 group 「检查设置」
  + four C pages + F button nodes + admin auto-grant (V15/V27 discipline); the modality
  vocabulary rides the already-complete dict 1027 检查类型 (V27) — no dict data
  migration.
- Test suite follows the AuthDeviceRoom* family: API-level integration tests +
  perm-pin + seed re-execution tests, zero infrastructure.

Out of scope (recorded, deferred): item↔HIS/platform mapping (integration domain),
print templates, performance calculation, booking-side consumption APIs
(`FLAG.business`-style), MWL mapping, 科室-scope templates.

## Capabilities

### New Capabilities

- `exam-setting`: the 检查设置 domain — `setting` schema data model (six tables),
  exam-catalog endpoints, report-template endpoints, scope/delete-guard behavior.
  Owns the whole service's behavior so later additions (mapping, print templates,
  booking-side reads) extend it.

### Modified Capabilities

- `service-topology`: the deferred-module enumeration gains `lumen-setting` (the
  change that gives the module behavior declares it in the aggregator).
- `data`: Schema-per-Service enumeration grows `setting` (six → seven schemas);
  initialization scenario covers the new schema/role.
- `system-data`: menu seeds for the 检查设置 console group (pages + button perms)
  and the exam-modality vocabulary completion (dict 1021 DR row).

## Impact

- `lumen-sev/pom.xml` — new module declaration.
- New `lumen-sev/lumen-setting/**` — controller/service/mapper/entity/dto layers.
- New `lumen-sev/lumen-setting/src/main/resources/db/migration/V1__setting_schema.sql`
  (+ data seeds if any) — schema-scoped Flyway.
- `lumen-sev/lumen-system/src/main/resources/db/migration/V2x__exam_setting_menu.sql`
  — menu + dict seeds (menu_info/dict live in the system/public schemas).
- `docs/deployment/pg-init.sql` — `setting` schema + `lumen_setting_role` grants.
- No changes to existing services' Java code; no MQ events; no new Feign contracts.
