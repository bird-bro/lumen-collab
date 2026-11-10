# Batch 1 Report — audit-log-unify-api / schema + code vocabulary (tasks 1.1–1.3)

> Status: **DONE_WITH_CONCERNS** (all three tasks red→green + layout verified live;
> concerns are DB-state findings and shared-DB flake/residue readings, detailed below —
> none block Batch 2).

## What was done

- **1.1 RED (migration test)** — 6 new `@Test` methods in
  `AuthSystemDataMigrationTests` asserting the post-V25 state: log_sign family absent
  (5 tables/partitions via information_schema + the sequence via `pg_class` relkind S),
  `log_info.type` integer, menu 12 = 审计日志/16/3/live, menu 17 soft-deleted with 0
  live role_menu grants, dict catalog 1030 named 审计日志类型 with the 8 deterministic
  details (codes 1/2/3/4/5/10/11/12, sort 1–8, all live), and both dict sequences
  resynced past the seeded ids. Plus the minimal shape re-targets the post-V25 gate
  forces (see "Interim breakage" below).
- **1.2 GREEN (V25)** — `V25__audit_log_unify.sql`, four ordered sections per
  design D1/D2/D7: (1) drop log_sign family (parent CASCADE, parked residue, sequence
  via a pg_class-guarded DO block covering both OWNED-BY and orphaned states);
  (2) re-type `log_info.type` integer; (3) menu convergence (12 retitle/re-parent/sort
  with V5 §3-style guarded WHERE; 17's grants hard-DELETE V22-precedent then
  soft-delete guarded `deleted = 0`); (4) dict seed 1030 + 1030001–1030008 with
  ON CONFLICT (id) DO NOTHING and the `setval` resync tail V23 shipped without.
  Header comment carries the full WHY + idempotence + deviation notes in the
  V13/V23 English style.
- **1.3 RED→GREEN (LogTypes)** — `common/Constants/LogTypes.java` (codes 1–5 as
  `public static final int`, `codeOf(String)` mapping the five `@Log` labels,
  null/blank/unknown → null via `StrUtil.isBlank`, never throws; private ctor; sign
  codes deliberately NOT duplicated — the unit test pins `Constants.SUCCESS/FAIL/
  SIGN_OUT` = 10/11/12 verbatim instead). Unit test `LogTypesTest` (plain JUnit,
  zero infrastructure) — 4 tests, all green.

## TDD evidence

**Task 1.1 RED** (`./mvnw -pl lumen-system -am test -q -Dtest=AuthSystemDataMigrationTests`,
V25 absent, schema at 24):

```
Tests run: 13, Failures: 5, Errors: 1
 v25DropsTheLogSignFamily        expected: <0> but was: <1>          (log_sign exists)
 v25RetypesLogInfoTypeToInteger  expected: <integer> but was: <character varying>
 v25ConvergesMenu12ToTheAuditPage expected: <审计日志> but was: <日志查询>
 v25RetiresMenu17WithItsGrants   expected: <1> but was: <0>          (deleted flag)
 v25SeedsAuditLogTypeDictCatalog EmptyResultDataAccessException      (catalog 1030 absent)
 v25ResyncsDictSequencesPastTheSeededIds expected: <true> but was: <false>
(The 7 pre-existing tests in the class stayed green.)
```

**Task 1.2 GREEN** (same command after V25):

```
Successfully validated 24 migrations ... Current version of schema "system": 25
Tests run: 14, Failures: 0, Errors: 0
```

**Task 1.3 RED** (`./mvnw -pl common test -q -Dtest=LogTypesTest`, `codeOf` stubbed
`return null` — full file already existed, so the RED is the mapped-labels case on
the stub; constants/null/blank/unknown cases are trivially satisfied by the stub and
green):

```
Tests run: 4, Failures: 1
 knownLabelsMapToTheirCodes expected: <1> but was: <null>
```

**Task 1.3 GREEN** (implementation restored): `Tests run: 4, Failures: 0, Errors: 0`
(quiet run, no failure output).

