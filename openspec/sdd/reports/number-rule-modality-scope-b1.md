# Batch 1 Report — number-rule-modality-scope (lumen-system, tasks 1.1–1.3)

## Implemented

- **1.1 V49 + entity field**: new `V49__number_rule_modality_scope.sql` (guarded
  `ADD COLUMN IF NOT EXISTS modality varchar(16)`; drop + recreate
  `uk_number_rule_active` as `(rule_type, COALESCE(modality, '')) WHERE active = 1
  AND deleted = 0`; no seeds/backfill; comment lines carry no semicolons — verified).
  `NumberRule` gains `modality` (+ javadoc refresh of the per-family×scope contract).
- **1.2 Issuer resolution chain**: `NumberIssuerService.issue(String,String)` overload
  (javadoc'd contract); `NumberIssuerServiceImpl.issue(String)` now delegates to
  `issue(ruleType, null)` (signature/annotation untouched); two-arg carries
  REQUIRES_NEW + resolution chain non-blank token → exact scoped rule → family global
  (`modality IS NULL`) → B0001 naming family + token. Existing mapper single-arg query
  gains `AND modality IS NULL` (semantic = family global) + javadoc. Controller binds
  extended `NumberIssueRequest` (new optional `modality`) and passes through.
- **1.3 Scope-aware CRUD**: `NumberRuleSaveRequest` + `SetNumberRuleActiveRequest`
  gain optional `modality` (toggle field informational — guard keys on the STORED
  row's scope, documented). Guard `activeRuleExists(ruleType, modality, excludeId)`:
  NULL-safe `isNull` vs `eq` branches; `duplicateActive` A0714 message names the scope
  (全局 or token); create normalizes a blank token to NULL (stored `''` is invisible to
  BOTH the exact lookup and the `IS NULL` global lookup — concrete failure, one line);
  update patches format params only (`patch.setModality(null)` — scope immutable,
  mirrors ruleType handling).

## TDD evidence

| Step | Command (JDK 11 export + `-pl lumen-system -am -q -Dtest='NumberRule*,NumberIssuer*' -Dsurefire.failIfNoSpecifiedTests=false`) | Result |
|:--|:--|:--|
| RED 1.1a | same, log `/tmp/b1-red1.log` | EXIT 1 — `找不到符号 方法 modality(java.lang.String)` (NumberRuleBuilder) |
| RED 1.1b | same, log `/tmp/b1-red2.log` | EXIT 1 — seed 2 errors + issuer 3 errors: `column "modality" does not exist` |
| GREEN 1.1 | same, log `/tmp/b1-green1.log` | EXIT 0 — Seed 5/5, Issuer 5/5, Api 6/6 |
| RED 1.2 | same, log `/tmp/b1-red3.log` | EXIT 1 — missing two-arg `issue` (11 call sites) |
| mid 1.2 | same, log `/tmp/b1-mid2.log` | EXIT 1 — service REDs: `expected: <NRC-0001> but was: <NRG-0001>`, scoped-only B0001s, API `NRIG-0001` from global |
| GREEN 1.2 | same, log `/tmp/b1-green2.log` | EXIT 0 — Issuer 11/11, Api 7/7, Seed 5/5 |
| RED 1.3 | same, log `/tmp/b1-red4.log` | EXIT 1 — 5 failures: `expected: <CT> but was: <null>`; `expected: <200> but was: <A0714>` ×2; A0714 msg lacks `modality=CT` ×2 |
| mid 1.3 | same, log `/tmp/b1-mid4.log` | EXIT 1 — exactly the 2 predicted: `expected: <CT> but was: <DR>` (update corruption), `expected: <1> but was: <0>` (blank stored `''`) |
| GREEN all | same, log `/tmp/b1-green3.log` | EXIT 0 — **Issuer 11/11, Api 14/14, Seed 5/5 = 30/30** |
| Reactor compile | `./mvnw compile -q`, log `/tmp/b1-reactor-compile.log` | EXIT 0 |

Planned-RED tests that pass until an earlier batch lands (recorded, not silent):
`updatePatchesParamsButKeepsStoredScope` + `blankModalityTokenStoresGlobalShape`
passed pre-DTO (unknown-JSON-field ignore), went RED mid-1.3, GREEN at 3b.

## Files changed

- NEW: `lumen-sev/lumen-system/src/main/resources/db/migration/V49__number_rule_modality_scope.sql`
- `entity/NumberRule.java`, `mapper/NumberRuleMapper.java`,
  `service/NumberIssuerService.java`, `service/NumberRuleService.java`,
  `service/impl/NumberIssuerServiceImpl.java`, `service/impl/NumberRuleServiceImpl.java`,
  `controller/NumberIssueController.java`, `dto/NumberIssueRequest.java`,
  `dto/NumberRuleSaveRequest.java`, `dto/SetNumberRuleActiveRequest.java`
- Tests: `NumberIssuerServiceTests.java` (+8 tests), `NumberRuleApiTests.java` (+8),
  `NumberRuleSeedTests.java` (+2). All fixtures `nr_`-prefixed, hard-DELETE swept
  (per-class hooks / per-test), seeded families untouched.

## Self-review findings

1. **Deviation (load-bearing): mapper scoped query is `selectScopedActiveForUpdate`,
   not a same-name overload.** The brief said "ADD overload selectActiveForUpdate(2
   params)" — MyBatis keys mapped statements by METHOD NAME; MP's MybatisConfiguration
   logged `mapper[...selectActiveForUpdate] is ignored, because it exists` and the
   scoped call silently executed the GLOBAL SQL (caught by RED mid-1.2: fallback to
   `NRG-0001`). SQL text, params, and semantics are the brief's verbatim; only the name
   differs, and the javadoc records why the name is load-bearing.
2. A0714 detail no longer starts with a duplicated `数据重复：` (the enum msg already
   prefixes it); rendered msg is now `数据重复：号码规则冲突: rule_type=X modality=Y
   已存在同范围启用规则…`. Existing tests assert code only — unaffected.
3. REQUIRES_NEW self-delegation (`issue(String)` → `issue(ruleType, null)` via `this`)
   bypasses the proxy on the inner call but the outer proxy call already opened the
   REQUIRES_NEW tx — existing `burnedSerialSurvivesCallerRollback` (untouched) stays
   green as the proof.
4. `git status` clean of foreign files: `openspec/sdd/progress.md` was already dirty at
   start (parallel session); `docs/prototypes/us-registration-redesign.html` is a
   parallel change's artifact — both untouched. Nothing outside `lumen-system` changed;
   reactor compile green (no dependents of lumen-system exist).

## Issues or concerns

- None blocking. Batch 2/3 note for the coordinator: the mapper method name above is
  the seam the Feign contract batch consumes indirectly (HTTP only) — no impact, but
  design D3's wording "mapper gains an overloaded @Select" cannot be read literally.
- retry_count: no unexpected failure occurred (all REDs were planned TDD steps);
  loop-state.yaml untouched.
