# Tasks: exam-catalog-device-fields

> Build from `lumen-sev/` (JDK 11 export; reactor). Dataset source: the shared
> test DB via the pgsql-test-lumen MCP. The user reviews the drafted Latin table
> BEFORE task 3 embeds it (the D4 gate).

## 1. Model + migration

- [x] 1.1 Entities + VOs: `ExamItem.deviceDesc`, `ExamBody.deviceToken` +
      `deviceName`, `ExamMethod.deviceDesc` (the create/update VOs and page VOs
      carry them; nullable) — verify: `./mvnw -pl lumen-system -am compile -q`
      exits 0
- [x] 1.2 The CRUD services persist and echo the fields verbatim (BeanUtil 1:1 —
      no mapper changes expected) — verify: the catalog API tests stay green
- [x] 1.3 `V53__exam_catalog_device_fields.sql`: the three additive ALTERs + the
      FIVE-table seed closure (23 exam_body_group / 34 exam_body / 15 exam_method /
      46 exam_item / 58 exam_item_method, explicit ids, ON CONFLICT (id) DO
      NOTHING, no-semicolon comments) + the device-value backfill UPDATEs (keyed
      (modality, code) over the seed's codes) + the setval tails (five sequences)
      — verify: the migration tests apply V53 on a fresh schema and replay as a
      no-op on the shared DB

## 2. Draft review gate (the D4 gate — present, wait for the user)

- [x] 2.1 Draft the Latin device values table (46 items / 34 bodies with DICOM
      tokens / 15 methods) and present it to the user for row-level review —
      verify: the user's corrections are applied before 3.1 embeds

## 3. Seed + test (after the review gate)

- [x] 3.1 Embed the reviewed values into V53 (the seed blocks + backfill) and
      write `ExamCatalogDemoSeedTests` (per-modality live counts 12/5/5/8/16, spot
      rows by (modality, code) incl. one device_desc assertion, zero dangling
      body_id, 58 method links, 23 groups + 15 methods present, idempotent re-run)
      — verify: green on the shared DB and a fresh schema
- [x] 3.2 `./mvnw -pl lumen-system -am test -q` exit 0 — verify: the seed rides
      the auth suite without fixture collisions

## 4. Frontend (after the backend user-verify gate)

- [x] 4.1 The three setting pages gain the device-field inputs (edit/create
      forms) + the device columns in their tables (检查项目 设备描述 / 检查部位
      部位token+拉丁名 / 检查方法 设备描述) — verify: the three page tests gain
      the field assertions, RED→GREEN; `pnpm build` + full suite exit 0

## 5. Verification hooks

- [x] 5.1 `openspec validate exam-catalog-device-fields` passes (the exam-setting
      MODIFIED supersets + the web-system-console ADDED)
