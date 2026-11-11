# Batch 3 Report — audit-log-unify-api / query endpoint convergence (tasks 3.1–3.2)

> Mode note: the dispatched implementer hit 3× API timeouts during investigation
> (no code/tests/commits produced — verified by git diff); the user ruled 接管 and the
> parent session drove this batch directly (Batch-Inline execution). All evidence below
> is from the parent session's runs.

## What landed (one commit on dev)

### Task 3.1 — RED (AuthAuditLogQueryTests, NEW class, 4 tests)

RED evidence (run BEFORE the 3.2 changes, `-pl lumen-system -am`, fresh framework
classes): **4 failures, all for the RIGHT reasons** —

1. `unifiedEndpointServesBothFamiliesWithSessionExcluded` — account filter returned
   33 rows (today's endpoint ignores the param; filter only matches one family shape)
2. `integerTypeFilterAndOrderingWork` — 500 (String-typed `getPage` overload vs the
   Integer column post-V25)
3. `nonIntegerTypeIsRejectedAsAParameterError` — 500 instead of an A-class code
4. `signEndpointIsGone` — 200 instead of 404

A first RED attempt via plain `-pl lumen-system` (no `-am`) silently used the STALE
framework jar in `~/.m2` (Nov 7, predating b2) and produced misleading
BadSqlGrammar insert failures — discarded; recorded here as a footgun for future
batches: **framework changes require `-am` runs** (or `./mvnw -pl framework install`),
never the plain module path.

**A-code ruling (parent, applied):** the copied
`GlobalExceptionHandler.handleTypeMismatch` maps
`MethodArgumentTypeMismatchException` → `R.ofFail("A0421", "参数格式不匹配", …)`
(GlobalExceptionHandler.java:117–125). The GREEN assertion pins the ACTUAL copied
machinery behavior — any `A*` code passes the test (`code.startsWith("A")`), so the
contract is "A-class parameter error" without hard-coding A0421 against a copied
file. Spec delta's "rejected as a bad request parameter" satisfied; zero errors
machinery touched.

### Task 3.2 — GREEN

- `ILogInfoService` + `LogInfoServiceImpl`: ADDITIVE overload
  `getPage(Integer pageNum, Integer pageSize, Integer type, Integer uid, String
  account, Integer dateYm)` — same skeleton (deleted=0 apply, actionTime DESC +
  id DESC tie-break) PLUS:
  - `.select(LogInfoEntity.class, f -> !"session".equals(f.getColumn()))` — the D5
    credential-exclusion (proven at the WIRE: the endpoint SQL log shows the SELECT
    column list WITHOUT session).
  - `eq(account non-blank, …)` via `StrUtil.isNotBlank` (sign-page parity).
  - `eq(type != null, …)` — Integer code filter.
- The OLD `getPage(String type, …)` overload REMOVED (interface + impl) — its only
  consumer (LogController.list) rewired in the same change; recorded as the ledger #3
  subtraction half. The legacy `getLogInfoPage(LogRequest)` XML path stays untouched
  (upstream parity).
- `LogController`: rewritten as the unified audit endpoint — Integer `type` + String
  `account` params, MAX_PAGE_SIZE cap kept, `sign(...)` + the
  `@PreAuthorize("system:signlog:list")` method gate + the field-injected
  `ILogSignService` all removed; class gate stays `system:log:list`; javadoc tells
  the audit-log story (D6/D8). This also resolves the b2 interims: type-filter 500
  and /sign 500×2 are gone with the code paths that caused them.

GREEN evidence: `AuthAuditLogQueryTests` **4/4** (account filter matches BOTH
families, Total: 2; Integer filter type=11 hits exactly the seeded failure; ordering
Sign-OUT → Sign-IN → op-row (actionTime DESC); A-class rejection of
`type=not-a-number`; `/sign` → 404; session absent from every wire row).

Sweep discipline note: the test's first `@AfterEach` used `removeById` (soft-delete —
WRONG per the 2026-11-07 hard-delete ruling); fixed to marker-keyed JDBC hard DELETE
(`b3.audit.`/`b3.filter.` account prefix + `b3-%` title + `b3 %` descr, LIKE-escaped)
before the final GREEN run.

## Files

- `framework/src/main/java/org/birdbro/framework/log/service/ILogInfoService.java`
  (additive overload + javadoc; old overload removed)
- `framework/src/main/java/org/birdbro/framework/log/service/impl/LogInfoServiceImpl.java`
  (new overload with session-exclusion)
- `lumen-system/.../controller/LogController.java` (unified endpoint)
- `lumen-system/src/test/.../AuthAuditLogQueryTests.java` (NEW)
- V25 / migrations / write paths / LogSign family: UNTOUCHED (Batch 4 scope)

## Concerns

1. The stale `~/.m2` framework jar footgun (above) — worth a line in lumen-sev/CLAUDE.md
   eventually; not this batch.
2. `AuthSignLogQueryTests` (the old sign-perm dual-gate proof) now 404s on its
   endpoint — its per-run synthetic `system:signlog:list` menu seeding still works,
   so the class compiles and fails red at runtime; full re-target/deletion is
   Batch 4's (4.2), as planned.
3. Reactor failure set after this batch should be exactly the 3 log_sign direct-query
   failures (AuthSignLogTests ×2 shapes + AuthUserAgentResilienceTests) — the
   background full-reactor run confirms; verdict recorded in progress.md.
