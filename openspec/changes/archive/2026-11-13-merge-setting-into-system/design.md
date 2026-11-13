# Design

## Context

The standalone `lumen-setting` service shipped via exam-setting-api (archived
2026-11-13, verify 29/29 PASS). The user then ruled the two-service topology redundant
(see proposal.md Why + coordination § "2026-11-13 Merge Ruling"). Facts driving the
design: both modules already declare identical dependency sets (bom-managed);
`SystemApplication` scans `org.birdbro` and `@MapperScan(basePackages =
"org.birdbro.**.mapper")` — the wildcard forms already discover the setting package;
`SettingUserDetailsService` is a verify-only stub (no account store) superseded by
system's real `UserDetailsServiceImpl`; the setting test family is a deliberate clone
of the `AuthIntegrationTestBase` pattern over the SAME `AUTH_TEST_*` env contract.

## Goals / Non-Goals

- Goals: one executable service (8081), one schema (`system`), one Flyway ledger
  (V1..V29), zero API/frontend churn, tests green in one surefire run.
- Non-Goals: renaming the module/package/app class (service-identity contracts stay);
  touching exam-setting endpoints, perms, menu seeds, or dict vocabulary; extracting
  anything else; docker/nginx changes (deferred deployment change).

## Decisions

### D1: Move the whole package tree, rename nothing

`org.birdbro.lumen.setting.*` (main + test) re-homes into `lumen-system`'s source roots
via git mv — package FQNs, class names, imports, and the
`dto/examsetting/` sub-package stay byte-identical, so git tracks renames and no import
rewrites exist. Alternative considered: flattening into `org.birdbro.lumen.system.*`
(deviceroom precedent) — rejected by user ruling: the separate package root IS the
future re-extraction boundary (naming.md "Hosted domain packages" precedent).
`SettingApplication` and `SettingUserDetailsService` are deleted, not moved: the first
is a second boot shell (two `UserDetailsService` beans would break `SecurityConfig`'s
constructor injection with `NoUniqueBeanDefinitionException` — observed in the first
merged-suite run), the second is the standalone-service verify stub the real
`UserDetailsServiceImpl` replaces.

### D2: Schema merges into `system`; ledger continues at V29

`V1__setting_schema.sql` becomes `V29__exam_setting_tables.sql` (house chain order —
V28 is the exam-setting menu seeds) in lumen-system's migration dir; DDL stays
UNQUALIFIED so Flyway's `default-schema: system` hosts it (the 2026-11-03 ruling: a
misconfigured URL must fail loudly, never migrate public). `IF NOT EXISTS` idempotence
and the no-semicolon comment discipline survive unchanged (the seed test's
statement-splitting re-execution proof re-points to V29). Alternatives: (a) keep a
`setting` schema under one service — rejected: a second schemas value makes unqualified
DDL ambiguous and contradicts one-service-one-schema (user ruling 2026-11-03); (b)
separate Flyway locations per context — rejected: two homes for the same tables.
Fresh deployments create the tables in `system`; the retired `setting` schema on the
team test DB holds ONLY fixture residue (verified 2026-11-13: 3 `ut_exb_` rows, 0 real
catalog rows) and is dropped — no data migration.

### D3: Test family stays self-contained; schema strings retarget

`SettingIntegrationTestBase` / `SettingTestEnv` / `SettingTestServerBootstrap` /
`SettingSchemaSeedTests` keep their clone-by-design shape (minimal diff, family
isolation) and change ONLY their schema references: datasource
`currentSchema=system,public`, Flyway props → `system`, sweep SQL → `system.*`,
re-execution proof → V29. The setting context's dynamic properties now carry the same
VALUES as `AuthIntegrationTestBase`'s but remain a distinct `@DynamicPropertySource`
method, so Spring creates two cached contexts (both flyway-run the same ledger; the
second pass is a no-op). Alternative: extend `AuthIntegrationTestBase` and dissolve the
clones — rejected for this change (inheritance semantics of `@DynamicPropertySource`
are version-sensitive; the merge must not risk the family), noted as follow-up debt.
Hosting-discovery subtlety surfaced by the first merged runs: `@SpringBootTest` finds
its `@SpringBootConfiguration` by walking UP from the test class's package — the
sibling package `org.birdbro.lumen.system` is never searched, so
`SettingIntegrationTestBase` pins `@SpringBootTest(classes = SystemApplication.class)`
explicitly (the retired `SettingApplication` used to be the same-package
configuration). Second trap in the same class: the suites' inline hard-delete helpers
used schema-QUALIFIED table SQL (`DELETE FROM setting.exam_body`) — qualified
references bypass the connection's search_path and kept hitting the retired schema's
old tables, silently no-op'ing the cleanup (RED run 3: guard assertions + residue
accumulation). Rule for hosted moves: grep the WHOLE test tree for both
`currentSchema=<old>` and `FROM <old>.` / `INTO <old>.` qualified forms.

### D4: Contract elements that deliberately do NOT move

URL prefix `/api/v1/setting/**` (controller mappings unchanged — frontend api files and
V28 menu seeds byte-stable), perms strings `setting:*`, `spring.application.name =
lumen-system` + port 8081, nginx contract `/api/v1/<service>/**`. The deferred
deployment change gets ONE line: retarget the `/api/v1/setting/**` upstream to 8081.

### D5: Spec surface reverts, not rewrites

service-topology's Foundation enumeration returns to the pre-exam-setting list
(department/workflow modules only) and GAINS the Hosted Domain Packages requirement
(the arrangement is now a sanctioned topology shape, not a one-off); data's
schema-per-service enumeration returns to six; exam-setting's endpoints/guards/
vocabulary requirements are untouched (behavior identical) — only the schema-host
clauses and Setting Schema Ownership are re-written. pg-init.sql drops the `setting`
schema + role block; the six-schema shared-grant lists drop `lumen_setting_role`.

## Risks / Trade-offs

- Shared-ledger coupling: exam-table DDL now rides system's migration chain — a catalog
  hotfix ships with system releases. Accepted: monorepo, one team, unified cadence;
  the re-extraction path (package + URL + perms preserved) stays cheap.
- Two cached Spring contexts (auth-shaped + setting-shaped) instead of the pre-merge
  two-JVM split: suite wall-clock roughly unchanged; correctness identical.
- `auth-schema` V1..V28 consumers and the exam tables now share one `system` schema:
  name collisions are the only structural risk — none exist (exam_*/rep_temp prefixes
  are disjoint from auth/org/dict/log tables).

## Migration Plan

Working tree first (already applied as of propose): git mv → delete the two shell
classes → V29 rename + header → test-family schema retarget → aggregator/pg-init/
.gitignore → coordination/naming/CLAUDE.md records. Then: full suite green → drop the
retired `setting` schema on the team test DB → archive syncs the three deltas. Deployed
environments (none yet — v1 pre-release): fresh installs get everything from V1..V29
in one pass.

## Open Questions

None — all rulings recorded in the coordination doc.
