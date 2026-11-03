# Batch 2 Review — system-data (tasks 3.1–3.4, 4.1–4.5)

Reviewer: code-reviewer agent · 2026-11-03
Commit reviewed: `33167c3` (base `cd703cf`; 24 files, +1556/−26 — all lumen-sev, no openspec/docs edits).
Independent suite re-run: **MVN_EXIT=0, lumen-auth surefire 50 tests / 0 failures / 0 errors / 0 skipped**
(15 classes: 42 existing + 8 new — matches the report exactly; a naive directory sum reads 51 only because of a stale
`org.birdbro.lumen.auth.TempFilterProbeTests.txt` at 14:59, probe residue in gitignored `target/` — the probe `.java`
is confirmed removed from the tree).

## Verdict: **Approved**

All claimed tasks are implemented to spec; the four batch-1 handoffs are verifiably handled; the unplanned
RequestContextConfig fix is additive-only, correctly conditioned, and load-bearing (the sign-out test fails without it).
One Important-quality discovery surfaced during review is documentation-level only (root cause of the logout NPE —
see A-2), so no code change is required; 5 Minor items are carry-forward/hardening notes.

---

## Part 1 — Spec compliance

### Task 3.1 dict endpoints — PASS
- `DictCatalController` `/api/v1/auth/dicts/catal`: POST (returns new id), PUT /{id}, DELETE /{id} (logical), GET /list
  bound to the copied `CatalQuery` (pageNum/pageSize/name/cls) — brief-conformant.
- `DictDetailController` `/api/v1/auth/dicts/detail`: POST, PUT /{id}, DELETE /{id}, GET /list?caCode=.
- Duplicate rules: catal code A0714 over live rows only (`@TableLogic` via `EntityBase` — partial unique index
  `uk_dict_catal_code WHERE deleted=0` backstop consistent); detail (caCode, value) A0714 with blank-value skip
  mirroring NULL-distinct index semantics; orphan caId A0400; unknown update target B0031 (RoleServiceImpl precedent).
- **ca_code is server-resolved**: `create`/`update` derive it from the live catalog after the copy — the test posts
  `caCode:"WRONG.<ts>"` and asserts the column holds the real code. Spec scenario 3 double-anchor guaranteed by
  construction.
- **Handoff 3 verified**: `CatalCreate.enable` is `Byte` (pojo read), `DetailCreate.enable` already `Integer`; explicit
  `intValue()` conversion in both create and update paths with javadoc.

### Task 3.2 log query endpoint — PASS
- `LogController` GET `/api/v1/auth/logs`, page/pageSize default 1/20, optional type/uid/dateYm AND-combined,
  `actionTime DESC` + `id DESC` tiebreak; `apply("deleted = 0")` matches the copied XML's raw filter byte-for-byte
  (`LogInfoMapper.xml:48`).
- **Handoff 2 verified**: `IDictDetailService.listByCaCode(String)` + impl are new methods; existing `getPage(DetailQuery)`
  (caId-based) untouched — overloading/additive discipline respected. `ILogInfoService.getPage(...)` likewise additive
  vs `getLogInfoPage(LogRequest)` (one-line-unchanged verified).

### Task 3.3 @Log wiring — PASS (19/19)
Every admin write endpoint annotated: orgs 3, org-deps 3, users 3 (create/resetPassword/setEnable — the controller has
no user delete in v1), roles 4 (incl. assignMenus = 授权), dict catal 3, dict detail 3. `AuthController.login` excluded
per brief; `LogController`/`MenuController` are read-only. Annotation signature matches the copied
`common/annotation/Log` (title/type/desc); type strings align with the @ApiOperation display strings.

### Task 3.4 sign wiring — PASS
- Success: `recordForSign(account, SUCCESS, "Sign-IN", …, loginUser)` after `createToken` — uid + the UUID session
  anchor (not the JWT) land in the row (test asserts token non-null).
- Failure: try/catch(BusinessException) → record → **rethrow original**; `AuthLoginTests` 5/5 green proves the
  A0201/A0202/A0210 envelopes are untouched. Existing-account failures pass a minimal `UserLogin(uid)`; unknown-account
  passes no varargs — verified tolerated (`AsyncFactory.java:60-64` creates an empty `UserLogin`, nullable columns).
