# audit-log-unify — Coordination (Cross-Stack Feature)

> Feature: merge the sign-in log (`public.log_sign`) and the operation log
> (`public.log_info`) into ONE audit log — one table (`log_info`), one page
> (审计日志), one permission — with `type` unified to Integer code values and
> dict-driven label display. Origin: user direction 2026-11-10 ("early stage,
> no business data to keep; keep one log_info table; rename the menu to
> 审计日志"), refined over three discussion rounds the same day. This feature
> EXPLICITLY SUPERSEDES two sign-log-query decisions (D4 dual-permission and
> D7 token-column isolation — replaced by the session-column equivalent).

## Why

- The two log pages split one audit trail across two menus in two different
  groups (登录日志 under 安全, 日志查询 under 系统配置) — IA friction with no
  compensating benefit now that the sensitivity argument has collapsed
  (operation-log rows already carry ip/os/browser/engine).
- Early-stage window: `log_sign` carries no data worth keeping, so the merge
  is a clean DROP + rewrite now, versus a permanent dual write path / dual
  partition family / dual permission system later.
- `log_info` already has an unwritten `session` column (varchar 128, zero
  writers in the whole backend) — the sign-event session-token anchor
  (UUID, non-JWT) lands there with NO new column.

## Shared Design (decisions 2026-11-10; user ruled the direction and approved
the recommendations below)

- **D1 — One table: `public.log_info`; `public.log_sign` is DROPPED.**
  New Flyway V2x drops the log_sign family (partitioned parent + 2026/2027/def
  children + parked residue + id sequence; V13 is never rewritten — the drop
  rolls forward). No data migration (table holds only test residue; user
  ruling: smoke-test fixtures are sweep targets, not data).
- **D2 — `log_info.type` becomes Integer; code segments: 1~5 operations,
  10/11/12 sign events.** Operations: 1=新增 2=修改 3=删除 4=授权 5=重置密码
  (new additive `common` map). Sign events reuse the copied
  `Constants.SUCCESS=10 / FAIL=11 / SIGN_OUT=12` VERBATIM — the
  `AuthServiceImpl` / `LogoutSuccessHandlerImpl` write path is byte-untouched.
  The V2x migration `ALTER COLUMN type TYPE integer` (empty table); the type
  filter on GET /system/logs becomes an Integer param.
- **D3 — Translation point is unique: `LogInfoServiceImpl.insert`.** The
  `@Log(type = "新增")` annotation String → code mapping happens ONLY there
  (the method is already on the rewrite list). The `BeanUtil.copyProperties`
  String→Integer hazard (AMENDED per b2 review, 2026-11-10: hutool 5.8.20's
  third arg is ignoreCase not ignoreError — the copy EAGERLY CONVERTS numeric
  strings and THROWS NumberFormatException on labels, it does not silently
  null) is neutralized by `CopyOptions.setIgnoreProperties("type")` excluding
  the converting field from the copy, followed by an explicit
  `entity.setType(LogTypes.codeOf(info.getType()))` — the translation is the
  only assignment path for type. POJO/annotation/aspect/21 `@Log` call sites
  all stay String and untouched.
- **D4 (supersedes sign-log-query D4) — ONE permission: `system:log:list`.**
  The sensitivity split behind the dual-permission design collapsed (both row
  families carry the same UA/IP columns). Menu 17 登录日志 and
  `system:signlog:list` retire; the unified page gates on the existing
  `system:log:list`. No role today holds signlog-only, so no compat path.
- **D5 (supersedes sign-log-query D7) — Credential-anchor isolation moves
  from the token column to the session column.** Sign-event rows land
  `token`→`session` (existing dead column, exact varchar fit for the 36-char
  UUID anchor). The unified query path select-EXCLUDES `session`; the admin
  response carries no credential material. New column rule, same guarantee.
