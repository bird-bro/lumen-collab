# Batch 2 Brief - sign-log-query / sign-log query endpoint (tasks 2.1-2.3, TDD)

## Task text (openspec/changes/sign-log-query/tasks.md)

- 2.1 RED: `AuthSignLogQueryTests` (extends `AuthIntegrationTestBase`, per-run markers) -
  `GET /api/v1/system/logs/sign` pages `log_sign` newest-first with `account`/`uid`/`dateYm`
  filters AND-ing, and no response row carries a `token` field. Verify: suite run fails on
  the missing endpoint.
- 2.2 GREEN: additive `ILogSignService.getPage(Integer pageNum, Integer pageSize, String
  account, Integer uid, Integer dateYm)` + impl (QueryWrapper, `deleted = 0`, select excludes
  `token`, `orderByDesc(actionTime, id)` - mirrors LogInfoServiceImpl.getPage);
  `LogController` gains `@GetMapping("/sign")` with method-level
  `@PreAuthorize("hasAuthority('system:signlog:list')")` and the shared MAX_PAGE_SIZE cap.
  Existing methods untouched. Verify: 2.1 green, `./mvnw compile -q` clean.
- 2.3 Perm tests: a principal with only `system:log:list` gets forbidden on
  `/system/logs/sign`; a principal with `system:signlog:list` passes even without
  `system:log:list` (method-level override pin, design D4). Verify: suite green.

## Read first

1. `openspec/changes/sign-log-query/design.md` - D4 (method-level gate precedence), D5
   (filters, no type filter), D7 (token never leaves the service)
2. `lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/system/controller/LogController.java`
   - current state HAS class-level `@PreAuthorize("hasAuthority('system:log:list')")`; the new
     method adds its OWN method-level annotation (Spring: method-level wins for that method)
3. `framework/.../log/service/ILogInfoService.java` + `LogInfoServiceImpl.getPage` - the exact
   pattern to mirror (QueryWrapper, apply("deleted = 0"), StrUtil.isNotBlank guard, double
   orderByDesc). Mirror the javadoc style too (@author birdbro, @since 2026-11-05).
4. `framework/.../log/service/ILogSignService.java` + `LogSignServiceImpl.java` + `LogSignMapper.xml`
   - the copied methods (`insert`, `getLogSignPage(LogRequest)`) must NOT be touched
5. `lumen-system/src/test/java/org/birdbro/lumen/system/AuthLogQueryTests.java` +
   `AuthIntegrationTestBase` - fixture/login/assert style to follow (per-run unique markers,
   keyed assertions, never global counts)
6. `lumen-system/src/test/java/org/birdbro/lumen/system/AuthPermEnforcementTests.java` -
   READ ONLY. This file is an UNTRACKED leftover of the archived server-side-perm-enforcement
   change: do NOT modify it and do NOT commit it. New pins go into OUR test class.

## Implementation notes

- Controller: new method on the existing LogController (never modify the existing `list`
  method); reuse `MAX_PAGE_SIZE` + the same page/size clamping; `@ApiOperation` in Chinese
  matching house style; class javadoc gets a one-line extension mention (comment-only edit).
- Service: `getPage` on `ILogSignService` + impl; token exclusion via MyBatis-Plus
  `.select(LogSignEntity.class, field -> !"token".equals(field.getColumn()))` (verify the
  exact lambda shape against the MP version in use - it must compile on the OPS train).
- Perm fixtures for 2.1/2.3: the `system:signlog:list` authority must exist WITHOUT depending
  on V14 (that is batch 3). Follow the AuthPermEnforcementTests fixture mechanics: seed
  per-run synthetic menu rows (unique markers) with the perms string bound to the test role,
  clean up after. If that suite builds authorities differently, mirror ITS way.
- 2.3 pins both directions in our test class:
  (a) principal holding ONLY `system:log:list` → `/system/logs/sign` forbidden
  (b) principal holding ONLY `system:signlog:list` → `/system/logs/sign` OK
      (and if cheap: forbidden on `/system/logs` - proves independence both ways)

## Environment / commands

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw test -q     # team test server via .env-test.local (already configured; never print/commit its values)
./mvnw compile -q                  # L1 quick check
```

Batch 1 (V13 partition conversion) has landed before you start - the test context applies
all migrations. NEVER touch rcp-test; never hand-run DDL.

## TDD discipline (iron law)

1. Write `AuthSignLogQueryTests` (2.1 assertions) + the perm-pin tests (2.3) FIRST
2. Run → confirm RED (missing endpoint). If it passes unexpectedly, STOP and investigate -
   the test is wrong or behavior already exists
3. Implement 2.2 minimum code → run → GREEN
4. `./mvnw test -q` full suite green (AuthLogQueryTests/AuthSignLogTests/AuthOperationLogTests
   still pass - no regression)

## Report + commit

- Full report: `openspec/sdd/reports/sign-log-query-b2-report.md`
  (RED evidence: command + failing output; GREEN evidence; files; self-review; concerns)
- Commit on dev, style per `git log --oneline -8` (emoji prefix), mention sign-log-query b2.
  Commit ONLY your files: the new test class, the 3 framework/system main files. Do NOT stage
  AuthPermEnforcementTests.java or anything else lying around the worktree.
- Report back ≤15 lines: Status, one-line summary, report path, commit sha.
