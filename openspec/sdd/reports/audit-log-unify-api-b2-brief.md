# Batch 2 Brief - audit-log-unify-api / write path retarget (tasks 2.1–2.4)

## Task text (openspec/changes/audit-log-unify-api/tasks.md)

- 2.1 Red: operation insert test — `@Log(type="新增")`-shaped `LogInfo` lands
  `type=1`; unknown label lands null type, row still written
- 2.2 Rewrite `LogInfoEntity.type` → Integer + `LogInfoServiceImpl.insert`
  translation-after-copy (`LogTypes.codeOf` overrides the copyProperties silent-null)
  — green
- 2.3 Red: sign insert test — `LogSign` (SUCCESS/FAIL/SIGN_OUT + token anchor) lands
  a `log_info` row with code passthrough and `session` = the anchor UUID
- 2.4 Rewrite `LogSignServiceImpl.insert` → translate to `LogInfoEntity`/log_info —
  green; `AsyncFactory`, `AuthServiceImpl`, `LogoutSuccessHandlerImpl` untouched
  (compile verifies)

## Read first

1. `openspec/changes/audit-log-unify-api/design.md` — D2/D3 are the authoritative
   shape (type unification + single translation point).
2. `openspec/changes/audit-log-unify-api/execution-contract.md` — Batch 2 scope,
   Test Obligations, blocking red lines.
3. `openspec/sdd/reports/audit-log-unify-api-b1-report.md` — what Batch 1 landed
   (V25 applied, LogTypes exists; interim breakage already mapped).
4. `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/impl/LogInfoServiceImpl.java`
   — the insert you rewrite (lines 36–44: builder + `BeanUtil.copyProperties(info,
   entity, true)` + `baseMapper.insertInfo(entity)`).
5. `lumen-sev/framework/src/main/java/org/birdbro/framework/log/entity/LogInfoEntity.java`
   — `type` field (String, line ~50) → Integer; Lombok combo stays
   `@Data @Builder @AllArgsConstructor @NoArgsConstructor`.
6. `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/impl/LogSignServiceImpl.java`
   — the sign insert you rewrite (builder + custom SQL `baseMapper.insertSign`).
7. `lumen-sev/common/src/main/java/org/birdbro/common/core/pojo/LogInfo.java` /
   `LogSign.java` — the transport POJOs (STAY String-typed / Integer-typed
   respectively; do NOT touch).
8. `lumen-sev/framework/src/main/resources/mapper/LogInfoMapper.xml` +
   `LogSignMapper.xml` — insert XML; the `type` binding is type-agnostic but check
   the resultMap/column lists for anything type-specific.
9. `lumen-sev/framework/src/main/java/org/birdbro/framework/log/mapper/LogInfoMapper.java`
   + `LogSignMapper.java` — the mapper interfaces (insertInfo / insertSign).

## Batch 2 requirements (design D2/D3, restated as build steps)

### Task 2.2 — LogInfoServiceImpl.insert translation (the ONLY op-side translation point)

- `LogInfoEntity.type`: String → Integer. Field-type change on a copied file —
  this is recorded adaptation ledger #2 (already recorded; no doc work needed).
- `insert(LogInfo info)` body: keep the builder + `BeanUtil.copyProperties(info,
  entity, true)` — but the copy MUST be followed by an explicit
  `entity.setType(LogTypes.codeOf(info.getType()))` making the translation the
  only assignment path (Hutool String→Integer copy silently nulls; the override
  is the documented fix, design D3). dateYm/app handling unchanged.
- The XML `insertInfo` stays as-is UNLESS it breaks (check the `#{entity.type}`
  binding and any resultMap with a `type` column mapping — jdbc type inference
  handles Integer fine; if the XML has `jdbcType=VARCHAR` pinned on type, adjust
  that one attribute and say so in the report).
- Existing `getPage(String type, ...)` overload and the legacy
  `getLogInfoPage(LogRequest)` stay UNTOUCHED in this batch (they die in Batch 3
  or stay for upstream parity — do not preempt).

### Task 2.4 — LogSignServiceImpl.insert retarget

