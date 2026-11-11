# Tasks — audit-log-unify-api

> Backend half of audit-log-unify (coordination: `../coordination/audit-log-unify.md`).
> TDD: red → green per task batch; migration tests run against the shared test PG.

## Batch 1 — Schema + code vocabulary

- [x] 1.1 Red: migration test asserts post-V25 state — log_sign family absent, log_info.type integer, menu 12 (审计日志, group 16, sort 3), menu 17 deleted + grants gone, dict catalog 1030 with 8 details
- [x] 1.2 Write `V25__audit_log_unify.sql` (drop log_sign family; re-type type; menu re-parent + 17 retirement; dict seed 1030) — green
- [x] 1.3 Add `common/Constants/LogTypes.java` (codes 1–5 + `codeOf(String)`; unit test: known labels map, null/blank/unknown → null)

## Batch 2 — Write path retarget

- [x] 2.1 Red: operation insert test — `@Log(type="新增")`-shaped `LogInfo` lands `type=1`; unknown label lands null type, row still written
- [x] 2.2 Rewrite `LogInfoEntity.type` → Integer + `LogInfoServiceImpl.insert` translation-after-copy (`LogTypes.codeOf` overrides the copyProperties silent-null) — green
- [x] 2.3 Red: sign insert test — `LogSign` (SUCCESS/FAIL/SIGN_OUT + token anchor) lands a `log_info` row with code passthrough and `session` = the anchor UUID
- [x] 2.4 Rewrite `LogSignServiceImpl.insert` → translate to `LogInfoEntity`/log_info — green; `AsyncFactory`, `AuthServiceImpl`, `LogoutSuccessHandlerImpl` untouched (compile verifies)

## Batch 3 — Query endpoint convergence

- [x] 3.1 Red: unified query tests — account filter matches both families; Integer type filter; ordering actionTime DESC, id DESC tie-break; `session` excluded from rows; non-integer type → A0400; `GET /logs/sign` → 404
- [x] 3.2 Add service overload `getPage(pageNum, pageSize, Integer type, uid, account, dateYm)` with select-exclusion of `session`; rewire `LogController.list` (Integer type + account param, MAX_PAGE_SIZE cap kept); remove `sign(...)` + the field-injected `ILogSignService` — green

## Batch 4 — Deletion + sweep

- [x] 4.1 Delete `LogSignEntity`, `LogSignMapper.java/.xml`, `ILogSignService`, `LogSignServiceImpl`, `LogSignMapper.xml`; keep `common` `LogSign` POJO (transport type)
- [x] 4.2 Re-target the 5 auth test classes (AuthSignLogTests, AuthSignLogQueryTests, AuthUserAgentResilienceTests, AuthSystemDataMigrationTests, AuthIntegrationTestBase) — fixtures hard-DELETE from log_info with the base prefix
- [x] 4.3 Full reactor: `./mvnw test -q` green; update coordination ledger checkmarks if any deviation emerged