- **D6 — One page: 审计日志.** Menu 12 日志查询 (系统配置 group) is retitled
  审计日志 and re-parented into the 安全 group (16), sort after the retired
  17's slot; route/component `system/log/index` unchanged. Single table with
  a 类别 (type) column + dict-driven Select filter; sign-family rows render
  — for method/URL. Frontend merges `logs/index.tsx` + `logs/sign.tsx`.
- **D7 — Dict catalog 审计日志类型 drives labels** (mirrors role-type-dict):
  new V2x seed catalog (code/value items for 1~5 + 10/11/12), consumed via
  the existing ungated `GET /dicts/items/{catalogName}` endpoint (commit
  925c852) + the session-cached `useDictItems` hook. Raw-code fallback on
  fetch failure (degrade, never block).
- **D8 — Backend endpoint surface: `GET /api/v1/system/logs` only.** The
  `/sign` endpoint retires with its method-level gate. The unified query
  gains an additive `account` filter (sign-page parity) alongside
  type/uid/dateYm. `GET /system/logs/sign` disappears from the API surface.

## Adaptation Ledger (framework/common OPS-lineage touches)

1. `LogSignServiceImpl.insert` REWRITTEN to translate `LogSign` →
   `LogInfoEntity` → `log_info` (recorded; `AsyncFactory.recordForSign`
   callers unchanged — signature identical).
2. `LogInfoEntity.type` String→Integer (field-type change on a copied file).
   V25's re-type carries the 18 known-label rows through a guarded CASE (deviation
   from D1's literal NULLIF — shared-DB data existed; 2026-11-10, recorded in V25's
   header).
3. `LogInfoServiceImpl.getPage` + mapper XML: select excludes `session`;
   additive `account`-filter overload; type filter re-typed Integer.
4. Copied log_sign code family DELETED (entity/mapper/service/impl + 2 XML):
   they reference a dropped table — dead code resurrects as runtime errors.
   REFINED per b4 execution (2026-11-10): the insert half SURVIVES renamed as
   `LogSignInsertService`/`LogSignInsertServiceImpl` (framework/log/service/) —
   deleting `ILogSignService` outright would sever `AsyncFactory.recordForSign`'s
   `SpringUtils.getBean` chain and break the zero-touch promise to
   `AuthServiceImpl`; the query half died with the dropped table. The
   `AsyncFactory` change is import + getBean type + comment only (call shape
   byte-identical). Upstream sync loses the old lines cleanly.
5. `LogController`: `/sign` endpoint + `ILogSignService` field retired;
   class javadoc updated. (Additive/substractive, never-modify respected.)

## Superseded Decisions

- sign-log-query D4 (dual permission system:log:list / system:signlog:list)
  → superseded by D4 above, 2026-11-10.
- sign-log-query D7 (token-column select-exclusion) → superseded by D5 above
  (session-column exclusion, same guarantee, new column), 2026-11-10.

## Change Registry

| Stack | Change | Status | Verify |
|:--|:--|:--|:--|
| backend | audit-log-unify-api | proposing | - |
| frontend | audit-log-unify-web | proposing | - |

## Gate

**SATISFIED 2026-11-10** — both changes verify PASS and are archived as
`2026-11-10-audit-log-unify-{api,web}`; specs synced (system-data: Operation Log
Recording absorbs Sign Event Recording + sign scenarios / System Log Tables log_sign
drop / Log Query Endpoint unified; web-system-console: merged audit-page requirement,
Sign-in Log Query retired; api spec `/dicts/items` → `/dicts/detail/items` path
correction recorded). Verify credentials: api L2 15/15 L3 155/155; web L2 6/6 L3
277/277 + build + real-backend smoke 4/4 (session-key absence re-verified after the
`LogInfoEntity.session` `@JsonInclude(NON_NULL)` fix, commit `0f621f1` — found BY the
web smoke, fixed backend-side). Git commits are a separate track (b1 26e9c40 →
ebadbbd). Outstanding user-side: the browser UI pass on the merged 审计日志 page
(web 3.2 asterisk). Backend first; the frontend built against the REAL backend
(no mock layer, user ruling 2026-11-05).

Feature done = ALL registered changes verify PASS → archive each.
