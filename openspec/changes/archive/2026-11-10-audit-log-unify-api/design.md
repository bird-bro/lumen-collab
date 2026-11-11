# Design — audit-log-unify-api

> Coordination: `openspec/coordination/audit-log-unify.md` (D1–D8). This design
> carries the backend half; the frontend half lives in the sibling change.

## Decisions

D1 drop log_sign family (roll-forward V25, no data migration) · D2 type unified
Integer (1–5 ops, 10/11/12 sign, `Constants` reuse) · D3 single translation point
`LogInfoServiceImpl.insert` with explicit `codeOf` override after the copy · D4
single permission `system:log:list` (supersedes sign-log-query D4) · D5 credential
anchor → dead `session` column with select-exclusion (supersedes sign-log-query D7)
· D6/D8 one page one endpoint · D7 dict catalog 审计日志类型 drives labels.

## D1 — Migration V25: one roll-forward script

`V25__audit_log_unify.sql` (lumen-system) does four things in order:

1. **Drop the log_sign family.** Parent `log_sign`, children `_2026/_2027/_def`
   (dropping the parent cascades children), any `log_sign_parked` conversion
   residue (guarded `DROP TABLE IF EXISTS`), sequence `log_sign_id_seq`
   (`OWNED BY NONE` first if still owned, matching V13's dance reversed).
2. **Re-type `log_info.type`.** `ALTER TABLE public.log_info ALTER COLUMN type
   TYPE integer USING NULLIF(trim(type),'')::integer` — the USING is a no-op
   on the empty/test-residue table but makes the script honest; the column
   was varchar(255) since V3.
3. **Menu re-parent.** UPDATE menu_info id 12: title 日志查询→审计日志,
   up_id 15→16 (安全 group), sort→3 (retiring 17's slot). Soft-delete menu 17
   (`deleted = 1`) and hard-DELETE its `role_menu` grants (V22 workstation
   precedent for node retirement). `system:signlog:list` leaves the perm
   vocabulary with the menu row.
4. **Dict seed 「审计日志类型」.** Catalog id 1030, details 1030001–1030008
   (codes 1,2,3,4,5,10,11,12 → 新增/修改/删除/授权/重置密码/登录成功/登录失败/登出),
   ON CONFLICT guarded, `setval` resync past explicit ids (V8/V14 discipline).
   Consumed by-name via the EXISTING ungated `GET /dicts/items/{catalogName}`
   (role-type-dict D2 precedent — consumers are log viewers, not dict managers).

Fresh DBs and the shared test DB both converge; no repair paths.

## D2/D3 — Type unification: String at the edge, Integer at rest

**New additive file** `common/.../Constants/LogTypes.java`: `int CREATE=1,
UPDATE=2, DELETE=3, GRANT=4, RESET_PWD=5;` + `static Integer codeOf(String
label)` mapping the five `@Log` Chinese labels (null/blank → null; unknown →
null, not an exception — a future annotation label without a mapping degrades
to a null type, never breaks the write path).

**`LogInfoEntity.type`** String→Integer. Field-type change on a copied file —
adaptation ledger item (already recorded in the coordination doc as ledger #2).

**`LogInfoServiceImpl.insert`** (the rewrite is pre-authorized — it already
builds the entity by hand):
`BeanUtil.copyProperties(info, entity, true)` would convert, not skip, the
String type (b2-review amendment: hutool 5.8.20's third arg is ignoreCase,
NOT ignoreError — numeric strings eagerly convert and labels THROW
NumberFormatException) → the copy excludes the field via
`CopyOptions.setIgnoreProperties("type")` and is FOLLOWED by an explicit
`entity.setType(LogTypes.codeOf(info.getType()))` making the translation the
only assignment path. `baseMapper.insertInfo(entity)` XML: the `#{entity.type}`
binding is type-agnostic; resultMap/resultType column mapping unchanged.

**Sign rows:** `LogSignServiceImpl.insert(LogSign)` rewritten to build a
`LogInfoEntity` — title=`logSign.getMsg()` ("Sign-IN"/"Sign-OUT"), descr/type
(Integer passthrough: 10/11/12), uid/account/actionTime/ip/os/browser/engine/
mobile/app/date_ym 1:1, token→`session`. `AsyncFactory.recordForSign` and its
three call sites (`AuthServiceImpl` ×3 semantics, `LogoutSuccessHandlerImpl`)
are untouched — identical signature, identical async dispatch
(RequestContextConfig doc comment already notes it runs on the request thread).

## D5 — Session-column exclusion

`LogInfoServiceImpl.getPage`: add `.select(LogInfoEntity.class, f -> !"session".equals(f.getColumn()))` —
a NEW additive overload `getPage(pageNum, pageSize, type(Integer), uid,
account, dateYm)`; the existing `getPage(String type, ...)` overload is
retired WITH the migration in the same change (its only consumer is
LogController, updated in lockstep). `LogInfoMapper.xml`'s copied
`getLogInfoPage` raw SQL keeps its explicit column list (never selected the
session column) — untouched, still referenced by the legacy
`getLogInfoPage(LogRequest)` which stays for upstream parity.

## D8 — LogController convergence

- `list(...)`: `type` param Integer; `account` param added; delegates to the
  new service overload. Class gate `system:log:list` unchanged.
- `sign(...)`: REMOVED with the field-injected `ILogSignService` (the field
  injection was itself a never-modify workaround — both leave cleanly).
- Class javadoc: rewritten to the audit-log story.

## Deletion — the log_sign code family

`LogSignEntity`, `LogSignMapper(.java/.xml)`, `ILogSignService`,
`LogSignServiceImpl` (+ `LogSignMapper.xml`) are DELETED. They reference a
dropped table; keeping them makes any accidental DI scan/refresh a runtime
error. Ledger item #4. The `common` `LogSign` POJO STAYS — it is the
`recordForSign` transport type, still in the call path.

## Testing

TDD per Superpowers; the five auth test classes re-target:

- `AuthSignLogTests` → rows land in `log_info` with codes 10/11; token anchor
  present in DB column `session`, ABSENT from response JSON.
- `AuthSignLogQueryTests` → unified endpoint: account filter matches both
  families; type=11 filters failures; `/sign` 404s; non-integer type → A0400.
- `AuthUserAgentResilienceTests` → unchanged assertions, target table swap.
- `AuthSystemDataMigrationTests` → log_sign relations absent; log_info.type
  integer; menu 12 in group 16 titled 审计日志; menu 17 gone; dict catalog
  1030 with 8 items.
- `AuthIntegrationTestBase` → fixture cleanup (smoke-test sweep hard-delete
  ruling): delete from `log_info` with the base prefix, drop log_sign probes.

Zero-infrastructure constraint holds: unit tests mock at the seam; DB tests
run against the shared test PG (existing harness).
