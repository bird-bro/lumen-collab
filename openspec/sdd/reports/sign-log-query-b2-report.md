# Batch 2 Report - sign-log-query / sign-log query endpoint (tasks 2.1-2.3, TDD)

**Status**: DONE (with two recorded deviations, both additive and argued below).
**Date**: 2026-11-05 · **Branch**: dev · **Commit**: see bottom.

## What shipped

- 2.1 `AuthSignLogQueryTests` (new, extends `AuthIntegrationTestBase`) - three tests:
  1. `signEndpointPagesFiltersOrdersAndExcludesToken` - pages `public.log_sign` newest-first
     with `account`/`uid`/`dateYm` AND-combining; page-size clamp; blank-account guard;
     empty-page edge; and NO response row carries a `token` field.
  2. `logListPermAloneIsDeniedOnSignEndpoint` - a principal holding ONLY `system:log:list`
     reads `/system/logs` but is denied (`A0301`) on `/system/logs/sign`.
  3. `signLogPermAlonePassesSignEndpointAndNotOperationLog` - a principal holding ONLY
     `system:signlog:list` (per-run synthetic menu + real grant API, NOT V14-dependent)
     passes `/system/logs/sign` and stays denied on `/system/logs` (independence both ways).
- 2.2 additive backend: `ILogSignService.getPage(Integer, Integer, String, Integer, Integer)`
  + impl (lambda QueryWrapper, `.apply("deleted = 0")` same as the copied bare-SQL filter,
  `StrUtil.isNotBlank` account guard, `.select(LogSignEntity.class, f -> !"token".equals(f.getColumn()))`
  token exclusion, `orderByDesc(actionTime, id)` - mirrors `LogInfoServiceImpl.getPage`);
  `LogController.sign` = `@GetMapping("/sign")` with method-level
  `@PreAuthorize("hasAuthority('system:signlog:list')")` reusing the shared `MAX_PAGE_SIZE`
  cap and the same clamp arithmetic. Existing `list` method, constructor, and class
  annotations untouched (see Self-review).
- 2.3 pins live in OUR test class (never touched `AuthPermEnforcementTests.java`).

## TDD evidence

### RED (before 2.2) - command `export JAVA_HOME=...jdk-11.jdk/Contents/Home && cd lumen-sev && ./mvnw test -q`

```
[ERROR] Tests run: 74, Failures: 3, Errors: 0, Skipped: 0
[ERROR]   AuthSignLogQueryTests.signEndpointPagesFiltersOrdersAndExcludesToken:106
          the sign-log endpoint answers over HTTP 200 (envelope transport) ==> expected: <200> but was: <404>
[ERROR]   AuthSignLogQueryTests.logListPermAloneIsDeniedOnSignEndpoint:201
          a system:log:list-only principal must be denied on /system/logs/sign ==> expected: <A0301> but was: <>
[ERROR]   AuthSignLogQueryTests.signLogPermAlonePassesSignEndpointAndNotOperationLog:230
          a system:signlog:list-only principal must pass /system/logs/sign ... ==> expected: <200> but was: <>
2026-11-05 15:55:59.827  WARN ... o.s.web.servlet.PageNotFound : No mapping for GET /api/v1/system/logs/sign
```

All 3 new tests RED for exactly the pinned reason (missing endpoint - 404, no envelope
code); the other 71 tests stayed green, so the failure set is precisely the new behavior.
Nothing passed unexpectedly.

### GREEN (after 2.2) - command `./mvnw compile -q` then `./mvnw test -q`

```
./mvnw compile -q  -> exit 0 (clean)
./mvnw test -q     -> exit 0
Tests run: 74, Failures: 0, Errors: 0, Skipped: 0
AuthSignLogQueryTests   tests=3 failures=0 errors=0
AuthLogQueryTests       tests=1 failures=0   (regression: /logs unchanged)
AuthSignLogTests        tests=2 failures=0   (regression: write path unchanged)
AuthOperationLogTests   tests=1 failures=0   (regression: /logs unchanged)
AuthPermEnforcementTests tests=6 failures=0  (regression: perm matrix unchanged)
```

Generated SQL captured from the GREEN run (proof of the D7 exclusion + ordering + AND):

```
SELECT id,date_ym,msg,descr,type,uid,account,action_time,mobile,ip,os,browser,engine,session,app,deleted,create_time
FROM log_sign WHERE deleted=0 AND (account = ? AND uid = ? AND deleted = 0)
ORDER BY action_time DESC,id DESC LIMIT ?
```

- no `token` column in the projection; global `logic-delete-field: deleted` auto-appends
  `deleted=0` alongside the mirrored `.apply("deleted = 0")` (the same double-filter the
  LogInfo query produces - house pattern kept verbatim).
