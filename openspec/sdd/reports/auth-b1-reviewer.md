# Batch 1 Review - auth-rbac-foundation (tasks 1.1-1.3)

> Reviewer verdict persisted verbatim by the coordinator (2026-11-02); the reviewer
> agent is read-only and reported in-conversation. Review range 00c04e1..bb2815e plus
> the fix commit 9da15e9 that superseded its Critical findings.

## Verdict

**Task quality: Needs fixes (documentation only) → code Approved at HEAD (9da15e9).**

All binding decisions are implemented exactly, verbatim discipline and security hygiene
are intact, and the red-to-green chain is genuinely proven on the real server
(PostgreSQL 14.11) after commit 9da15e9 corrected three latent context-load blockers in
bb2815e. The only remaining must-do was refreshing the implementer report with the fix
commit and green evidence (done - see the report ADDENDUM).

## Spec Compliance (bb2815e, as fixed at HEAD)

| Binding decision | Status |
|---|---|
| 1.1 env file gitignored, exact var names | Compliant (file absent from both commits; .gitignore:20 hits; all 8 AUTH_TEST_* names match D3) |
| Env loader (walk up, env-wins, explicit failure) | Compliant (require() names every missing var + expected path; distinct file-found/missing branches) |
| Bootstrap algorithm | Compliant (postgres maintenance DB → pg_database guard → CREATE DATABASE identifier-whitelisted → CREATE SCHEMA IF NOT EXISTS auth; once-per-JVM DCL) |
| @DynamicPropertySource props | Compliant at HEAD (currentSchema=auth URL, creds, redis host/port, conditional redis password, flyway schemas+default-schema+check-location) |
| flyway-core unversioned in lumen-auth only | Compliant (Boot 2.5.15 manages 7.7.3, verified in spring-boot-dependencies pom) |
| application-test.yml | Compliant (dummy datasource removed; Nacos-off + token/xss dummies kept) |
| Converted AuthApplicationTests | Compliant at HEAD (real connection + current_schema + flyway history + identity asserts; stub import kept for Batch 3) |
| Verbatim discipline | Compliant (zero framework/common changes in either commit) |
| Unconfigured = explicit failure | Compliant and proven (RED verbatim in report; loader not weakened; no invented credentials) |

Latent blockers in bb2815e (all latent - the RED run fails earlier in require()), fixed
in 9da15e9:
1. Boot 2.5.15 flyway check-location rejects empty db/migration (decompiled
   FlywayConfiguration.checkLocationExists evidence) → test-only check-location=false.
2. Flyway 7.x: default-schema must be member of schemas → spring.flyway.schemas=auth.
3. RANDOM_PORT vs fixed-port identity assert → local.server.port set-and-positive.

## Strengths

- Exemplary unconfigured-failure design (two message branches, every missing variable
  named, expected file path included, surfaces as the surefire cause).
- Security hygiene clean: no credentials in diff/tree, bootstrap never logs the password,
  CREATE DATABASE identifier-validated, pg_database lookup via PreparedStatement.
- Bootstrap semantics right: existence-guarded CREATE DATABASE, idempotent schema,
  connectTimeout=10 fail-fast, failed bootstrap leaves retry allowed (no cached success).
- Real-behavior assertions beyond the minimum (current_schema() through the pool;
  captured log proves the bootstrap path ran).
- Honest DONE_WITH_CONCERNS handling (no invented credentials, no weakened loader).

## Issues

### Critical (Must Fix)
- None remaining at HEAD (the three against bb2815e are fixed by 9da15e9 + green run).

### Important (Should Fix)
1. Implementer report lacked the configured-green evidence + fix commit (task 1.3's
   Verify) - RESOLVED by the coordinator addendum (2026-11-02).

### Minor (Nice to Have)
1. Stale "Flyway 7.15" comment → 7.7.3 (FIXED in follow-up commit).
2. Tautological assertTrue(historyEntries >= 0) → dropped, query itself is the proof
   (FIXED in follow-up commit; ==0 was rejected because it would break once Batch 2's
   migrations land).
3. Governance docs uncommitted (batches/, delta edits, tasks.md, ledger) → committed by
   coordinator at the batch boundary.
4. AuthTestEnv robustness nits (stray parent-dir file adoption; malformed lines silently
   ignored) - test-only, accepted as-is; both still fail explicitly downstream.

## Flyway 7.7.3 vs PostgreSQL 14.11 - watch-item (not Critical)

Boot 2.5.15 manages flyway-core 7.7.3; test server is PG 14.11. Decompiled
PostgreSQLDatabase.ensureSupported(): PG 14+ only triggers LOG.warn ("not tested,
latest supported is 13") - no throw; green run confirms. Residual risk: Batch 2's real
DDL on the untested combination. If Batch 2 hits migration misbehavior, the fix is a
lumen-only version-pin proposal (7.15.0 = first 7.x with PG 14 support), design-gated
per the no-version-changes rule. Coordinator action: recorded in coordination doc at
Batch 5 (task 5.1) alongside the other flags.
