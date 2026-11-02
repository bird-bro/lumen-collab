# Batch 3 Review — auth-rbac-foundation (tasks 3.1-3.3): login loop, first real endpoints, audit identity bridge

> Reviewer verdict persisted verbatim by the coordinator (2026-11-02). Verification
> method: full diff 640b28e..fe03c96 (27 files, all under lumen-sev/lumen-auth),
> cross-checked against copied framework/common sources; suite independently re-run
> (./mvnw -pl lumen-auth -am test -q, JDK 11): MVN_EXIT=0, 20/20 (Application 1,
> AuditIdentity 1, DataLayer 4, Login 5, SchemaMigration 4, SeedMigration 5).

## Spec Compliance - 10/10 PASS

1. Login loop correctness — PASS. AuthServiceImpl.login:67 exact binding order: unknown
   account → A0201 (:72, @TableLogic makes deleted rows "unknown"), enable != 1 → A0202
   (:75), BCrypt mismatch → A0210 (:78). UserLogin built as UserSign{uid, account,
   name, enable} + permissionSet (:86). TokenService trap handled: createToken RETURN
   VALUE used (:89) — the copied method stashes the Redis-key UUID on loginUser.token
   (framework/security/TokenService.java:187-196) and returns the JWT.
2. /auth/me — PASS. Principal from SecurityUtils.getLoginUser() (:101); profile re-read
   from DB (:103); menus via MenuTreeService (:113); perms fresh (:111) — nothing trusts
   the Redis copy. ProfileDto has no password field; login test asserts no "password" key
   in JSON (AuthLoginTests.java:76-78). Mid-session deleted account → A0203 (:106) not NPE.
3. Anonymous access — PASS. web.ignoring() on EXACTLY /api/v1/auth/login
   (AuthWebSecurityConfig.java:30), no wildcards; copied SecurityConfig untouched.
4. UserHeaderBridgeFilter — PASS. Principal-is-UserLogin + user != null + no upstream
   USER-HEADER (gateway wins, :73); URLEncoder symmetric with SecurityUtils' URLDecoder;
   case-insensitive wrapper (:95-134); plain @Component registers after the security
   chain (-100); failures degrade to pass-through (:89-92).
5. Audit identity (3.3) — PASS. RED (create_by null) + GREEN (create_by=1 via physical
   JDBC query) verbatim in report; minimal vehicle, no 4.3 scope creep.
6. UserDetailsServiceImpl + stub removal — PASS. deleted=0 load, UsernameNotFoundException,
   no hash in principal; AuthTestStubConfig deleted, all @Imports removed.
7. Batch-2 handoffs — PASS. No password material in DTOs OR Redis session (UserSign built
   field-by-field; getPassword @JSONField(serialize=false)); deleted=0 on every joined
   table in all three new @Select queries; mapper changes purely additive.
8. application-test.yml — PASS. token.header: Authorization; dummies documented test-only.
9. Tests — PASS. Four RED→GREEN pairs verbatim (RED shape 203-not-404 explained: chain
   precedes dispatch); real-behavior assertions (envelope codes, JWT presence, tree shape,
   perms, DB create_by); 20/20 exit 0 independently reproduced.
10. Verbatim discipline — PASS. git diff -- framework common is empty.

## Strengths

- Pair-3 red/green caught the UUID-as-token integration bug before commit — strongest TDD
  evidence; one-line return-value fix with explanatory comment.
- Integration assumptions verified against actual copied sources; both mechanism decisions
  (WebSecurityCustomizer; bridge filter) are minimal lumen-owned solutions with documented
  trade-offs.
- No hash reaches Redis, responses, or DTOs anywhere; profile truth always DB-fresh.
- Shared-DB discipline in the audit test (millis-suffix, physical-query assertion).

## Issues

**Critical** — none. **Important** — none.

**Minor:**
1. permissionSet runs twice per login (AuthServiceImpl :86/:95) — cheap later refactor.
2. Two separate new BCryptPasswordEncoder() fields (AuthServiceImpl :53, UserInfoServiceImpl
   :23) — cosmetic; stateless/thread-safe.
3. me() does not re-check enable — disabled user keeps session until Redis expiry; add
   check/token-revoke when session-management work lands.
4. web.ignoring() bypasses the copied CorsFilter for /api/v1/auth/login (latent under
   nginx same-origin); servlet-level CORS registration if SPA ever calls cross-origin.
5. A0400 bean-validation path wired but untested — add when a batch touches validation.
6. Duplicate-username create ends at the DB partial-unique index with a generic violation
   envelope — deferred to 4.3 by the brief; 4.3 must ADD the A0111 service check and KEEP
   the index.
7. AuthAuditIdentityTests cleanup skipped on mid-test failure — stray live row possible;
   D3 tolerates; awareness only.

## Assessment

**Approved.** All ten review points check out against both the diff and the copied
machinery; the batch ships the first real endpoints with the JWT return-value subtlety,
anonymous-access boundary, and audit-identity bridge all proven by red/green evidence.
