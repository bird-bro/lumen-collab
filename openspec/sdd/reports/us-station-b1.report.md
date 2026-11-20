# Report: us-station-registration-sev Batch 1 (tasks 1.1-1.3)

## Status

DONE_WITH_CONCERNS — all three tasks implemented, 16/16 module tests green, L1 full-reactor
compile exit 0. One task bullet (nginx location + docker compose service) is unimplementable
in-repo today; see Concerns.

## What was implemented

### Task 1.1 — module scaffold
- `lumen-sev/lumen-us` (new module): pom mirrors lumen-patient's (parent `lumen-sev`, BOM
  via the aggregator import; deps framework/common/web/validation/flyway-core/commons-pool2/
  test). lumen-core and lumen-api are DELIBERATELY absent until tasks 2.x/3.x: the kernel
  services constructor-require `PatientRegistryPort`/`NumberIssuerPort`, so putting the
  kernel on this classpath before the adapters exist would make the context unbootable.
  The `@MapperScan org.birdbro.**.mapper` wildcard already covers the kernel mappers for
  the day the dependency lands (design D1).
- `UsApplication` (`org.birdbro.lumen.us`, scan `org.birdbro`, service-root app class,
  no `@EnableFeignClients` yet - clients ride tasks 2.x/3.x).
- `UsUserDetailsService` (security pkg) — the framework `SecurityConfig` constructor slot.
- `application.yml`: port 8083, `spring.application.name=lumen-us`, flyway
  `schemas/default-schema = us`, token/swagger/xss/dispose/logging blocks (patient mirror),
  `lumen.api.patient.url` + `lumen.api.system.url` + `lumen.internal-api-key` props.
- `application-dev.yml.example` (gitignored dev-credentials template, currentSchema=us),
  `db/migration/.gitignore` (the *.sql negation exception).
- Port-table row in `openspec/coordination/init-project-skeleton.md` (`lumen-us | 8083`,
  patient-row style).
- Test harness: `UsTestEnv` / `UsTestServerBootstrap` (schema `us`) /
  `UsIntegrationTestBase` (MOCK web env per the named KernelContextBoot precedent, dynamic
  datasource/redis/flyway props, `.env-test.local` AUTH_TEST_* contract) /
  `UsContextBootTests` (flyway history lands in the us schema + Service Identity:
  name=lumen-us, port=8083).

### Task 1.2 — ledger V1 vendored kernel DDL
- `V1__exam_kernel_registration.sql` is a **byte-identical copy** of
  `lumen-core/src/main/resources/db/kernel/V1__exam_kernel_registration.sql`
  (`diff` empty — re-verified post-implementation). The kernel library is untouched.
- `UsSchemaMigrationTests` (5 tests): four aggregate tables exist in `us`; EXACT column
  shape per table (id + canonical business columns + EntityBase audit tail in order —
  pins "no department columns added"); `uk_exam_order_accession_live` partial unique
  index (WHERE deleted = 0); zero physical FKs (kernel shape); statement-split replay of
  V1 into a throwaway `us_replay_<hex>` schema recreates the same four tables (schema
  hard-dropped in finally — no residue on the shared server).

### Task 1.3 — ledger V2 ext table + V3 cross-schema menu seed
- `V2__us_registration_ext.sql`: `order_id` itself the PRIMARY KEY (1:1 anchor, no
  physical FK), `pregnancy_status varchar(16)`, `gestational_weeks integer`,
  `note varchar(255)`, full EntityBase audit set.
- `V3__us_menu_seed.sql` (patient-V2 shape, content-guarded): 登记 C page (path
  `registration`, component `us/registration/index`, perm `us:order:list`) under the US W
  node anchored by CONTENT ONLY (up_id 0 + type W + title 超声工作站 — the V22 contract
  keys, ids never assumed); F buttons 新建/登记/取消 = `us:order:create/update/cancel`
  (sorts 1/2/3); grant carry-over (page/button holders receive the W ancestor, the V32
  defensive shape); admin role-1 auto-grant (W + C + F, NOT-EXISTS guards); setval tails
  on `system.menu_info` + `system.role_menu`.
- `UsRegistrationExtTests` (4 tests): exact column set + audit tail order, order_id is
  the sole PK column, ruled column types, zero physical FKs.
- `UsMenuSeedTests` (5 tests): C page joins the W tree (title/path/component asserted);
  button family under the page; admin holds every seeded node incl. the W ancestor;
  carry-over invariant (no role holds a `us:order:*` node without the W ancestor);
  V3 re-execution is a live-schema row-count no-op.
