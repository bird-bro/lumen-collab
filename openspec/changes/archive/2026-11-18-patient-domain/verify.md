<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: patient-domain
  date: 2026-11-18
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "21/21"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "5/5"
      L3: PASS
---

# Verification: patient-domain

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit=0) | 23/23 | PASS (`./mvnw test -q` full reactor, exit=0, 0 failure lines) | logs/build-backend.log / logs/test-backend.log |
| lumen-web | PASS (`pnpm build`, exit=0) | 5/5 | PASS (`pnpm test`, 33 files / 392 tests green on rerun) | logs/build-lumen-web.log / logs/test-lumen-web-rerun.log |

## Scenario Alignment

### specs/patient-domain/spec.md (21 scenarios + 2 contract edge cases)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Creation issues image number and pinyin | IMPLEMENTED | lumen-sev/lumen-patient/.../impl/PatientArchiveServiceImpl.java:123-151; PatientArchiveServiceTests.java:78 |
| Merged archive stays traceable | IMPLEMENTED | PatientArchiveServiceImpl.java:206 (getById, no merge filter); PatientMergeServiceTests.java:113 |
| Duplicate id_no is rejected | IMPLEMENTED | PatientArchiveServiceImpl.java:123 (A0715) + :144 (DKE backstop); PatientArchiveServiceTests.java:133 |
| Card numbers are not unique | IMPLEMENTED | PatientArchiveServiceTests.java:153 (same card_no, two rows) |
| Merged rows do not block re-issuance | IMPLEMENTED | PatientArchiveServiceTests.java:170 |
| Exact id_no hit resolves immediately | IMPLEMENTED | PatientArchiveServiceImpl.java:234 (chain step 1) |
| Soft match returns candidates | IMPLEMENTED | PatientArchiveServiceImpl.java:231-290 (soft union branch); PatientArchiveServiceTests resolve-branch suite |
| No match creates a new archive | IMPLEMENTED | PatientArchiveServiceImpl.java (resolve tail create); PatientApiTests.java:121,157 |
| Emergency placeholder receives an image number | IMPLEMENTED | PatientArchiveServiceTests.java:104-118 (PROVISIONAL + pat_id) |
| Minimal merge writes pointer and log | IMPLEMENTED | PatientArchiveServiceImpl.java:293-346; PatientMergeServiceTests.java:68 |
| Merge is idempotent | IMPLEMENTED | PatientMergeServiceTests.java:128-153 (no new log/outbox rows) |
| Default search hides merged rows | IMPLEMENTED | PatientArchiveServiceImpl.java:207-225 (page filters live rows); javadoc :45 |
| Concurrent issuance does not duplicate | IMPLEMENTED | PatientNumberServiceImpl.java:46-48 (REQUIRES_NEW + FOR UPDATE); PatientNumberIssuanceTests.java:53 (8x5 = 40 distinct) |
| Stamp rule resets by day | IMPLEMENTED | PatientNumberServiceImpl.java:57-81 (stamp_value compare + padPre); PatientNumberIssuanceTests.java:85 |
| Correction writes a change event | IMPLEMENTED | PatientArchiveServiceImpl.java (update → PATIENT_CHANGED in-tx); PatientOutboxTests.java:68 |
| Merge writes a merged event | IMPLEMENTED | PatientArchiveServiceImpl.java (resolveOrMerge → PATIENT_MERGED, payload names both ids); PatientOutboxTests.java:88 |
| No broker is required | IMPLEMENTED | PatientOutboxTests.java:113 (PENDING accumulation; suite runs brokerless); PatientEventOutboxService.java:37 (append-only interface) |
| Rule CRUD enforces perms | IMPLEMENTED | PatientNumberRuleController.java:42-79 (class gate + button pins); PatientPermTests.java:37-152 (A0301 / 203) |
| Creation resolves the active rule | IMPLEMENTED | PatientNumberRuleMapper.java:29-31 (verbatim FOR UPDATE select); PatientNumberIssuanceTests (active-rule resolution) |
| Endpoints are reachable under the unified entry | IMPLEMENTED | PatientNumberRuleController.java:44 (`/api/v1/patient/patient-number-rules`); PatientController (6 archive routes); PatientApiTests.java:43-389 (10 routes) |
| Service identity is declared | IMPLEMENTED | lumen-patient/src/main/resources/application.yml (name lumen-patient, port 8082, flyway schemas patient); PatientSchemaMigrationTests |
| Rule delete on the active rule rejected *(contract edge case, beyond the 21 delta scenarios)* | IMPLEMENTED | PatientNumberRuleServiceImpl.java:116-123 (A0400 先停用); PatientApiTests.java:312-317 |
| Update-path activation conflict A0714 *(contract edge case)* | IMPLEMENTED | PatientNumberRuleServiceImpl.java (DKE backstop + gated check); PatientApiTests.activatingASecondRuleForOneTypeViaUpdateConflicts |

### specs/system-data/spec.md (4 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Fresh deployment seeds the patient config group | IMPLEMENTED | lumen-patient/.../db/migration/V2__patient_menu.sql (content-guarded L2 患者配置 + 号码规则 C + F buttons, icon team, sort 3); PatientMenuSeedTests.java:63 |
| Non-admin grants keep their chain | IMPLEMENTED | V2 grant carry-over section; PatientMenuSeedTests.java:102 (synthetic grant + re-run completes ancestors) |
| Re-execution is a no-op | IMPLEMENTED | PatientMenuSeedTests.java:142,181 (row counts stable, setval convergence) |
| Admin role receives the button grants | IMPLEMENTED | V2 admin auto-grant (role 1, NOT-EXISTS); PatientMenuSeedTests.java:63-101 |

### specs/web-system-console/spec.md (5 scenarios)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Page loads against the real backend | IMPLEMENTED | lumen-web/src/pages/patient/number-rule/index.tsx (pageNumberRules on mount, no mock); number-rule-page.test.tsx (mount page test) |
| Toolbar follows button perms | IMPLEMENTED | index.tsx (hasPerm patient:rule:create/update/delete gates); number-rule-page.test.tsx perm-gating describe |
| Create a stamped rule | IMPLEMENTED | index.tsx save() (ruleType pat_id, stamp null→server default yyMMdd); number-rule-page.test.tsx create test |
| Invalid serial length surfaces on the field | IMPLEMENTED | index.tsx (form rule 1-10 — InputNumber deliberately unclamped so the field error surfaces, drawer stays open); number-rule-page.test.tsx (creates not called, error text visible) |
| Delete requires confirmation | IMPLEMENTED | index.tsx Popconfirm; number-rule-page.test.tsx (refusal verbatim 启用的规则不可删除) |

## Issues

- None blocking. Notes:
  - lumen-web L3 first run hit the KNOWN stochastic flake (exam-body-page.test.tsx "multiple tooltip" isolation noise — a parallel session's WIP file, not touched by this change); rerun = 33 files / 392 tests green. Recorded in logs/test-lumen-web.log + logs/test-lumen-web-rerun.log.
  - `openspec validate` reports 3 style WARNINGs (requirement text >500 chars in patient-domain/spec.md) — wording guidance, not verification failures.
  - Production deployment note: enable the commented lumen_patient_role GRANT block (incl. sequence UPDATE) in docs/deployment/pg-init.sql before first production boot (V2 cross-schema menu seed).

## Next

- PASS -> archive patient-domain via /opsx:archive (after the coordination gate: all registered changes of the feature must be verify-PASS — this is the only registered change).
