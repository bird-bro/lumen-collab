# SDD Progress Ledger

## Change: init-backend-skeleton
## Execution Mode: SDD
## Started: 2026-10-29
## Base: e7d182e (branch: dev)

### Batch 1: Aggregator + lumen-bom (tasks 1.1-1.4)
- Status: Complete + Approved (commit 00cca62; review fixes commit follows)
- Commits: e7d182e..00cca62
- Review: Approved - deviations 1/2 accepted with reproduction evidence; Important-1 (dcm4che repo propagation) fixed in aggregator; Important-2 (reactor-only builds) documented
- Notes: implementer measured 1454 shared depMgmt keys, 0 version mismatch; okhttp 3.14.4-vs-3.14.9 drift avoided by adopting OPS's import order
- Notes: toolchain = lumen-sev/mvnw (wrapper 3.3.4 → Maven 3.9.9) + JDK 11 (/Library/Java/JavaVirtualMachines/jdk-11.jdk); settings.xml sanitized (aliyun mirror only, no credentials)

### Batch 2: OPS framework/common verbatim copy (tasks 2.1-2.3)
- Status: Complete + Approved (commit ab2baa7; review fixes commit follows)
- Commits: 6bd2cf6..ab2baa7
- Review: Approved - byte-faithfulness gate re-verified independently (36=36, 54=54, only recorded adaptations); Important-1 (D5 (a) scope) reconciled; Minor items deferred to B5 (settings URL modernization, dispose.properties mojake noted)
- Notes: deleted classes unreferenced in copied modules; OPS-wide consumers only in uncopied business modules

### Batch 3: lumen-auth shell (tasks 3.1-3.2)
- Status: Complete + Approved after fix (commits 6634be5 + fix eb34dd2)
- Commits: 94ca262..eb34dd2
- Review: Needs fixes -> fixed - Critical: Nacos config neutralization didn't reach bootstrap phase (12 live :8848 attempts); fixed via bootstrap.yml test-profile document, evidence 8848 grep 21->0, red reason unchanged (xss.excludes), surefire 2.95s->0.98s
- Notes: port 8081, scanBasePackages="org.birdbro"; D3 amendment recorded in coordination doc; Batch 4 red-test intelligence: token.* + xss.* properties will be the red

### Batch 4: Zero-infrastructure verification (tasks 4.1-4.3)
- Status: Complete + Approved (commit 2eecd07; review-fix commit follows)
- Commits: eb34dd2..2eecd07
- Review: Approved - gate 4 passed: context loads, zero Nacos/Redis/DB contact, L1+L3 exit 0; all 3 concerns accepted (stub quarantined / @MapperScan recorded as entry-class contract / dummy-URL datasource with explicit initial-size: 0)
- Notes: task 4.1 sequencing note recorded (observed red is the strongest available); framework/common byte-untouched

### Batch 5: Documentation and spec hygiene (tasks 5.1-5.5)
- Status: Complete (commit 8a42961) - final review in progress
- Commits: 2eecd07..8a42961
- Review: Pending (final cross-batch review)
- Notes: 4 drift items found and fixed (registry row, adaptation counts in sev CLAUDE + tasks 2.2, sev AGENTS.md resync, naming.md wording)

---

### Verify remediation (tweak round, tasks 6.1-6.4)
- Status: Complete
- Scope: 13 PARTIAL + 2 deferred-✗ scenarios resolved at the wording level; command portability (MAJOR-1), Redis default-on semantics + opt-out (MAJOR-2 rebuttal + real gap), MetaObjectConfig fill scope (data), SUCCESS("0000") note, coordination ApiEnvelope claim, offline-package risk registered; one more "three adaptations" straggler caught in design Risks.
- Gates: L1 exit 0, L3 exit 0, strict valid; root-invoked dir-build re-proven exit 0

## Summary
- Total tasks: 21 (17 impl + 4 remediation)
- Completed: 21
- Blocked: 0
- Final review: PASS (dimensions filled in contract)

---

# Frontend Change: init-web-skeleton
## Execution Mode: SDD
## Started: 2026-11-02
## Base: 33e39b5 (branch: dev)

### Batch 1-2: Scaffold + pinned toolchain + module layout (tasks 1.1-2.2)
- Status: Complete + Approved (commits 661aca6 + minor-fix 9ab1d0b)
- Commits: 33e39b5..9ab1d0b
- Review: Approved - pin table 10/10 exact (resolved==pinned), lock free of React19/AntD6/Vite8/TS7, alias positive+negative controls reproduced; 4 minors (2 fixed: *.local gitignore, tsconfig.node.json gated into build; 2 noted: pages/.gitkeep form superseded by B5, wildcard-alias equivalence - single `@/*` in both configs proven functionally identical to the five named aliases, reviewer ran positive+negative controls)
- Notes: @types trio exact-pinned; no "type":"module" so __dirname alias is valid

### Batch 3-4: API seam + unified-entry proxy (tasks 3.1-4.3)
- Status: Complete + Approved (commit 3cfacbe)
- Commits: 9ab1d0b..3cfacbe
- Review: Approved - endpoint-free seam re-proven; proxy both rungs physically reproduced (Rung B: request arrived at controlled :9999 listener); TS2322 control judged declaration-level (vacuous in variant A) - queued for first envelope-owning change
- Notes: 5173 occupied by unrelated vite - review ran 5174/strictPort

