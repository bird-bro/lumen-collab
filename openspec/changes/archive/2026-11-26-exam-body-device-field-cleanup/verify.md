---
verify:
  change: exam-body-device-field-cleanup
  date: 2026-11-26
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "2/2"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "2/2"
      L3: PASS
---

# Verification: exam-body-device-field-cleanup

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS | 2/2 | PASS (321 tests) | logs/build-backend.log · logs/test-backend.log |
| lumen-web | PASS | 2/2 | PASS (516 tests) | logs/build-lumen-web.log · logs/test-lumen-web.log |

## Scenario Alignment

| Scenario | Status | Evidence |
|:--|:--|:--|
| Body belongs to a group and may nest | IMPLEMENTED | ExamBodyServiceImpl.java:84-86 (create guards) · :102-103 (update guards) · SettingSchemaSeedTests.java:71-74 (NOT NULL anchors) |
| Body carries the device-facing fields | IMPLEMENTED | V54__exam_body_device_field_cleanup.sql:14-15 (drop+rename) · ExamBody.java:51-59 · BodyCreateRequest.java:38-40 · BodyUpdateRequest.java:38-40 · ExamBodyServiceImpl.java:89 (create BeanUtil 1:1) · :112 (update explicit set) · BodyTreeNode.java:21 (echo) · AuthExamBodyApiTests.deviceFieldsEchoAndStayNullable (red→green) |
| The catalog forms maintain the device fields | IMPLEMENTED | exam-body/index.tsx:686 (部位token→deviceBodyPart, 拉丁名 removed) · :335 (echo init) · :357/:369 (payloads) · :433-434 (single-term 设备 column) · exam-method/index.tsx:38/119/135 (deviceDesc untouched) · exam-body-page.test.tsx:179-217 (red→green) |
| The demo catalog ships the device values | IMPLEMENTED | V53 seed (34 bodies w/ terms, 46 items, 58 links) + V54 (name column dropped) · ExamCatalogDemoSeedTests.spotRowsCarryTheReviewedDeviceValues (1011=ABDOMEN_UPPER) · SettingSchemaSeedTests BODY_COLUMNS (10+5=15) |

## Issues

(none open)

### Verification-run history (fixes applied mid-verify, then re-credentialled)

1. Delta spec: the MODIFIED scenario had been renamed (`...fields` → `...field`),
   violating the layering rule (MODIFIED must keep the main spec's scenario
   names) — `openspec validate` caught it; name restored, validate now 0 issues.
2. L3 run 1 FAILED (322 tests, 1 failure): `SettingSchemaSeedTests` expected the
   V53 column list. Root causes found and fixed under this change's scope:
   - `SettingSchemaSeedTests.BODY_COLUMNS` still listed `device_token` +
     `device_name` → updated to `device_body_part`.
   - `ExamCatalogDemoSeedTests.seedStatementsReExecuteAsARowCountNoOp` re-executed
     the whole V53 file as an idempotency proof — post-V54 its
     `ADD COLUMN IF NOT EXISTS` resurrects the dropped columns on the shared test
     DB (silent schema pollution; the row-count assertion still passed). The proof
     was only valid while V53 was the ledger tail; removed (Flyway never re-runs a
     superseded row — the scenario is not real), and the shared DB's resurrected
     columns were dropped after a byte-equality check confirmed
     `device_body_part` carried all values.
3. L3 credential runs after the fixes: backend 321 tests exit=0; web 516 tests
   exit=0. One earlier full-suite flake (us-registration birthdate, non-adjacent
   file) passed isolated + on rerun — known family.

## Next

- PASS -> archive exam-body-device-field-cleanup.
