# Execution Contract

> Auto-generated compact bridge from planning artifacts.
> Do not duplicate full proposal/spec/design/tasks content here; cite source paths and line refs.
> Review AI-marked sections before applying. guard.sh checks contract existence + freshness.

## Source Map

- Proposal: `proposal.md`
- Specs: `specs/*/spec.md`
- Design: `design.md`
- Tasks: `tasks.md`

## Intent Lock

- **Change name**: number-rule-modality-scope
- **Problem to solve**:
The number-rule directory enforces exactly ONE active rule per number family
(`uk_number_rule_active(rule_type)`), so a family like the 检查号 (登记号) shares one
serial pool platform-wide. Real deployments need finer pools: US and radiology each
numbering independently, and inside radiology either all devices sharing one pool or
per-modality pools (`CT0001` / `DR0001`). The directory has no scope dimension and the
`/issue` contract carries no context to resolve one.

- **In scope**: `number_rule` gains nullable `modality` (dict-1024 value token) +
  `(rule_type, COALESCE(modality,''))` active-unique index; `/issue` gains optional
  `modality` with exact→global resolution; scope-aware CRUD guards; kernel
  `NumberIssuerPort` overload + `ExamItemRef.modality` jsonb widening; US create
  passthrough; 号码规则 page 适用范围 column + drawer scope select; US 登记 item
  modality payload.
- **Out of scope**: department scope dimension (user-ratified modality-only 2026-11-20);
  page filter fields; issuer-side dict validation of the modality token; server-side
  authoritative item→modality resolution (needs a setting Feign contract — debt);
  `serialCur` in CRUD bodies (runtime state).

## Approved Behavior

<!-- AI: review and confirm these verification scenarios -->
- `specs/number-rule/spec.md`
5:### Requirement: Number Rule Directory
16:#### Scenario: Rule CRUD enforces system perms
25:#### Scenario: Family defaults are seeded
31:#### Scenario: Scoped rule coexists with the family global rule
37:### Requirement: Platform Number Issuance
44:#### Scenario: Concurrent issuance does not duplicate
49:#### Scenario: Stamp rule resets by day
54:#### Scenario: Burned serial survives a caller rollback
59:#### Scenario: Scoped pools count independently
67:### Requirement: Modality-Scoped Issuance Resolution
76:#### Scenario: Exact scoped rule wins
82:#### Scenario: No scoped rule falls back to the family global rule
88:#### Scenario: Blank modality resolves the global rule
93:#### Scenario: No active rule anywhere refuses
- `specs/us-station/spec.md`
5:### Requirement: Registration Surface
17:#### Scenario: Create a booked order
25:#### Scenario: Illegal transition is refused verbatim
31:#### Scenario: Cancel carries a reason
37:#### Scenario: Modality reaches the issuer port
- `specs/web-system-console/spec.md`
5:### Requirement: Number Rule Page Shell
15:#### Scenario: Page loads against the real backend
21:#### Scenario: Toolbar follows button perms
26:#### Scenario: Scope column distinguishes global and scoped rows
31:### Requirement: Number Rule Form and Delete
35:#### Scenario: Create a rule with a dict-backed stamp format
40:#### Scenario: Create a stamped rule
47:#### Scenario: Edit echoes the stored format
52:#### Scenario: Missing format blocks submit
57:#### Scenario: Invalid serial length surfaces on the field
62:#### Scenario: Delete requires confirmation
67:#### Scenario: Rule type selects the platform family
72:#### Scenario: Scope defaults to global and submits the token
78:#### Scenario: Edit echoes the stored scope
84:#### Scenario: Scoped duplicate-active rejection surfaces verbatim
- `specs/web-us-station/spec.md`
5:### Requirement: Order Form and Actions
15:#### Scenario: Create posts identity, items, and extension
22:#### Scenario: Item modality rides the create payload
28:#### Scenario: 登记 transitions the order
33:#### Scenario: Cancel requires a reason and confirmation
38:#### Scenario: Illegal transition surfaces verbatim


## Design Constraints

