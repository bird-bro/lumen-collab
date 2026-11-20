# Batch 1 Brief — lumen-system: scoped rule model + issuer resolution (tasks 1.1–1.3)

Change: number-rule-modality-scope. You implement ONLY batch 1, ONLY in `lumen-sev/lumen-system`
(+ reactor compile sanity). Do not touch lumen-web, other sev modules, or open working-tree
files of the parallel change `system-drawer-status-switch-web`.

## Read first (targeted, in this order)

1. `openspec/changes/number-rule-modality-scope/specs/number-rule/spec.md` (the delta — your
   WHEN/THEN acceptance), `design.md` decisions D1–D3 + D8 only.
2. Existing code: `lumen-system/src/main/java/org/birdbro/lumen/manage/` — `entity/NumberRule.java`,
   `mapper/NumberRuleMapper.java`, `service/impl/NumberRuleServiceImpl.java`,
   `service/impl/NumberIssuerServiceImpl.java`, `controller/NumberRuleController.java`,
   `controller/NumberIssueController.java`, `dto/NumberRuleSaveRequest.java`,
   `dto/SetNumberRuleActiveRequest.java`, `dto/NumberIssueRequest.java`.
3. Test precedents (extend these, follow their fixture/sweep conventions EXACTLY):
   `src/test/java/org/birdbro/lumen/manage/NumberIssuerServiceTests.java`,
   `NumberRuleApiTests.java`, `NumberRuleSeedTests.java`.

## Task 1.1 — V49 migration + entity field

TDD RED first: extend the seed/API tests to cover the `modality` column shape (NULL = global;
scoped row accepted alongside a global row of the same family).

- New file ONLY: `lumen-system/src/main/resources/db/migration/V49__number_rule_modality_scope.sql`:
  `ALTER TABLE number_rule ADD COLUMN modality varchar(16)`, drop index
  `uk_number_rule_active`, recreate as
  `CREATE UNIQUE INDEX uk_number_rule_active ON number_rule (rule_type, COALESCE(modality, '')) WHERE active = 1 AND deleted = 0`.
  NEVER touch existing applied migrations. Comment lines carry NO semicolons (re-execution
  tests split on them).
- `NumberRule` entity: add `private String modality;` (@Builder pattern already on class;
  @Data/@Builder/@NoArgsConstructor/@AllArgsConstructor all present — do not disturb).

## Task 1.2 — Issuer resolution chain (exact → global → B0001)

TDD RED first in `NumberIssuerServiceTests` (and concurrency test precedent as in the file):
exact-scope win; no scoped rule → family global; blank/null modality → global only; no rule
anywhere → B0001 naming family + modality; two scopes of one family advance independent
`serial_cur`; concurrency within one scope yields distinct numbers.

- `NumberRuleMapper`: ADD overload `NumberRule selectActiveForUpdate(@Param("ruleType") String ruleType, @Param("modality") String modality)` — SQL `... AND active = 1 AND deleted = 0 AND modality = #{modality} FOR UPDATE` (exact match, called only with a non-blank token).
  The EXISTING single-arg method keeps its signature; its SQL gains `AND modality IS NULL` (its
  semantic becomes "the family's global rule" — before V49 no scoped rows exist, so existing
  callers' behavior is unchanged). Update its javadoc accordingly.
- `NumberIssuerServiceImpl`: ADD `public String issue(String ruleType, String modality)` (same
  REQUIRES_NEW + row-lock semantics, body shared/refactored as needed). The EXISTING
  `issue(String ruleType)` stays — delegate to `issue(ruleType, null)` (never re-implement;
  do not change its signature/annotation). Resolution: non-blank `modality` → exact lookup,
  null/blank result → global lookup (`selectActiveForUpdate(ruleType)`), still null → B0001
  business error whose message includes the family and (when present) the modality token.
- `NumberIssueController`: bind the extended request, call the two-arg overload (pass-through).

## Task 1.3 — Scope-aware rule CRUD

TDD RED first in `NumberRuleApiTests` (+ service tests if the split demands): duplicate-active
rejected per `(ruleType, modality)` — two global actives rejected, same-scope actives rejected,
`CT`-scoped + global coexist; rejection message names the scope; create with `modality` token
persists; active-toggle conflict on a scoped rule rejected.

- `NumberRuleSaveRequest` + `SetNumberRuleActiveRequest`: add `modality` (String, optional).
- `NumberRuleServiceImpl`: duplicate-active guard keys on (ruleType, modality) — NULL-safe
  (`isNull` vs `eq` branches in the LambdaQueryWrapper). Follow the existing A0714 data-duplication
  message pattern, scope named in the message. Update path: `modality` is IMMUTABLE after create
  (mirror the existing ruleType-immutable handling — patches format parameters only, silently
  keeps the stored scope; the UI disables the field on edit).

## House rules (non-negotiable)

- Java 11; every `./mvnw` invocation starts with
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home` (foreground
  shells lose JAVA_HOME — Lombok TypeTag crash otherwise).
- Every new class header `@author birdbro`; interface javadoc carries the class+method contract;
  `@Service`/`@Transactional` live on impls. Hutool (StrUtil/CollUtil) over hand-rolled.
- Never modify an existing public method signature — overloads only.
- Tests hit the shared team test server (`.env-test.local`): new fixtures follow the existing
  base-class timestamp-prefix family and hard-DELETE their rows (never soft-delete); expect
  occasional WAN `Connection reset` — re-run the class before diagnosing; pass
  `-Dsurefire.rerunFailingTestsCount=1` on suite-ish runs.
- DO NOT commit — the coordinator commits after review. Do not run the full reactor suite;
  smallest affected tests only:
  `cd /Users/birdbro/work/02-birdBro/00-github/lumenCollab/lumen-sev && export JAVA_HOME=... && ./mvnw test -pl lumen-system -am -q -Dtest='NumberRule*,NumberIssuer*' -Dsurefire.failIfNoSpecifiedTests=false`
  (redirect to a log file; report exit code + counts + log path).

## Report

Write `openspec/sdd/reports/number-rule-modality-scope-b1.md` (<80 lines): implemented items,
RED evidence (command + failing assertion), GREEN evidence (command + counts + exit), files
changed, self-review findings, concerns. Then reply ≤15 lines: Status / summary / report path.