- **Handoff 1 verified**: `LogSignMapper.xml` `#{req}` → `#{req.account}` is correct against the interface signature
  `getLogSignPage(IPage, @Param("req") LogRequest)`.

### Tasks 4.1–4.5 tests — PASS
4.1 roundtrip asserts `public.dict_catal` placement (qualified read + no auth-schema shadow table + setval past 1/2);
4.2 covers scenarios 1–4 incl. dup-code data-null, wrong-caCode rejection, sort order, delete-shrink, code reuse after
logical delete; 4.3 seeds 3 controlled rows via the real `ILogInfoService.insert` and asserts pagination/uid-exact/type/
dateYm/empty-page edge; 4.4 polls `public.log_info` for uid=1/title/method/url/param and record=0; 4.5 proves type
10/11/12 rows keyed to a fresh per-run user. Deviation A (4.1 has no RED — batch 1 already delivered the data layer) is
sound; the information_schema four-table assertions were batch 1's and are not duplicated.

### Nothing extra — VERIFIED
No dict_city/DictMenu/DictCity anywhere in the commit; no endpoints beyond brief; V1/V2/V3 migrations untouched;
`toProfile` in `AuthServiceImpl` is byte-identical between `cd703cf` and `33167c3` (deviation C correctly excluded the
parallel-session hunk from the commit).

## Part 2 — Code quality
- **Envelope discipline**: no void returns — create→Integer id, update/delete→Boolean.TRUE, queries→IPage/List; the
  "204 trap" is documented in both dict controllers' javadocs; tests assert `code:"200"` + data payloads.
- **Swagger**: `@Api(tags=…)` on all three new controllers + `@ApiOperation` on every endpoint (中文 display strings) —
  the spec will not go empty.
- **Audit bridge**: `create_by = 1` asserted against the real `public.dict_catal` row (MetaObjectConfig fill; identity
  deliberately not set in the manage services).
- **Caching interplay**: the lumen dict write path does not evict `CACHE_DICT`; only the copied `modify()` carries a
  (dormant) `@CacheEvict` — nothing populates that cache in-repo today, so no stale-read risk yet (Minor 4).
- **Polling discipline**: shared `AuthAsyncPoll` (20×250 ms); zero bare-sleep assertions; all async assertions keyed to
  per-run unique values (shared long-lived DB hygiene incl. logical-delete cleanup).

## Part 3 — Tests
- Independent re-run: `export JAVA_HOME=… && cd lumen-sev && ./mvnw test -q` → **MVN_EXIT=0**, 50/0/0 (see header).
- Tests exercise REAL behavior: JdbcTemplate assertions against `public.log_info` / `public.log_sign` /
  `public.dict_catal` / `public.dict_detail` — no mocks anywhere in the async paths.
- **Adaptations (7)+(8) proven end-to-end**: the sign-out test posts `/api/sign/out` and asserts HTTP 200 envelope +
  a `type=12` row. RED showed the 500 (unbound RequestContextHolder at LogoutFilter); GREEN shows the row. Without the
  context filter this endpoint is 100% dead — the assertion is a genuine regression guard for (8).
- RED evidence accepted as reported (8 failures decompose 1:1 with the 8 new tests; not re-runnable post-merge).