- `docs/deployment/pg-init.sql` section 八: the commented `lumen_us_role` cross-schema
  GRANT block (mirror of the patient section-七 twin; documentation-only, decision 7) —
  V3 cannot run in production without it, and the patient precedent documents the grant
  there.

## TDD evidence

Every task ran RED before its implementation; commands run from `lumen-sev/` with
`JAVA_HOME=...jdk-11.jdk/Contents/Home` exported.

| Task | RED command | RED result | GREEN result |
|:--|:--|:--|:--|
| 1.1 | `./mvnw -pl lumen-us -am test -q` | exit 1 — 2/2 error: `SecurityConfig` ctor param 0 `UserDetailsService` not found (predicted failure) | exit 0 — `UsContextBootTests` 2/2 |
| 1.2 | `./mvnw -pl lumen-us test -q -Dtest=UsSchemaMigrationTests` | exit 1 — 3 failures + 1 error (tables/index absent) | exit 0 — 5/5 |
| 1.3 | `./mvnw -pl lumen-us test -q -Dtest='UsRegistrationExtTests,UsMenuSeedTests'` | exit 1 — ext columns absent, C page/buttons absent, V3 file absent | exit 0 — 4/4 + 5/5 |

Batch-end: `./mvnw compile -q` exit 0 (full reactor, L1); `./mvnw -pl lumen-us test -q`
exit 0 — 16/16 (2+5+4+5).

## Notable finding (V36 lesson, learned live)

The kernel file's own header line 12 contains a backticked `;` inside a comment
("splits statements on `;`"), so a naive `split(";")` truncates a comment mid-line and
PostgreSQL rejects the orphaned backtick tail (first GREEN attempt of task 1.2 failed
with `ERROR: syntax error at or near "``"`). The statement-split helpers therefore
STRIP `--` comment lines BEFORE splitting — exactly what the kernel header prescribes
("splits statements on `;` and strips `--` comment lines"). Both us suites share this
strip-then-split discipline.

## Files changed

- New: `lumen-sev/lumen-us/**` (pom, `UsApplication`, `UsUserDetailsService`,
  `application.yml`, `application-dev.yml.example`, `db/migration/.gitignore`,
  `V1__exam_kernel_registration.sql`, `V2__us_registration_ext.sql`,
  `V3__us_menu_seed.sql`, 7 test classes)
- Modified: `lumen-sev/pom.xml` (aggregator module row),
  `openspec/coordination/init-project-skeleton.md` (port row),
  `docs/deployment/pg-init.sql` (section 八)
- lumen-core / lumen-patient / lumen-system / lumen-api: **zero changes** (git-verified)

## Self-review findings

- Verbatim check: `diff` of us V1 vs kernel source is empty (byte-identical, re-verified
  after all edits).
- Completeness vs the two spec requirements: "US Service Host" — module identity +
  reserved port asserted by test, own schema + ledger asserted, kernel tables resolve in
  `us` (byte-verbatim, exact-shape tests). "Menu and Perms Seed" — all WHEN/THEN content
  asserted by `UsMenuSeedTests` (component key, perm family, admin grant).
- Ext audit columns: EntityBase set present in ruled order (tested head-to-tail).
- Ledger ordering: V1/V2/V3 never rewritten after application (shared-server history
  append-only; re-execution proofs never touch flyway_schema_history).

## Concerns

1. **nginx location + docker compose service (task 1.1 bullet) — deferred, not built.**
   Inspection found NO nginx conf and NO docker-compose file anywhere in the repo:
   `docker/` contains only a README ("nginx 由部署 change 提供"), and the api spec's
   Gateway Path Convention scenario states "deployment config arrives with the deferred
   `docker/` change". The patient scaffold precedent (the named mirror) registered only
   the port-table row — there is no lumen-patient compose service to mirror. The us
   registration therefore lives where the house puts it today: the port-table row, the
   `application.yml` identity comments (nginx `/api/v1/us/**`), and the module identity
   asserted by test. Inventing a first nginx/compose deployment here would be new
   infrastructure with multiple valid shapes — out of a scaffold batch's scope and
   explicitly deferred by the specs. If the parent wants the first nginx conf created
   now, that is a follow-up task with its own decision.
2. **`order_id` carries no physical FK** (design D2 says "PK/FK 1:1"). Read as the house
   reads references: the PK provides the 1:1 uniqueness; the reference stays logical
   (kernel DDL itself has zero physical FKs; patient tests pin the same rule). Flagged
   in case the parent reads D2 as requiring a real constraint.
3. **`openspec/changes/us-station-registration-sev/tasks.md` left unticked** — checkbox
   state + artifacts_hash are loop-managed; the implementer report is this file.
