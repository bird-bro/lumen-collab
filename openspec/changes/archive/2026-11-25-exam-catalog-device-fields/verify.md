<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: exam-catalog-device-fields
  date: 2026-11-25
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "9/9"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: exam-catalog-device-fields

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 9/9 | PASS | logs/build-backend.log · logs/test-backend.log |
| lumen-web | PASS | 2/2 | PASS | logs/build-lumen-web.log · logs/test-lumen-web.log |

- backend L3: `./mvnw test -q` full reactor exit 0 — **523 tests**; one transient
  WAN-transport error (UsSlotConfigApiTests 尝试连线已失败, the documented
  公网断连簇) passed green in isolation — the shared-DB hazards pattern, not this
  change.
- web L3: `pnpm test` exit 0 — **36 files / 516 tests / 0 failures**.

## Scenario Alignment

### specs/exam-setting/spec.md (9 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Body belongs to a group and may nest | IMPLEMENTED | unchanged (ExamBodyServiceImpl group/parent guards); suite green |
| Body carries the device-facing fields | IMPLEMENTED | ExamBody.deviceToken/deviceName (ExamBody.java:57/:59) + BodyCreate/UpdateRequest + the tree-node echo (BodyTreeNode extends the entity) + AuthExamBodyApiTests.deviceFieldsEchoAndStayNullable |
| Fresh deployment creates the method columns | IMPLEMENTED | V53 ALTERs (V53:29-32) + SettingSchemaSeedTests column pins (updated with the device columns) |
| Method carries the device-facing description | IMPLEMENTED | ExamMethod.deviceDesc + the update chain .set + create/create-request; AuthExamMethodApiTests.deviceDescEchoesReplacesAndStaysNullable |
| Item stores the catalog facts and the score weights | IMPLEMENTED | unchanged green |
| Item code uniqueness | IMPLEMENTED | unchanged green |
| Item stores the external mapping code | IMPLEMENTED | unchanged green |
| Duplicate external mapping codes are accepted | IMPLEMENTED | unchanged green |
| Item carries the device-facing description | IMPLEMENTED | ExamItem.deviceDesc (ExamItem.java:56) + ItemCreate/UpdateRequest + the update chain .set + AuthExamItemApiTests.deviceDescEchoesReplacesAndStaysNullable |

### specs/web-system-console/spec.md (2 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| The catalog forms maintain the device fields | IMPLEMENTED | exam-method/index.tsx (设备描述 form :319 + column :193), exam-body/index.tsx (部位token/拉丁名 + the compact 设备 column), exam-item/index.tsx (设备描述 form + column); the three page tests pin form echo, PUT payload, blank→null/em-dash |
| The demo catalog ships the device values | IMPLEMENTED | V53 PART-3 backfill (keyed (modality, code) + seeded ids) with the user-reviewed Latin values (D4 gate passed 2026-11-25); ExamCatalogDemoSeedTests spot rows assert the device values + the dangle-free five-table closure |

## Issues

- The demo seed's body-count assertion counts by the seeded EXPLICIT ids — the
  shared DB carries 11 extra hand-applied marked bodies beyond the closure that
  are pre-existing environment data, not the seed's responsibility (documented in
  the test).

## Next

- PASS → archive exam-catalog-device-fields.