- Rewrite the method body: build a `LogInfoEntity` from the `LogSign` transport —
  title=`logSign.getMsg()` ("Sign-IN"/"Sign-OUT"), descr 1:1,
  type=`logSign.getType()` PASSTHROUGH (already Integer: Constants.SUCCESS/FAIL/
  SIGN_OUT — no translation), uid/account/actionTime/ip/os/browser/engine/mobile/
  app/dateYm 1:1, **token → `session`** (the anchor UUID lands in the existing
  dead column). dateYm = current yyyymm (same `DateUtils.dateTimeNow("yyyyMM")`
  idiom as the info side), app = Constants.APP_CODE.
- Then `baseMapper.insertInfo(entity)` (the info-side XML insert) — the sign-side
  custom insert (`insertSign` + LogSignMapper.xml) becomes DEAD in this batch.
  Do NOT delete the LogSign family yet — Batch 4 owns deletion. Leave
  `LogSignMapper.insertSign`/XML in place untouched (they reference a dropped
  table; they are unreachable dead code until Batch 4 removes them — compile
  still passes because nothing else calls insertSign).
- `AsyncFactory.recordForSign` / `AuthServiceImpl` / `LogoutSuccessHandlerImpl`:
  ZERO changes (signature identical). Verify by compile — any edit there is a
  scope violation.

### Reviewer Important #1 — V25 idempotence fix (rides with this batch's commit)

In `V25__audit_log_unify.sql` section 2, wrap the ALTER in a DO block gated on the
column's current data_type so a hand re-run on the shared DB is a true no-op:

```sql
DO $retype$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'log_info'
                 AND column_name = 'type' AND data_type = 'character varying') THEN
        ALTER TABLE public.log_info ALTER COLUMN type TYPE integer USING <existing CASE>;
    END IF;
END
$retype$;
```

- Keep the existing CASE expression verbatim inside (do not retype it — copy it).
- Update the header's idempotence claim to cover ALL four sections honestly.
- DO NOT change anything else in V25 (checksum discipline: this is a NEW migration
  being amended BEFORE any other database has applied it — the shared test DB has
  applied V25 already, so this edit changes its checksum. Handle it the house way:
  the Flyway repair precedent from role-type-dict (delete history row + re-run) —
  BUT the recorded ruling is "never rewrite an applied migration; delete history
  row + re-run for idempotent data migrations". Since V25's content must change,
  follow the b1-report/verify notes' house procedure: delete the flyway_schema_history
  row for 25 on the shared test DB, apply the fixed V25, confirm 25→25 with the
  DO block no-op path, and record the operation in the report. If psql/DDL access
  to the history row is not available, report BLOCKED with specifics — do NOT
  silently leave the checksum broken.)

## TDD discipline

- 2.1 RED first: a test asserting `insert()` lands type=1 for a `LogInfo` with
  type="新增" and type=null (row still written) for an unknown label — against the
  live shared DB (follow AuthSystemDataMigrationTests' fixture style; sweep family
  prefix per the base class's hard-delete discipline). Expect FAIL (String entity
  → copyProperties leaves type null / compile-level type mismatch resolves after
  2.2 — write the test to the POST-change API so it fails on behavior, not compile).
  Practical route: write the test calling `insert()` + reading the row via a
  mapper/service query — it fails pre-change because the row's type column holds
  NULL (copyProperties silent-null on the current String field would actually
  throw on the DB write... run it and record what happens; the RED is the
  failing evidence either way).
- 2.2 → GREEN (operation path).
- 2.3 RED: sign insert test — SUCCESS/FAIL/SIGN_OUT `LogSign` → log_info rows
  with type passthrough + session = anchor UUID + no row in any log_sign relation
  (guard: to_regclass). Expect FAIL (current impl writes log_sign).
- 2.4 → GREEN. Record all four runs.

## Build/test commands

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q
```

- Shared test DB is LIVE (.env-test.local; never print values). Sweep discipline:
  hard-DELETE log_info fixtures by the base-class prefix family.
- Known interim breakage from Batch 1 (String write paths) resolves in THIS batch;
  Batch 3/4 breakage (log_sign query tests, /sign 500) remains expected — map any
  mid-flight failures to their owning batch in the report (b1-report protocol).

## Report + commit

- Report to `openspec/sdd/reports/audit-log-unify-api-b2-report.md` (what/TDD
  evidence red+green per task/files/V25-fix procedure + DB verification/self-review/
  concerns).
- ONE commit on dev, style per `git log --oneline -8` (emoji prefix), mention
  audit-log-unify-api b2 + the V25 idempotence fix.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha, concerns.