## Context claims — verdicts
**A-1. RequestContextConfig — VERIFIED, additive-only.** `AsyncFactory.recordForSign` calls
`ServletUtils.getRequest()` synchronously at task-creation time (`AsyncFactory.java:42`) → `getRequestAttributes()`
returns null → NPE at `ServletUtils.java:50`, on the request thread, inside `LogoutSuccessHandlerImpl` invoked from
`LogoutFilter` — before any DispatcherServlet binding. The fix is one new file, zero copied-line changes; its
conditions are **byte-identical** to Boot's own auto-config (`javap` on spring-boot-autoconfigure 2.5.15:
`@Bean @ConditionalOnMissingBean({RequestContextListener, RequestContextFilter})
@ConditionalOnMissingFilterBean(RequestContextFilter)` on the adapter's `requestContextFilter()`), so exactly one
filter exists in every environment (GREEN stack shows a single `RequestContextFilter` ahead of
`springSecurityFilterChain`, order −105 vs security −100).
**A-2. Root cause depth (reviewer addition): the copied `framework/config/SwaggerConfig.java:31` carries
`@EnableWebMvc`, which backs off `WebMvcAutoConfiguration` ENTIRELY — including the adapter's `requestContextFilter()`
bean (auto-configured since Boot 2.4, gh-23902). That is why lumen had no context filter while a plain Boot 2.5 app
does; the new config restores exactly the bean `@EnableWebMvc` silently removed. Recommend appending this sentence to
the adaptation (8) ledger entry (Minor 5).**
**B. SIGN constants — VERIFIED coherent.** `Constants.java:30-40`: SUCCESS=10（通用成功标识）, FAIL=11（通用失败标识）,
SIGN_OUT=12; grep confirms no SIGN_IN exists anywhere. 10/11/12 as the sign trio needs no common modification; tests
lock the mapping.
**C. `#{req.account}` fix — correct** (param name checked against the mapper interface).
**D. ~/.m2 install discipline** — claimed followed; my re-run builds framework in-reactor so it cannot regress here.

## Additional findings (reviewer)
- **Minor 1** Non-browser User-Agent drops audit rows silently: `AsyncFactory.java:77`
  (`userAgent.getBrowserVersion().getVersion()`) NPEs on the AsyncManager pool for unparseable UAs, and LogAspect's
  try/catch swallows its own (`异常信息:null`, 39 occurrences in my run). Consequence: sign/@Log rows never land for
  curl/script/service clients — spec scenario 10's WHEN is unconditioned. Copied-machinery defect surfaced (not
  caused) by this batch; disclosed as 关注项 3; production browser traffic unaffected; tests carry Chrome UA
  deliberately. Recommend a future recorded adaptation: additive null-safe engine resolution or a new
  `recordForSign` overload — do not hot-edit the copied file.
- **Minor 2** `/api/v1/auth/logs` pageSize has a floor but no cap (`Math.max(1,·)` only) — a huge LIMIT over the
  shared append-only table is possible. Self-noted; align to the `PageSizeConfig` cap in a later pass.
- **Minor 3** The widened try in `login` now spans createToken + success sign + response assembly: a future A-class
  throw from the tail would double-record (SUCCESS + FAIL rows) and orphan the Redis session. No current thrower
  (toProfile/menuTree/permissionSet are plain selects) — theoretical, noted for awareness.
- **Minor 4** Dormant cache eviction: lumen dict enable/disable/delete bypasses the copied `@CacheEvict(CACHE_DICT)`
  habit; harmless until a consumer populates that cache — carry-over note for the first dict cache consumer.
- **Minor 5** Adaptation (8) ledger: add the `@EnableWebMvc`/SwaggerConfig root cause (A-2) so future readers know why
  Boot's own auto-config did not cover this.
- **Housekeeping** Stale `lumen-auth/target/surefire-reports/org.birdbro.lumen.auth.TempFilterProbeTests.txt`
  (gitignored probe residue) — delete at will; `openspec/changes/system-data/tasks.md` checkboxes (1.x–4.x) still
  unticked despite committed work — coordinator to tick at archive.
- **Out-of-scope observation** Test file names ≠ class names (`AuthOrgCrudTests.java` contains `AuthUnitCrudTests`;
  `AuthOrgDepTests.java` contains `AuthDepartmentTests`) — pre-dates this batch; surefire keys by class, cosmetic.
- **Carry-over (batch 1, unchanged)** No index on `dict_detail.ca_code`; `listByCaCode` now actively filters on it.
  Tables are tiny; V4 if ever — do not touch frozen V3.

## Findings summary
Critical: 0 · Important: 0 · Minor: 5 (+2 housekeeping, +2 observations)

**Verdict: Approved**

Parallel-session residue (entities Lombok/BeanUtil refactor, application.yml, CLAUDE.md, service impls,
`AuthDataLayerTests`, openspec loop-state) was left untouched and unstaged throughout this review; the only file this
review created is the present one.