- The two D4 override pins passed empirically: method-level annotation REPLACES the
  class-level one for `/sign` (sign-only principal passes /sign; log-only principal denied).

## Files (all additive - `git diff --numstat`: 0 deletions in every file)

| File | Change |
|:--|:--|
| `lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/system/AuthSignLogQueryTests.java` | NEW - the 2.1 + 2.3 suite |
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/ILogSignService.java` | +`getPage` (+16 lines) |
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/service/impl/LogSignServiceImpl.java` | +`getPage` impl (+26) |
| `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/controller/LogController.java` | +`/sign` endpoint, class-javadoc paragraph, new service field (+37; `list`/constructor byte-identical) |
| `lumen-sev/framework/src/main/java/org/birdbro/framework/log/entity/LogSignEntity.java` | +`@JsonInclude(NON_NULL)` on the `token` field (+7) - DEVIATION, see below |

Not touched / not committed: `AuthPermEnforcementTests.java` (read-only reference),
`rcp-test` (never touched), `.env-test.local` (never read or printed), other agents'
worktree changes (lumen-web, loop-state, progress.md).

## Self-review

- **Existing methods**: `list`, both copied service methods (`insert`, `getLogSignPage`),
  and the controller constructor are byte-identical (numstat shows zero removed lines in
  all four main files). The class javadoc extension is comment-only, as the brief allows.
- **Controller wiring**: the new `ILogSignService` is field-injected (`@Autowired`) with a
  comment explaining why - the never-modify rule forbids widening the existing public
  constructor, and a second constructor would need the new field non-final anyway (a
  half-wired public overload would be a worse landmine). Flagged for the parent in case a
  later house decision prefers constructor consolidation.
- **Fixture discipline**: unique per-run markers everywhere (`b2.signlog.<ts>` accounts,
  `b2logonly_*`/`b2signlogq_*` usernames avoid the V5 purge patterns
  `^b2(sign|out)_[0-9]+$`); keyed assertions, never global counts; try/finally
  logical-delete cleanup of role_menu/user_role/role_info/user_info/menu_info so failed
  runs leave no live probe grant chain (strictly tidier than the reference suite).
- **2.1 runs as a scoped fixture user, not the admin**: pre-V14 NOBODY holds
  `system:signlog:list` - observed live when the first GREEN attempt failed with the
  seeded admin receiving `A0301` on `/sign` (correct enforcement, wrong fixture). Test 1
  now mints its own signlog-authorized principal through the same per-run synthetic-menu
  fixture as pin (b); this also keeps the suite green AFTER batch 3 lands V14 (the admin
  gaining the perm changes nothing for the fixture user).
- **Edge cases** (execution contract): empty-result page, `pageSize=500` clamped to the
  served `size=100` echo, blank `account` treated as unfiltered (matched the uid-only
  total, not zero rows).

## Concerns / deviations for the parent

1. **Fourth main file: `LogSignEntity.token` gained `@JsonInclude(NON_NULL)`** (beyond the
   brief's 3-file list). Required by the strict WHEN/THEN "no row carries a `token`
   field": the select exclusion leaves the field null, and plain Jackson still serializes
   `"token": null` - the assertion (`assertFalse(item.has("token"))`) fails without the
   annotation. Field-level scope: the copied XML path's non-null tokens still serialize
   (D7's "copied path unchanged" holds - and no lumen code calls `getLogSignPage` today);
   only the never-selected null disappears. If the parent prefers the looser
   `hasNonNull` reading instead, drop the annotation and the test stays green - but the
   strict reading matches the spec delta's "Responses SHALL NOT include the `token`
   column".
2. **Field injection in LogController** (see Self-review) - deliberate never-modify-rule
   compliance, commented in code.
3. **Untracked `lumen-sev/logs/` + `lumen-sev/lumen-system/logs/`** (run artifacts from
   `logging.file.name: ./logs/...`) are not gitignored and remain in the worktree (the
   `rm` cleanup was permission-gated; they are untracked and regenerable). A one-line
   `.gitignore` entry would stop the recurrence (left to the parent - shared-file edit
   outside this batch's scope).
4. `system:signlog:list` is invisible in the seeded admin's menu tree until batch 3 (V14)
   - expected ordering, no action.

## Commit

`d48bf29` - `✨ feat(system): sign-log query endpoint + method-level perm pins (sign-log-query b2)`
(5 files changed, 449 insertions(+), 0 deletions(-); report + tasks.md ticks left untracked
per the b1 precedent - the worktree's other modifications belong to other sessions.)
