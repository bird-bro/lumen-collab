# Tasks

Backend-only structural change (the frontend and its tests are untouched — URL/perms
contract preserved). The implementation was executed in the working tree during the
2026-11-13 ruling session; these tasks pin the apply-phase proof obligations. TDD
discipline: the suite IS the red-green evidence — the first merged run (212 errors,
dual-UserDetailsService `NoUniqueBeanDefinitionException`) was RED, the fix + rerun is
GREEN.

## 1. Package hosting

- [x] 1.1 git mv `org.birdbro.lumen.setting.{controller,dto,entity,mapper,service,service.support}` +
  the `org.birdbro.lumen.setting` test tree from the retired `lumen-setting` module into
  `lumen-sev/lumen-system/src/{main,test}/java/...` — package FQNs unchanged; delete
  `SettingApplication` + `SettingUserDetailsService` (D1: single boot shell, single
  UserDetailsService). Verify: `./mvnw compile -q` green from the aggregator; no
  `SettingApplication`/`SettingUserDetailsService` class files remain.
- [x] 1.2 Aggregator `lumen-sev/pom.xml` module list drops `lumen-setting`; the retired
  module directory is removed (tracked files git rm'd; build artifacts swept). Verify:
  no `pom.xml` under `lumen-sev/lumen-setting/`; reactor resolves.

## 2. Schema re-home (one ledger)

- [x] 2.1 `V1__setting_schema.sql` → `lumen-system/.../db/migration/V29__exam_setting_tables.sql`
  (git mv; DDL unqualified, `IF NOT EXISTS` idempotence + no-semicolon comment
  discipline preserved; header cites the merge ruling). Verify: `SettingSchemaSeedTests`
  re-execution proof passes against the system schema.
- [x] 2.2 Test family schema retarget (D3): `SettingIntegrationTestBase` datasource
  `currentSchema=system,public` + Flyway props → `system` + sweep SQL `system.*`;
  `SettingTestServerBootstrap` ensures the `system` schema; `SettingSchemaSeedTests`
  schema strings → `system`, re-execution source → V29. Verify: the setting suites run
  inside lumen-system's surefire fork against the shared ledger.

## 3. Platform records

- [x] 3.1 `docs/deployment/pg-init.sql`: `setting` schema + `lumen_setting_role` block
  removed, shared-grant lists back to six roles. `.gitignore`: the retired
  lumen-setting dev-yml ignore line removed.
- [x] 3.2 `.claude/rules/naming.md` "Hosted domain packages" precedent recorded;
  `lumen-sev/CLAUDE.md` module-list line updated;
  `openspec/coordination/exam-setting.md` § "2026-11-13 Merge Ruling" appended
  (done at propose time).

## 4. Verification + cleanup

- [x] 4.1 Full suite green: `./mvnw test` from the aggregator exits 0 (log path +
  counts reported, no full-log dump). RED→GREEN chain recorded (run2: 212
  bean-ambiguity errors → shell-class deletions; run3: config-discovery errors →
  `classes=SystemApplication`; run4: qualified-SQL + V29 comment-semicolon fixes;
  run5: checksum drift → V29 history row reset; final run: 241/241 green, 0F/0E,
  logs/test-backend.log).
- [x] 4.2 Team test DB cleanup: confirmed the retired `setting` schema held fixture
  residue only (2 ut_exb_ bodies + 1 ut fixture item + its binding; zero real catalog
  rows), then `DROP SCHEMA setting CASCADE` (hard delete, 2026-11-07 ruling). Verify:
  `pg_namespace` lists only `public` + `system`.
- [x] 4.3 Run `/opsx:verify merge-setting-into-system` (L1/L2/L3) and refresh the
  archive credential; archive-time sync updates the exam-setting main spec's Purpose
  line to the hosted wording. Verify.md overall=PASS (17/17 scenarios, L1 exit=0,
  L3 241/241 green — logs/build-backend.log · logs/test-backend.log).
