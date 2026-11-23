---
verify:
  change: number-rule-modality-scope
  date: 2026-11-23
  overall: PASS
  stacks:
    backend:
      L1: PASS
      L2: "33/33"
      L3: PASS
    lumen-web:
      L1: PASS
      L2: "33/33"
      L3: PASS
---

# Verification: number-rule-modality-scope

Overall: **PASS**

## Stack Summary

| Stack | L1 Build | L2 Spec | L3 Tests | Log |
|:--|:--|:--|:--|:--|
| backend | PASS (`./mvnw compile -q`, exit 0) | 33/33 | PASS (full reactor `./mvnw test -q`, 449 tests / 0 failures / 0 errors / 0 skipped, exit 0) | logs/build-backend.log · logs/test-backend.log |
| lumen-web | PASS (`pnpm build`, tsc+vite, exit 0) | 33/33 | PASS (`pnpm test`, 462 tests / 35 files / 0 failures, exit 0 — quiet-machine rerun) | logs/build-lumen-web.log · logs/test-lumen-web-rerun2.log |

L2 counted ONCE across both stacks (33 scenarios; backend scenarios evidenced in lumen-sev only, frontend scenarios in lumen-web only — cross-domain rule held).

## Scenario Alignment

Full per-scenario table (33 rows, all `✓ IMPLEMENTED` with file:line evidence) audited 2026-11-23 by the L2 auditor; highlights:

| Scenario (spec) | Status | Evidence |
|:--|:--|:--|
| Rule CRUD enforces system perms (number-rule) | IMPLEMENTED | NumberRuleController.java:57-83 (system:number:*); NumberRuleServiceImpl.java:141-161 NULL-safe per-scope guard; V49 COALESCE index |
| Family defaults are seeded (number-rule) | IMPLEMENTED | V40:40-54 + V44:32-34 (three families active global); V49 leaves NULL |
| Scoped rule coexists with family global (number-rule) | IMPLEMENTED | NumberRuleServiceImpl.java:58-61,147-151 + V49:18-20 |
| Concurrent issuance does not duplicate (number-rule) | IMPLEMENTED | NumberRuleMapper.java:34-37,54-58 FOR UPDATE; NumberIssuerServiceImpl.java:56-101 |
| Stamp rule resets by day (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:79-94 |
| Burned serial survives a caller rollback (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:56,62 (REQUIRES_NEW both overloads) |
| Scoped pools count independently (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:64-71,96-106 (per-scope row = own serial_cur/prefix) |
| Exact scoped rule wins (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:66-68 (selectScopedActiveForUpdate first) |
| No scoped rule falls back to global (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:69-71 |
| Blank modality resolves the global rule (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:57-68; NumberIssueController.java:36-41 |
| No active rule anywhere refuses (number-rule) | IMPLEMENTED | NumberIssuerServiceImpl.java:72-76 (B0001 family+token) |
| Create a booked order (us-station) | IMPLEMENTED | RegistrationOrderServiceImpl.java:100-141; ExamOrderRegistrationServiceImpl.java:85-95 |
| Illegal transition refused verbatim (us-station) | IMPLEMENTED | ExamOrderStateMachineServiceImpl.java:77-81 |
| Cancel carries a reason (us-station) | IMPLEMENTED | RegistrationOrderServiceImpl.java:180-190; ExamOrderStateMachineServiceImpl.java:87-90 |
| Modality reaches the issuer port (us-station) | IMPLEMENTED | ExamOrderRegistrationServiceImpl.java:92-93,119-122; NumberIssuerPortAdapter.java:45-55; ExamItemRefListJsonbTypeHandler.java:40; ExamItemRef.java:39 |
| Page loads against the real backend (web-system-console) | IMPLEMENTED | number-rule/index.tsx:145-164; number-rules.ts:72-80 |
| Toolbar follows button perms (web-system-console) | IMPLEMENTED | index.tsx:408,421,424,337 |
| Scope column distinguishes global and scoped rows (web-system-console) | IMPLEMENTED | index.tsx:309-324 |
| Dict-backed stamp format / stamped rule / edit echo / missing format / serial length / delete confirm / rule type select (web-system-console ×7) | IMPLEMENTED | index.tsx:553-566,195-196,215,557,526-532,425-430,487-492 (batch pre-existing surface, regression-green) |
| Scope defaults to global and submits the token (web-system-console) | IMPLEMENTED | index.tsx:194,135,235 ('' sentinel → `\|\| null` on the wire) |
| Edit echoes the stored scope (web-system-console) | IMPLEMENTED | index.tsx:210 (`?? ''`), :501 (disabled + raw-token fallback) |
| Scoped duplicate-active rejection surfaces verbatim (web-system-console) | IMPLEMENTED | index.tsx:256-258 (messageApi.error raw, drawer stays open), :284-295 |
| Create posts identity, items, and extension (web-us-station) | IMPLEMENTED | us/registration/index.tsx:177-201 |
| Item modality rides the create payload (web-us-station) | IMPLEMENTED | index.tsx:184-190 (row-derived, `?? null`); us-orders.ts:29-31 |
| 登记 transitions / cancel reason+confirm / illegal verbatim (web-us-station ×3) | IMPLEMENTED | index.tsx:215-222,362-366,416-433,220,226-232 |

## Issues

(none blocking)

- Environment note, both web full-suite runs WHILE the backend reactor suite ran concurrently produced exactly ONE nondeterministic antd Popconfirm pollution failure each (`Found multiple elements with the role tooltip` — users-page delete in run 1, number-rule-page delete in run 2; neither file touched by this change's code paths except number-rule tests are in the same file family). Both files pass 20/20 and 17/17 in isolation; the quiet-machine full rerun is 462/462 green (logs/test-lumen-web-rerun2.log). Recorded per the honest-credential discipline; no code action.

## Next

- PASS → `/opsx:archive number-rule-modality-scope`.