- **Architecture/Decisions**:
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
- **Technical constraints**: Java 11 / Spring Boot 2.5.15 (no version bumps); never
  modify existing public methods — overloads only (`NumberIssuerService.issue`,
  `NumberIssuerPort.issue`, `NumberRuleMapper.selectActiveForUpdate`); migration V49 in
  lumen-system (comment lines carry no semicolons — re-execution-test discipline);
  additive Feign wire change only (fail-fast fallback untouched); frontend builds
  against the REAL backend, no mock layer; scope select options loaded via the
  id-anchored dict items endpoint (1024) but submitted as the value token (D1).

## Task Batches

<!-- AI: review task batching; adjust goals/inputs/outputs per batch -->
5:- [ ] 1.1 **V49 migration + entity field** — TDD RED first: extend
13:- [ ] 1.2 **Issuer resolution chain** — TDD RED first: `NumberIssuerServiceTests` for
21:- [ ] 1.3 **Scope-aware rule CRUD** — TDD RED first: duplicate-active rejected per
30:- [ ] 2.1 **Feign contract field** — `lumen-feign` `NumberIssueRequest` gains optional
33:- [ ] 2.2 **Kernel port overload + jsonb widening** — TDD RED first: kernel tests for
39:- [ ] 2.3 **US adapter + create passthrough** — TDD RED first: `NumberIssuerPortAdapter`
47:- [ ] 3.1 **号码规则 scope UI** — TDD RED first: `number-rule-page.test.tsx` — 适用范围
53:- [ ] 3.2 **US 登记 item modality** — TDD RED first: the registration form's create
59:- [ ] 4.1 **L1 build** — sev full reactor `./mvnw test` (JDK 11) exit 0; web `pnpm
61:- [ ] 4.2 **L2 WHEN/THEN walk** — every delta scenario mapped to a passing test /

## Test Obligations

<!-- AI: identify behaviors requiring failing-test-first from scenarios above -->
- **Behaviors requiring failing-test-first**: scoped resolution chain (exact win /
  global fallback / blank-modality global / none→B0001); scoped pools independent
  serials; duplicate-active per (ruleType, modality) with different-scope coexist;
  kernel first-item modality derivation + jsonb widening tolerance (old rows null);
  US create passthrough preserving `ExamItemRef.modality`; page scope column render +
  scope select default/echo/submit-shape + duplicate-active rejection verbatim.
- **Required edge cases**: two global actives of one family rejected (COALESCE NULL
  safety); unknown modality token → global fallback (no validation); legacy single-arg
  issue unchanged (patient 影像号); edit echo of stored token rendered by dict name.
- **Regression-sensitive areas**: seeded family rows stay active-global after V49;
  stamp-day reset and burned-serial semantics untouched; `NumberApi` signature and
  internal-key guard unchanged; existing number-rule page tests (format select, perms)
  stay green.

## Execution Mode

- **Mode**: `SDD`
- **Selection rationale**: 9 implementation tasks across 5 modules (lumen-system,
  lumen-feign, lumen-core, lumen-us) + lumen-web — cross-module dependency chain
  (1.x system core → 2.x contracts/kernel/us → 3.x web after sev user-verified) with
  a schema migration and a shared Feign contract; the SDD per-batch review gates apply
  (us-station-registration precedent).

## Verification Dimensions

| Dimension | Status | Findings |
|:--|:--|:--|
| Completeness | Pending | - |
| Correctness | Pending | - |
| Coherence | Pending | - |

**Overall**: Pending

## Review Gates

- **Mandatory review points**: DP-3 user approval of this contract (before apply); SDD
  per-batch approval after each batch's RED→GREEN demonstration (batch 1 → 2 → 3 → 4).
- **Blocking categories**: schema migration errors (V49); Feign wire-shape breaks;
  resolution-chain behavior divergence from the delta scenarios.

## Escalation Rules

- **When to rewind to proposing**: new scope appears; key design assumption wrong; interfaces change materially
- **When NOT to continue**: contract intent lock no longer matches proposal scope