First RED attempt also surfaced the missing test infra in `common` — no test
dependency existed (`程序包org.junit.jupiter.api不存在`), fixed by the minimal
test-scoped `junit-jupiter` (version from the Boot train via lumen-bom's
spring-boot-dependencies import; no version pinned — BOM edits need a proposal).

## Files

| File | Change |
|:--|:--|
| `lumen-sev/lumen-system/src/main/resources/db/migration/V25__audit_log_unify.sql` | NEW — the migration (56 lines of statements + header) |
| `lumen-sev/common/src/main/java/org/birdbro/common/Constants/LogTypes.java` | NEW — code vocabulary + codeOf |
| `lumen-sev/common/src/test/java/org/birdbro/common/Constants/LogTypesTest.java` | NEW — unit proofs (first test under `common`) |
| `lumen-sev/common/pom.xml` | junit-jupiter test-scope dep (minimal, unpinned) |
| `lumen-sev/lumen-system/src/test/.../AuthSystemDataMigrationTests.java` | 6 new V25 tests + log_sign shape assertions removed (V25 gate) |
| `lumen-sev/lumen-system/src/test/.../AuthIntegrationTestBase.java` | sweep's `log_sign` DELETE guarded `to_regclass` (V25 gate) |

V3–V24 untouched (verified: `git status` shows no modification to any applied
migration). No entity/service/controller/prod-code changes — the write path
(`LogInfoServiceImpl.insert` still copies the String `type` into the Integer column)
is now BROKEN-BY-DESIGN until Batch 2 lands; see below.

## Post-V25 layout verification (psql against the shared test DB, 2026-11-10)

```
1. to_regclass log_sign / parked / seq          → NULL / NULL / NULL        ✓
2. log_info.type data_type                      → integer                   ✓
3. menu 12 / 17                                 → 审计日志,16,3,0 / 登录日志,16,3,deleted=1 ✓
4. live role_menu grants on menu 17             → 0                         ✓
5. dict catalog 1030                            → 1030 / 审计日志类型 / 8    ✓
6. dict sequences                               → catal ≥ 1030, detail ≥ 1030008 ✓
7. carried log_info.type values                 → 1×3, 2×11, 3×1, 4×3       ✓ (18 rows re-homed)
8. flyway history tail                          → 25 / audit log unify / success ✓
```

## Interim breakage in the full reactor (expected, batch-plan-consistent)

Two full-reactor `./mvnw test -q` runs after V25 (145 tests each):

- Run A: 9 failing/erroring — every one mapped to the interim states below.
- Run B: 12 failing/erroring — run A's set (minus the workstation one, self-healed)
  plus 4 shared-server flake failures (3× admin-login 500 in AuthAuditIdentityTests /
  AuthOrgCrudTests / AuthRoleEnableToggleTests and 1× DataAccessResourceFailure in
  AuthButtonSeedTests). **All four pass standalone immediately after** (4/4, 5/5, 3/3,
  3/3) — shared-test-server contention flakiness, not V25-caused (V25 touches no
  login-path row; the failures appear in neither run consistently).

The stable, mapped interim failures (present in both runs):

- 4 errors = tests querying the dropped `public.log_sign` (`relation does not exist`):
  AuthSignLogTests ×2, AuthSignLogQueryTests.signEndpoint…, AuthUserAgentResilienceTests.
  recordForSign… — Batch 4 re-targets these classes (execution-contract batch plan);
  the base sweep's log_sign DELETE is already guarded so suites still run to completion.
- 2 errors + 2 failures = the String `type` write path now that the column is integer
  (AuthLogQueryTests seeding via `logInfoService.insert(LogInfo.type="b2.typeA…")` →
  PG "column type is of type integer but expression is of type character varying";
  AuthOperationLogTests / AuthUserAgentResilienceTests @Log rows never land) — Batch 2
  (2.2) re-types the entity + adds the `codeOf` translation; until then `@Log` writes
  degrade to a swallowed async error (the aspect catches; the async pool logs
  `relation "log_sign" does not exist` for sign events), queries on pre-existing
  integer rows work.
