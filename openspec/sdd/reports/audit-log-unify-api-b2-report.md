# Batch 2 Report — audit-log-unify-api / write path retarget (tasks 2.1–2.4)

> Status: **DONE_WITH_CONCERNS** (all four tasks red→green + the V25 idempotence fix
> applied and verified on the shared test DB; one design-premise finding — Hutool copy
> behavior — forced a one-attribute deviation inside the pre-authorized insert rewrite,
> detailed below; query-side String-type filter and log_sign-family test failures map
> to Batches 3/4 as planned).

## What was done

- **2.1 RED (operation insert test)** — new class `AuthAuditLogWriteTests` (one class
  carries both 2.1 and 2.3, two `@Test` methods): a `@Log(type="新增")`-shaped `LogInfo`
  must land `type=1` with the copy sanity (title/uid/date_ym) beside it; an unknown
  label and a blank label must land a NULL type with the row STILL written. Seeded via
  the framework `ILogInfoService` directly (synchronous — no async poll), read via plain
  JDBC keyed by per-run unique account markers, inline hard-DELETE sweep (ruling
  2026-11-07), `b2.*`-prefixed accounts inside the base sweep's existing `b2` family.
- **2.2 GREEN (operation translation)** — `LogInfoEntity.type` String→Integer (one
  attribute; Lombok combo untouched, adaptation ledger #2 as pre-recorded);
  `LogInfoServiceImpl.insert` keeps the builder + copy, now followed by the explicit
  `entity.setType(LogTypes.codeOf(info.getType()))` as the ONLY type assignment path.
- **2.3 RED (sign insert test)** — the same class's second test: `LogSign` with
  SUCCESS/FAIL/SIGN_OUT + token anchors must land a `log_info` row (code passthrough,
  title = msg, descr 1:1, `session` = the anchor UUID) and the dropped `log_sign`
  relation must STAY dropped (`to_regclass` guard).
- **2.4 GREEN (sign retarget)** — `LogSignServiceImpl.insert` rebuilt: `LogSign` →
  `LogInfoEntity` (title = msg, descr/type/uid/account/actionTime/ip/os/browser/engine/
  mobile 1:1, **token → session**, dateYm = `DateUtils.dateTimeNow("yyyyMM")` idiom,
  app = `Constants.APP_CODE`) then `SpringUtils.getBean(LogInfoMapper.class).insertInfo`
  — the same `insertInfo` XML the op side uses (one insert path). The dead
  `insertSign`/`LogSignMapper.xml` family stays in place untouched for Batch 4.
- **Reviewer Important #1 (V25 idempotence fix)** — section 2's ALTER wrapped in a DO
  block gated on `information_schema.columns.data_type = 'character varying'` (existing
  CASE copied VERBATIM inside); the header's IDEMPOTENCE paragraph rewritten to cover
  all four sections honestly. Full shared-DB history-row procedure executed (below).

## TDD evidence

**Task 2.1 + 2.3 RED** (`./mvnw -pl lumen-system -am test -q -Dtest=AuthAuditLogWriteTests
-DfailIfNoTests=false`, pre-change code):

```
Tests run: 2, Failures: 0, Errors: 2
 operationInsert…  BadSqlGrammarException: ERROR: column "type" is of type integer
                   but expression is of type character varying  (insertInfo, PG 位
置：344)
 signInsert…       BadSqlGrammarException: ERROR: relation "log_sign" does not exist
                   (insertSign — still writing the dropped table)
```

Exactly the two pre-change failure shapes the brief predicted. (The tests are written
against the POST-change API — `insert()` signature unchanged, assertions via JDBC — so
the RED is behavioral, not compile-level.)

**Task 2.2 GREEN** (entity re-typed + translation, operation test alone):

```
Tests run: 1, Failures: 0, Errors: 0 - BUILD SUCCESS
MyBatis param evidence: type=1(Integer) for 新增; type=null for unknown label and
blank label; all three rows written (Updates: 1 ×3)
```

One mid-GREEN iteration was a TEST bug (not code): my first assertion SQL mixed
`COUNT(*)` with non-aggregated columns → PG "must appear in the GROUP BY clause";
fixed by splitting the count into its own `queryForObject`. The insert path itself was
already landing correct rows at that point (parameter log shows type=1/null/null).

**Task 2.3 + 2.4 GREEN** (sign retarget, both tests):

```
Tests run: 2, Failures: 0, Errors: 0 - BUILD SUCCESS
MyBatis param evidence (three rows): title Sign-IN/Sign-OUT, type 10/11/12 (Integer
passthrough), session = the three fresh anchor UUIDs (96beb9fa-…/67772fd7-…/3f7b27bc-…)
```

## Design-premise finding (deviation, recorded)

**Hutool 5.8.20 `copyProperties(src, dest, true)` does NOT silently null a String→Integer
mismatch — it THROWS.** Design D3 (and the task text: "`LogTypes.codeOf` overrides the
copyProperties silent-null") assumed the boolean third arg suppresses conversion errors.
Probed with the exact classpath jar before writing the implementation:

- `copyProperties(src, dst, true)` with type `"新增"` → **throws** `NumberFormatException:
  Unparseable number: "新增"` (javap: the boolean overload sets `CopyOptions.setIgnoreCase`,
  NOT `setIgnoreError` — OPS copied code that never hit a type-mismatched field, so the
  difference never surfaced).
- Numeric strings copy through eagerly (`"2"` → 2) — meaning a bare keep-the-copy
  rewrite would ALSO have silently bypassed `codeOf` for numeric-label rows.

**Resolution inside the pre-authorized rewrite scope** (this is the same
copy-then-override shape the design mandates, one attribute changed to make the
override reachable): the copy becomes
`BeanUtil.copyProperties(info, entity, CopyOptions.create().setIgnoreCase(true)
.setIgnoreProperties("type"))` — `ignoreCase(true)` preserves the original boolean
idiom's exact semantics, `setIgnoreProperties("type")` excludes the one converting
field per the house rule that semantic-converting fields stay explicit — and the
`codeOf` override after it is then provably the only assignment path (probe: excluded
property stays null through the copy for known, unknown, and blank labels; the
parameter logs above show the values landing). Behavior is exactly design D3's
intent: String label in → Integer code at rest → unknown/blank degrade to NULL, row
still written. Flagged for the ledger if the reviewer wants the D3 wording amended
("silent-null" → "eager-convert-throws").

## V25 idempotence fix + shared-DB procedure (reviewer Important #1)

`lumen-sev/lumen-system/src/main/resources/db/migration/V25__audit_log_unify.sql`:

1. **Section 2 rewrite**: the bare ALTER is now inside
   `DO $retype$ BEGIN IF EXISTS (… data_type = 'character varying') THEN ALTER …
   USING CASE <b1 body VERBATIM>; END IF; END $retype$;` — on the already-integer
   column the gate skips the ALTER (b1's bare form errored there: `btrim(integer)`).
2. **Header honesty**: the IDEMPOTENCE paragraph now walks all four sections
   (DROP IF EXISTS + pg_class DO block / data_type-gated retype / guarded WHEREs +
   grant-DELETE / ON CONFLICT + setval re-converge) and states the shared-DB
   history-row procedure was executed. Nothing else in the file changed; the CASE
   body is byte-identical (copied).
3. **Shared-DB procedure** (all steps over JDBC, `.env-test.local` values never
   printed):
   - PRE state: history row 25 present (checksum `1666894264`, success), 18 log_info
     rows, `log_info.type` integer.
   - STEP 1: `DELETE FROM system.flyway_schema_history WHERE version = '25'` →
     1 row (the history table lives in the `system` schema, discovered by query —
     the brief's `public.` guess was wrong; note for future procedures).
   - STEP 2 (hand-apply of the FIXED file through a statement-splitting JDBC run,
     inside one transaction): all sections executed as TRUE NO-OPS on the converged
     shape — 18 rows preserved, type stayed integer, dict 1030 still 8 details, menu
     12 = 审计日志/16/3/live, COMMITTED with zero errors. (The hand-run's parser
     skipped the comment-led first DROP-IF-EXISTS statement — itself a no-op; the
     Flyway re-apply below ran the complete file through Flyway's own parser.)
   - STEP 3: the next test-class run triggered Flyway: history row 25 re-created
     (installed 2026-11-10 15:31:01, success), new checksum `954461499`.
   - STEP 4 (checksum proof): Flyway 7.7.3's own
     `org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(LoadableResource)`
     over the fixed file on disk → `954461499` — **MATCH**. The shared DB runs the
     fixed content; no `flyway repair`, no divergence.

## Files

| File | Change |
|:--|:--|
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/entity/LogInfoEntity.java` | `type` String→Integer (one line) |
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/impl/LogInfoServiceImpl.java` | insert: CopyOptions excluding `type` + `codeOf` override (javadoc carries the probe finding) |
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/impl/LogSignServiceImpl.java` | insert rebuilt → LogInfoEntity/log_info, token→session (dead insertSign left for Batch 4) |
| `lumen-sev/lumen-system/src/main/resources/db/migration/V25__audit_log_unify.sql` | section-2 DO-block gate + honest idempotence header (CASE verbatim) |
| `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthAuditLogWriteTests.java` | NEW — both write-path proofs (2.1/2.3) |

XML check per the brief: `LogInfoMapper.xml` `insertInfo` has NO `jdbcType=VARCHAR`
pinned on `#{entity.type}` and the resultMap column mapping is type-agnostic — the XML
needed zero changes (the same insert now serves both families). `LogSignMapper.xml` /
`LogSignMapper.java` / `LogSignEntity` / `ILogSignService` untouched (Batch 4).

**Zero-touch verification**: `AsyncFactory.java`, `AuthServiceImpl.java`,
`LogoutSuccessHandlerImpl.java` (handle/), the `common` `LogInfo`/`LogSign` POJOs,
`LogInfoMapper.java` — none appear in `git status`/`git diff`; the full reactor's
compile of every module is the compile-verify. The reactor log shows the untouched
call path WORKING end-to-end: `AsyncFactory` → `Sign-IN` rows landing in `log_info`
with type 10/11/12(Integer) and the UserLogin token in the session column position.

## Full reactor run (JDK 11, `./mvnw test -q`)

**147 tests, 3 failures + 3 errors — every one mapped to its owning batch; the
Batch-1 String write-path breakage is RESOLVED by this batch:**

| Failure | Owning batch |
|:--|:--|
| `AuthLogQueryTests` type-filter 500 (`?type=<String marker>` vs integer column — the `getPage(String,…)` overload the brief forbids touching) | Batch 3 (Integer type param + new overload) |
| `AuthSignLogQueryTests` ×2 `/sign` 500 (endpoint still wired to the dead table via `ILogSignService.getPage`) | Batch 3 (removes the endpoint) |
| `AuthSignLogTests` ×2 `BadSqlGrammar` (tests query `public.log_sign` directly — dropped) | Batch 4 (re-target the 5 auth classes) |
| `AuthUserAgentResilienceTests.recordForSignSurvivesMissingUserAgent` (`log_sign` count query) | Batch 4 |

RESOLVED from Batch 1's interim list (now green in the same run): `AuthOperationLogTests`
1/1 (the `@Log` write path lands rows again — type=1(Integer) visible in the reactor
log for the annotated user-create), `AuthSystemDataMigrationTests` 13/13 (V25 state
assertions), and the sign-row ERROR from the b1 interim map no longer hits the async
pool (sign events now land in log_info). No new failures beyond the mapped six; no
shared-server flake this run.

## Self-review

- **Completeness**: 2.1 RED + 2.2 GREEN, 2.3 RED + 2.4 GREEN, all four runs recorded;
  V25 fix applied with the full history-row procedure + checksum proof; unknown AND
  blank label edges covered (both degrade to NULL, rows written).
- **Quality**: `codeOf` is the ONLY type assignment path on the op side (copy excludes
  `type` — probe-proven, javadoc'd); session mapping explicit (`.session(sign.getToken())`);
  the sign side does Integer passthrough with NO translation (per D2 — Constants codes
  land verbatim); both paths share the single `insertInfo` XML.
- **Scope**: exactly the brief's 4 files + 1 new test class; zero-touch files
  byte-untouched (git-verified); dead `insertSign`/XML family left for Batch 4; no
  Batch 3 surface touched (`getPage(String,…)` overload, `LogController`, `/sign`
  endpoint all as-briefed).
- **Sweep discipline**: fixtures use `b2.`/`b2.`-prefixed accounts (inside the base
  class's existing `b2` account family) + inline hard-DELETE; the shared DB carried
  zero new residue after the run (base sweep log: `log_info=16` swept on class exit).
- **Ledger semantics (D3)**: single translation point holds — `LogInfoServiceImpl.insert`
  is the only place a String label becomes an Integer code; the sign side never
  translates (Integer passthrough).

## Concerns

1. **D3 wording vs hutool 5.8.20 reality** (the deviation above): the design/task text
   say the copy "silently nulls" the mismatching field; the probe proves it THROWS
   (`NumberFormatException`) and eagerly converts numeric strings. My fix preserves the
   designed SHAPE (copy-then-override, single translation point) via
   `setIgnoreProperties("type")`. Reviewer may want D3 + the coordination ledger #2
   wording amended to "eager-convert-throws — the copy excludes the converting field;
   codeOf after it is the only assignment path". Behavior-wise everything D3 intended
   holds (proven by the parameter logs + assertions).
2. **`flyway_schema_history` lives in `system`, not `public`** — the brief's procedure
   text said `flyway_schema_history` without a schema; future history-row operations
   should target `system.flyway_schema_history` (matches the per-service-schema
   convention; Flyway's `default-schema` = system).
3. **`AuthSignLogQueryTests` seeds now LAND** (its 3 inserts go through the retargeted
   `insert` and write log_info rows keyed `b2.signlog.*`), but its query assertions
   still target `/sign`/log_sign — its two 500s are Batch-3-owned; the seeded rows are
   swept by the base `b2` family rule on class exit. No action for Batch 2.
4. **Hand-apply statement-splitter caveat (one-off, no residue)**: my ad-hoc JDBC
   splitter skipped the file's first statement (the comment-led `DROP TABLE IF EXISTS
   public.log_sign CASCADE` — a no-op on the already-converged DB). The subsequent
   Flyway re-apply executed the FULL fixed file with its own parser (checksum-proof
   above), so the shared DB state is Flyway-canonical; the caveat is recorded only
   because the report promises every step of the procedure.
