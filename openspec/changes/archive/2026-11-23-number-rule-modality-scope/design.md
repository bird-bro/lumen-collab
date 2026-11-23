# Design

## Context

The number-rule directory (number-rule-unification-sev, archived) owns every family in
one SYSTEM-schema table with one row-lock burned-serial issuer and the platform's first
Feign contract (`NumberApi`). Today one active rule per family
(`uk_number_rule_active(rule_type)`), `/issue {ruleType}`, no context. Grounded facts
driving the design:

- dict 1024 detail VALUES are the DICOM modality tokens (`CT`/`DR`/`US`…); the platform
  stores modality as `varchar` value tokens everywhere (`org_device.type` V27,
  `exam_item.modality` V29).
- Kernel `ExamOrder.examItems` is jsonb `List<ExamItemRef{itemId, itemName}>` — no
  modality anywhere in lumen-core/lumen-us today.
- The kernel issues the accession inside `ExamOrderRegistrationServiceImpl.registerOrder`
  via `numberIssuerPort.issue("登记号")` when the caller brought none; US never pre-issues.
- dict 1028 family tokens are the Chinese values (影像号/登记号/预约号) per V44.

## Goals / Non-Goals

- **Goal**: family × modality-scope rule pools; two-level resolution with global
  fallback; US registration carries its 项目 modality; console UI manages scope.
- **Non-Goals**: department scope dimension (user-ratified 2026-11-20 — 全局 = platform
  shared pool; department isolation = each department adds its modality-scoped rule);
  page filter fields; issuer-side dict validation of the modality token; server-side
  authoritative item→modality resolution (needs a setting Feign contract that does not
  exist yet — recorded as debt).

## Decisions

### D1 — Scope column stores the dict-1024 value token (`varchar(16)`, NULL = 全局)

Parity with the established modality storage shape (`org_device.type`, `exam_item.modality`
store the dict-1024 VALUE token). The 2026-11-19 id-anchor ruling governs UI dict
option loading/anchoring, not cross-table storage format — the UI loads 1024 options
through the id-anchored items endpoint and submits the chosen option's value token.
DICOM tokens are machine values, not renameable display names, so the anti-rename
intent is preserved.

### D2 — NULL-safe uniqueness by expression index, not `NULLS NOT DISTINCT`

`CREATE UNIQUE INDEX uk_number_rule_active ON number_rule (rule_type,
COALESCE(modality, '')) WHERE active = 1 AND deleted = 0`. PG treats NULLs as distinct
in plain partial unique indexes (two global actives would slip through); the COALESCE
expression is version-proof (no PG15 coupling). `NumberRuleServiceImpl` duplicate-active
guard stays the front line (message names the scope, A0714 pattern); the index is the
concurrency backstop. Existing seeded rows become global rules untouched. The scope is
part of a rule's pool identity — like the family token, `modality` is immutable after
create (an update patches format parameters only; a scope switch would re-point the
burned-serial pool identity), so the edit form echoes it disabled.

### D3 — Issuer resolution: exact → global, row lock on the resolved row only

`NumberIssuerService` gains `issue(ruleType, modality)` (overload; the existing single-arg
method delegates with null — patient 影像号 and any legacy caller stay global). Resolution:
`selectActiveForUpdate(ruleType, modality)` first, then
`selectActiveForUpdate(ruleType, null)` fallback, then B0001 (system-state). Mapper
gains an overloaded `@Select ... FOR UPDATE` taking both params (the existing single-arg
query stays). REQUIRES_NEW, stamp-day reset, zero-pad format are untouched — a scoped
rule row is just another row with its own `serial_cur`.

### D4 — Contract additivity on the Feign surface

`NumberIssueRequest` (both the `lumen-feign` contract class and the system-side
controller DTO) gains optional `modality`; `NumberApi`'s method signature is unchanged.
Additive JSON field — consumers rebuilt against the new jar serialize one more optional
key; the fail-fast fallback and internal-key guard are untouched.

### D5 — Kernel plumbing rides the jsonb item refs, zero DDL

`ExamItemRef` gains `modality` (jsonb widening — pre-existing rows simply lack the key;
Jackson reads them as null). `NumberIssuerPort` gains the overloaded
`issue(String ruleType, String modality)`; the single-arg method keeps its contract
(= null modality). `ExamOrderRegistrationServiceImpl.registerOrder` derives the issue
modality from the FIRST exam item's non-blank `modality` (null-safe → global fallback;
mixed-modality orders are outside v1 scope — US issues single-item 项目). The us
`NumberIssuerPortAdapter` implements the overload and forwards the token to Feign.

### D6 — US passes the catalog's modality through untouched

`RegistrationCreateRequest` already reuses the kernel `ExamItemRef` — the create path
must preserve the new field end-to-end. The US service builds the order from the request
(`BeanUtil.copyProperties` house idiom / ref list pass-through): task 2.3 verifies the
refs reach `exam_order.exam_items` WITH `modality` (if the service rebuilds refs
item-by-item, the build carries the field). The modality token originates from the
setting catalog response the UI already renders — the client forwards it verbatim.

### D7 — Console UI: one column, one select

Table gains 适用范围 (全局 / token). Drawer gains a scope SELECT defaulting to 全局,
options loaded through the id-anchored dict items endpoint (1024), rendered by name and
submitted as the value token; edit echoes the stored token's option. Existing-page edit —
no prototype; drawer chrome follows the system-console conventions (existing drawer
grows one field). Duplicate-active server rejection renders verbatim (the house
backend-refusal pattern).

### D8 — Migration V49 (lumen-system)

`ALTER TABLE number_rule ADD COLUMN modality varchar(16)`; drop the old
`uk_number_rule_active`; create the COALESCE expression partial unique index. No seed
changes; no data backfill (existing rows = global). Comment lines carry no semicolons
(the re-execution-test discipline).

## Risks / Trade-offs

- **Client-origin modality token**: the token rides from the client (the authoritative
  setting catalog it renders) until a setting exam-item Feign contract enables
  server-side resolution. Accepted for v1 — a wrong token only mis-scopes the pool
  (still a valid rule row), never fabricates or corrupts numbers.
- **First-item modality on multi-item orders**: kernel uses the first item's token;
  mixed-modality orders would number by the first item's scope. Documented; US v1 is
  single-项目 per order.
- **`serial_cur` state per scope row**: creating a scoped rule starts a fresh pool at
  0 — operators must seed the starting serial in the create body if migrating an
  existing manual sequence (the create request already accepts runtime-format fields;
  `serialCur` is runtime state and stays out of CRUD bodies — seed via a follow-up
  manual UPDATE if ever needed; not v1 scope).

## Migration Plan

V49 only (column + index). Deploy order: sev first (this change), then web against the
real backend. Consumers (patient, us) rebuild against the new `lumen-feign` jar — the
old jars keep working until rebuilt (the optional field is simply absent on the wire).

## Open Questions

(none — scope model user-ratified 2026-11-20; storage shape follows the platform
modality-string convention)
