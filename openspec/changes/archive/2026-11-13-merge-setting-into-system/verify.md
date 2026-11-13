<!--
  Written by the verify step. This is the archive credential. Archive parses `overall`.
  Do not hand-edit; re-run verify to refresh. Keep it compact: no raw logs or source excerpts.
-->
---
verify:
  change: merge-setting-into-system
  date: 2026-11-13
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "17/17"
      L3: PASS
    lumen-web:
      L1: SKIP
      L2: SKIP
      L3: SKIP
---

# Verification: merge-setting-into-system

Overall: **PASS** — backend L1 exit=0; L3 exit=0 with the FULL configured suite green
(241/241 tests, 0 failures / 0 errors / 0 skipped across 49 test classes, one surefire
fork — the former two-module split now runs as one); every one of the 17 delta-spec
scenarios carries concrete code/DB evidence. lumen-web SKIP: the change touches zero
frontend files by design (URL/perms contract preserved) — scope guard per
verify.config.yaml.

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (exit=0) | 17/17 | PASS (241/241, 0F/0E) | logs/build-backend.log · logs/test-backend.log |
| lumen-web | SKIP | SKIP | SKIP | not affected (zero frontend diffs from this change) |

## Scenario Alignment (17/17 IMPLEMENTED)

| Scenario | Status | Evidence |
|:--|:--|:--|
| Foundation modules resolve | IMPLEMENTED | lumen-sev/pom.xml:11-15 (four modules); L1 exit=0; no `lumen-setting` module dir remains |
| Deferred module requested | IMPLEMENTED | pom module list carries no placeholder (lumen-sev/pom.xml:11-15) |
| Hosted domain discovered by the hosting service | IMPLEMENTED | SystemApplication.java:29-30 (unchanged org.birdbro scan + wildcard MapperScan register the setting package); exactly one app class (no SettingApplication/SettingUserDetailsService anywhere — find=0); suites drive the setting controllers through the booted context |
| Hosted domain migrates through the one ledger | IMPLEMENTED | application.yml:22-23 (schemas/default-schema: system); V29__exam_setting_tables.sql in the chain; SettingSchemaSeedTests.migrationReExecutionIsARowCountNoOp green |
| Hosted domain keeps its external contract | IMPLEMENTED | ExamBodyController.java:38 `@RequestMapping("/api/v1/setting/exam-bodies")` unchanged (pure git rename); V28 menu seeds untouched; zero lumen-web diffs from this change |
| Role isolation denies cross-schema access (data) | IMPLEMENTED | pg-init.sql six-role grant structure unchanged (DBA deliverable; carried from the archived exam-setting-api verify) |
| Schemas exist after initialization | IMPLEMENTED | docs/deployment/pg-init.sql:8-13 — six CREATE SCHEMA, no `setting` |
| Shared system tables reachable | IMPLEMENTED | DictModalityMapper.java:30-31 unqualified name-join resolves public dict tables at runtime (suite green) |
| Service migrates only its own schema (exam-setting) | IMPLEMENTED | V29 applied through the single `system.flyway_schema_history` (run-5 boot re-applied after the history-row reset); six exam tables verified in the system schema (SettingSchemaSeedTests green) |
| Role isolation denies cross-schema access (exam-setting) | IMPLEMENTED | test-DB inspection 2026-11-13: `pg_namespace` lists only public+system after DROP SCHEMA setting CASCADE; `pg_roles` carries no `lumen_setting_role`; pg-init.sql:8-13 |
| Fresh deployment creates the body-group columns | IMPLEMENTED | SettingSchemaSeedTests.settingTablesExistWithRuledColumns green (asserts the system schema, SettingSchemaSeedTests.java:112,126,141) |
| Body belongs to a group and may nest | IMPLEMENTED | AuthExamBodyApiTests.bodyTreeNestsByUpIdWithinTheGroupScope green |
| Fresh deployment creates the method columns | IMPLEMENTED | SettingSchemaSeedTests green (method table column set asserted) |
| Item stores the catalog facts and the score weights | IMPLEMENTED | AuthExamItemApiTests.itemUpdateIsNullAware green |
| Item code uniqueness | IMPLEMENTED | AuthExamItemApiTests.itemCodeUniqueness green |
| Item update rewrites method bindings atomically | IMPLEMENTED | AuthExamItemTransactionTests green (failure-injection case included) |
| Public and private templates share one tree model | IMPLEMENTED | AuthRepTempApiTests green (tree scope + owner binding cases) |

## Issues

- RED→GREEN chain of the merged suite (all four layers surfaced and fixed inside this
  change): run2 212 errors = dual `UserDetailsService` beans (`SettingApplication` +
  `SettingUserDetailsService` were not deleted in the first move — deleted, D1); run3
  config-discovery errors = Boot's upward `@SpringBootConfiguration` walk never searches
  the sibling package → `@SpringBootTest(classes = SystemApplication.class)` pinned
  (design D3); run4 = suites' schema-qualified cleanup SQL (`FROM setting.*`) hitting the
  retired schema's old tables + a comment semicolon breaking the V29 re-execution proof —
  both fixed, residue swept; run5 = checksum drift from editing the already-applied V29 →
  history row deleted and V29 re-applied cleanly (flyway-never-rewrite ruling). Final
  run: 241/241 green.
- Workspace observation (not this change's scope): lumen-web carries 9 uncommitted
  entries from the concurrent session — untouched by this change.

## Next

- PASS → archive ready (`/opsx:archive merge-setting-into-system`). Archive-time sync
  updates the exam-setting main spec's Purpose line to the hosted wording (task 4.3).
