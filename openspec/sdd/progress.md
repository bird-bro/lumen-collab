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
- Status: Complete (commit 661aca6) - review in progress
- Commits: 33e39b5..661aca6
- Review: Pending (gate: exact pins, strict TS, no caret/tilde on six core deps)
- Notes: @types/react 18.3.31 / @types/react-dom 18.3.7 / @types/node 22.16.5 added exact-pinned (strict-build requirement, disclosed); alias proof redone with value-import + TS2307 negative control after the first side-effect-import probe proved vacuous; no "type":"module" so __dirname alias is valid

### Batch 3-4: API seam + unified-entry proxy (tasks 3.1-4.3)
- Status: Pending
- Commits: -
- Review: Pending (gate: no invented endpoints; proxy target = nginx :80 via VITE_API_ENTRY_URL)
- Notes: -

### Batch 5: Shell, theming, department routing (tasks 5.1-5.5)
- Status: Pending
- Commits: -
- Review: Pending (contract calls this the most important review: non-red primary, lazy chunks, zero-request placeholders)
- Notes: -

### Batch 6: Debt and spec hygiene (tasks 6.1-6.4)
- Status: Pending
- Commits: -
- Review: Pending
- Notes: L3 stays SKIP (test: null) - debt.md already carries it

---

## Summary (frontend)
- Total tasks: 22
- Completed: 0
- Blocked: 0
- Final review: Pending