- 1 failure = `AuthSignLogQueryTests.signLogPermAlonePassesSignEndpoint…` 500 (the
  `/sign` endpoint's `ILogSignService` insert hits the dropped table) — Batch 3
  removes the endpoint.

## Concerns

1. **DB-state surprise (brief assumption contradicted, resolved conservatively)** — the
   brief said "existing rows in log_info.type are NULL/empty (suite residue is
   swept)". The shared test DB actually carried 18 rows with non-empty labels:
   新增/修改/删除/授权 (real admin-operation rows from 2026-11-06/09 console usage,
   plus suite residue that used the same four labels). **No value outside the known
   five-label vocabulary** (重置密码 absent, nothing unknown), so this is NOT the
   escalation-trigger state; the labels were within the designed vocabulary and the
   guarded CASE re-homed them to their codes (equivalent to `LogTypes.codeOf` —
   probe-era numeric strings also carry through). The design's literal
   `NULLIF(trim(type),'')::integer` was probed and ERRORS on label strings
   (PG "invalid input syntax for type integer: 新增"), so the migration deviates to
   the guarded CASE — recorded in the file header; a strict reading of design D1's
   "no data migration" would have failed the shared-DB gate instead. Fresh/empty-table
   behavior is unchanged (CASE is a no-op there).
2. **PG 14 + partitioned-parent ALTER TYPE verified by probe before writing** — temp
   tables (session-scoped, zero residue): the re-type works on a partitioned parent on
   PostgreSQL 14.11, and the partitioned child indexes keep their names
   (AuthSystemDataMigrationTests's existing index-name assertions unaffected).
3. **Pre-existing shared-DB residue surfaced by reactor run A, NOT caused by V25** —
   `AuthWorkstationSeedTests.seedStatementsReExecuteAsARowCountNoOp` failed once
   ("re-executed seed must not add grants expected 2 was 3"): role 1 entered the run
   with a dead 放射工作站 grant (rows 2864/5171 deleted=1 from 2026-11-06/09 runs;
   only 超声/内镜 held live grants), and V22's self-healing re-execution re-granted
   放射工作站 (row 5566, created mid-run 12:24:46). Forensics: V25's only role_menu
   DELETE filters `perms = 'system:signlog:list'` (menu 17) — no W-node rows touched.
   Re-run of the class alone: **4/4 green** (the re-execution's self-heal made the
   state converge; run B passed it outright). Left as-is deliberately: the row is
   real-seed data correctly re-granted, not test residue.
4. **Shared-server flakiness under parallel load (not V25-caused)** — run B produced
   4 extra failures (3× admin-login 500 + 1× DataAccessResourceFailure) that all pass
   standalone; see the interim-breakage section. Worth knowing for Batches 2–4 gate
   reads: a red reactor run against this shared server needs the standalone re-run
   before being read as a regression.
5. **Batch-2 dependency note for the next batch** — until 2.2 lands, every `@Log`
   annotated write DEGRADES (async error swallowed by the aspect's catch; the endpoint
   still answers) and every login event's sign row errors in the async pool (logged,
   never thrown into the request). The shared test DB's real console usage between now
   and Batch 2 will silently not log. Same-day execution of Batch 2 recommended.

## Self-review

- Completeness: 1.1 red evidence + 1.2 green + 1.3 red/green recorded; all six V25
  state assertions from the task text covered (plus the sequence-resync one, which
  hardens V23's known gap — flagged as an addition beyond the task text).
- Idempotence: every statement guarded (IF EXISTS / DO block / ON CONFLICT /
  guarded WHERE); a re-run is a no-op on the converged shape; the whole file relies
  on Flyway's single-transaction execution per migration (no explicit
  BEGIN/COMMIT, matching V13's discipline note).
- Scope: zero files outside the brief's list (the `common` pom test dep and the two
  test-class touch-ups are forced by the green gate, each documented above).
- Style: SQL header English + WHY + IDEMPOTENCE notes (V13/V23 style); LogTypes
  follows the Constants sibling (Chinese one-line field javadocs, `public final
  static` ordering per RoleConstants — using `public static final int` per the
  brief's signature; `@author birdbro` present).
