# Proposal

## Why

The number-rule directory enforces exactly ONE active rule per number family
(`uk_number_rule_active(rule_type)`), so a family like the 检查号 (登记号) shares one
serial pool platform-wide. Real deployments need finer pools: US and radiology each
numbering independently, and inside radiology either all devices sharing one pool or
per-modality pools (`CT0001` / `DR0001`). The directory has no scope dimension and the
`/issue` contract carries no context to resolve one.

## What Changes

- `number_rule` gains a nullable `modality` column (dict-1024 VALUE token — the same
  `varchar` modality-string shape `org_device.type` / `exam_item.modality` already
  store; NULL = 全局). The partial unique index becomes
  `(rule_type, COALESCE(modality, '')) WHERE active = 1 AND deleted = 0` — one active
  rule per family × scope, NULL-safe without PG-version coupling. Seeded families
  become global rules automatically.
- The issue contract gains an optional `modality` field; resolution is two-level:
  exact `(ruleType, modality)` active rule → the family's global (NULL) active rule →
  B0001. Row-lock burned-serial semantics are untouched (per-scope rows carry
  independent `serial_cur`, so `CT` / `DR` count independently).
- Rule CRUD duplicate-active guards and rejections become scope-aware (same family,
  different scope coexist).
- Kernel `NumberIssuerPort` gains an overloaded `issue(ruleType, modality)` (the
  existing single-arg method stays untouched per the overloading rule);
  `ExamItemRef` (jsonb) gains optional `modality` — zero DDL — and the kernel issues
  the accession with the first exam item's modality when present.
- US registration create passes the picked 项目's modality through the item refs to
  the port; patient 影像号 issuance keeps the single-arg call (global — 影像号 is not
  exam-scoped).
- The 号码规则 page gains an 适用范围 column and a scope SELECT in the drawer (options
  loaded id-anchored from dict 1024, saved value = the option's value token; default
  全局); scope-active conflicts surface the server rejection verbatim.
- Scope model user-ratified 2026-11-20: modality ONE dimension only — no department
  dimension. 全局 = the platform shared pool; a department wanting isolation adds its
  own modality-scoped rule (config action, not code).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `number-rule`: directory uniqueness becomes family × modality-scope; issuance
  resolution chain (exact scope → global fallback); issue body carries optional
  `modality`.
- `us-station`: the registration surface passes each picked 项目's modality through
  the item refs so the accession issues under the scoped rule.
- `web-system-console`: the 号码规则 page renders 适用范围 and the drawer selects it.
- `web-us-station`: the order form's create payload carries per-item modality.

## Impact

- `lumen-system`: V49 migration (column + index rewrite), `NumberRule` entity,
  `NumberRuleSaveRequest` / `SetNumberRuleActiveRequest`, `NumberRuleServiceImpl`
  guards, `NumberRuleMapper` lock query overloads, `NumberIssuerServiceImpl`
  resolution, issue controller.
- `lumen-feign`: `NumberIssueRequest` + optional `modality` (additive — no wire break;
  `NumberApi` signature unchanged).
- `lumen-core`: `NumberIssuerPort` overload, `ExamItemRef` jsonb widening,
  `ExamOrderRegistrationServiceImpl` first-item modality.
- `lumen-us`: create flow passes item refs through (kernel type reused by the US DTO).
- `lumen-web`: `number-rules.ts` client, `/system/number-rules` page, `us-orders.ts`
  client, `/us/registration` order form.
- Known tradeoff recorded in design: the modality token rides from the client (the
  authoritative setting 目录 response) until a setting exam-item Feign contract enables
  server-side authoritative resolution.