### Batch 5: Shell, theming, department routing (tasks 5.1-5.5)
- Status: Complete + Approved (commit d00617a)
- Commits: 3cfacbe..d00617a
- Review: Approved - teal HSL(185,81,29%) independently recomputed; laziness proven IN THE EMITTED BUNDLE; placeholders request-free; implementer's "pre-existing" antd-chunk claim DISPROVEN (base build 142.78kB clean, warning new in B5, benign) -> correction addendum in report
- Notes: chunkFileNames traceability + mutation-control-queued recorded in coordination doc (B6)

### Batch 6: Debt and spec hygiene (tasks 6.1-6.4)
- Status: Complete (commit 65b163a)
- Commits: d00617a..65b163a
- Review: folded into final review (Approved)
- Notes: debt entries verified live; registry flipped; B5 addendum appended

---

## Summary (frontend)
- Total tasks: 22
- Completed: 22
- Blocked: 0
- Final review: PASS (Ready for /opsx:verify)

---

# Change: auth-rbac-foundation
## Execution Mode: SDD
## Started: 2026-11-02
## Base: 00c04e1 (branch: dev)

### Preflight rulings (user, 2026-11-02)
- Unauthenticated rejection = envelope code `"203"` (copied AuthenticationEntryPointImpl
  semantic) - auth-rbac delta + tasks 3.1 + proposal re-worded; artifacts_hash refreshed
  (602c782b), strict validate exit 0
- Test-server credentials: user fills `lumen-sev/.env-test.local` themselves (gitignored;
  AUTH_TEST_* names fixed in that file)
- Test-server PG <= 14 -> Flyway 7.14.0 (Boot-managed), no version pin needed
- New-code wiring decisions under design D6's "apply 时核对" mandate:
  WebSecurityCustomizer permits `/api/v1/auth/login`; UserHeaderBridgeFilter derives
  USER-HEADER from the security context (MetaObjectConfig fills create_by); UserDetailsServiceImpl
  ships in lumen-auth; seed admin `admin`/`admin123` + must-change-password flag

### Batch 1: Test-server integration base (tasks 1.1-1.3)
- Status: In progress
- Status: Complete + Approved (commits bb2815e + green-proof fix 9da15e9; minor cleanups in follow-up)
- Review: Approved at HEAD - bb2815e carried three latent context-load blockers (Boot flyway
  check-location, Flyway 7 schemas/default-schema, RANDOM_PORT port assert) fixed by 9da15e9;
  Important-1 (report green evidence) resolved by coordinator addendum; 4 minors (2 fixed,
  docs committed at boundary, 2 test-only nits accepted)
- Notes: green run on real server PG 14.11; Flyway resolves 7.7.3 (Boot 2.5.15 managed, NOT
  7.14.0) - PG14 is WARN-only (decompile-verified), watch-item carried to Batch 5 coordination
  doc; test server credentials supplied by user in-chat and written to gitignored .env-test.local

### Batch 2: Flyway migrations + data layer (tasks 2.1-2.3)
- Status: In progress
- Status: Complete + Approved (commit c94d004; review 0 Critical/Important, 7 minors)
- Review: Approved - DDL/seed compared 1:1 vs brief, setval block verified load-bearing,
  suite independently re-run 14/14 green MVN_EXIT=0
- Notes: global gitignore *.sql (machine-level) was silently hiding migrations -> directory-
  scoped db/migration/.gitignore negation; docs/deployment/pg-init.sql was a prior casualty -
  coordinator committed it (d00fd96) with repo-level negation. Minor watch-items handed to
  Batch 3: password hash must not leak (DTO mapping), deleted=0 on permission joins;
  future explicit-id seed migrations repeat the setval tail; Flyway 7.7.3 x PG14.11 still
  WARN-only (0 ERROR lines in migrated run)

### Batch 3: Login loop (tasks 3.1-3.3)
- Status: In progress
- Status: Complete + Approved (commit fe03c96; review 10/10 PASS, 0 Critical/Important, 7 minors)
- Review: Approved - suite independently re-run 20/20; TokenService UUID-vs-JWT trap caught
  by pair-3 red/green BEFORE commit (createToken return value is the JWT; loginUser.token
  holds the Redis-key UUID); 203-not-404 red shape explained (chain precedes dispatch)
- Notes: minor handoffs to Batch 4 - A0111 service check required + DB partial-unique index
  kept (minor-6); shared BCryptPasswordEncoder bean optional polish (minor-2); to later
  work: me() enable re-check/token revoke (minor-3), servlet-level CORS if cross-origin
  SPA calls login (minor-4), A0400 validation test (minor-5)

### Batch 4: Admin CRUD (tasks 4.1-4.4)
- Status: In progress
- Status: Complete + Approved (commit b72d4de; review 10/10 PASS, 0 Critical/Important, 5 minors)
- Review: Approved - 34/34 independently re-confirmed; role-delete cascade verified as the
  only sound choice (Batch-3 perms SQL has no role join - inert bindings would keep granting);
  envelope discovery recorded (void->"204" branch; R.ofFail drops its data arg; mutations
  return data:true)
- Notes: RECORD AT ARCHIVE/VERIFY (minor-3): "authenticated administrator" enforced as
  authentication-only - authority gating deferred to DataScope/radiology change per task
  scope. Later-batch candidates: level re-derivation on unit re-parent, @NotBlank on update
  DTOs, unit-delete child guard, cycle detection, user list pagination, A0400 validation test

### Batch 5: Documentation & spec hygiene (tasks 5.1-5.3)
- Status: In progress
