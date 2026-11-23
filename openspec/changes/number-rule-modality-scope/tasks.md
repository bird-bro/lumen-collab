# Tasks

## 1. Batch: lumen-system — scoped rule model + issuer resolution (sev)

- [x] 1.1 **V49 migration + entity field** — TDD RED first: extend
      `NumberRuleSeedTests`/`NumberRuleApiTests` for the `modality` column shape
      (NULL=global). Migration `V49__number_rule_modality_scope.sql`:
      `ALTER TABLE number_rule ADD COLUMN modality varchar(16)`, drop
      `uk_number_rule_active`, recreate as the COALESCE expression partial unique index
      `(rule_type, COALESCE(modality, '')) WHERE active = 1 AND deleted = 0` (comment
      lines carry no semicolons). `NumberRule` entity gains `modality`. GREEN: seed
      tests pass with existing families as global rules.
- [x] 1.2 **Issuer resolution chain** — TDD RED first: `NumberIssuerServiceTests` for
      exact-scope win / no-scope global fallback / blank-modality global / no rule
      anywhere → B0001 / scoped pools advance independent serials / concurrency
      distinctness within one scope. Mapper gains the overloaded
      `selectActiveForUpdate(ruleType, modality)` FOR UPDATE (existing single-arg stays);
      `NumberIssuerServiceImpl` gains `issue(ruleType, modality)` (overload — the
      single-arg delegates with null), exact→global resolution, B0001 naming family +
      modality. GREEN.
- [x] 1.3 **Scope-aware rule CRUD** — TDD RED first: duplicate-active rejected per
      `(ruleType, modality)` (two global actives; same-scope actives) while different
      scopes coexist; rejection message names the scope. `NumberRuleSaveRequest` +
      `SetNumberRuleActiveRequest` gain `modality`; `NumberRuleServiceImpl` guard keys
      on both; controller endpoints pass through; issue controller binds the extended
      request. GREEN.

## 2. Batch: contracts + kernel + US plumbing (sev)

- [x] 2.1 **Feign contract field** — `lumen-feign` `NumberIssueRequest` gains optional
      `modality` (`NumberApi` signature unchanged); system-side issue DTO/controller
      binds it. Compile + existing `NumberRuleApiTests` green.
- [x] 2.2 **Kernel port overload + jsonb widening** — TDD RED first: kernel tests for
      `ExamItemRef.modality` round-trip (old jsonb rows read null), port overload
      contract (single-arg = null modality), `registerOrder` deriving the issue modality
      from the first item's non-blank modality. `ExamItemRef` gains `modality`;
      `NumberIssuerPort` gains `issue(String ruleType, String modality)`;
      `ExamOrderRegistrationServiceImpl` uses the overload with the derived token. GREEN.
- [x] 2.3 **US adapter + create passthrough** — TDD RED first: `NumberIssuerPortAdapter`
      forwards the token to `NumberApi`; a create body whose item refs carry modality
      produces `exam_items` jsonb with modality intact and the port called with the
      first item's token (verify the request→order copy preserves the field — extend
      the build if it rebuilds refs item-by-item). GREEN.

## 3. Batch: lumen-web — console page + registration form (web, after sev user-verified)

- [x] 3.1 **号码规则 scope UI** — TDD RED first: `number-rule-page.test.tsx` — 适用范围
      column renders 全局 vs token; drawer scope SELECT defaults 全局, options loaded
      through the id-anchored dict items endpoint (1024, options by name, submitted as
      value token); edit echoes stored modality; duplicate-active rejection surfaces
      verbatim. `number-rules.ts` client + `/system/number-rules` page. GREEN (build +
      page tests).
- [x] 3.2 **US 登记 item modality** — TDD RED first: the registration form's create
      payload carries each picked 项目's modality token from the setting catalog; us
      orders client passes it through. `/us/registration` form + `us-orders.ts`. GREEN.

## 4. Batch: verify (both stacks)

- [ ] 4.1 **L1 build** — sev full reactor `./mvnw test` (JDK 11) exit 0; web `pnpm
      build` + full test suite exit 0. Evidence → verify.md.
- [ ] 4.2 **L2 WHEN/THEN walk** — every delta scenario mapped to a passing test /
      observed behavior; `verify.md` per-capability results.
