# Batch 3 Implementer Report - Login loop, first real endpoints, audit identity bridge (tasks 3.1-3.3)

Status: **DONE** - all four red/green pairs captured, final
`./mvnw -pl lumen-auth -am test -q` exit 0 with 20/20 tests (Batch 1-2's 14 + Batch 3's
6 new), committed as `fe03c96`. Framework/common untouched (`git diff --stat -- lumen-sev
lumen-sev/common` = 0 lines).

## What was implemented

### 3.1 - Login loop endpoints (first real REST endpoints of the platform)

- `controller/AuthController` (`/api/v1/auth`): `POST /login`
  (`@Valid @RequestBody LoginRequest` -> `AuthService.login`) and `GET /me`
  (`AuthService.me`). Returns the DTOs; the copied `GlobalResponseAdvice` wraps into
  `code:"200"`, the copied `GlobalExceptionHandler` renders the A-class envelopes.
- `service/AuthService` + `impl/AuthServiceImpl`: BCrypt verify against the
  `user_info` hash (lookup by username among live rows - `@TableLogic` adds
  `deleted = 0`), then the copied `TokenService.createToken` (JWT + Redis session).
  Error mapping: unknown account -> `A0201`, `enable != 1` -> `A0202`, BCrypt mismatch
  -> `A0210` (all via the copied `ExceptionEnum`/`BusinessException`).
- **`TokenService` subtlety honored (and it caught me)**: `createToken(UserLogin)`
  RETURNS the signed JWT while stashing a UUID on `loginUser.token` (the Redis session
  key). The response must carry the RETURN value - see TDD pair 3.
- `service/impl/UserDetailsServiceImpl`: the REAL `UserDetailsService` the copied
  `SecurityConfig` constructor-injects - loads `UserInfo` by username (deleted=0),
  maps to `UserLogin(userId, UserSign{uid, account=username, name, enable}, perms)`
  WITHOUT the password hash; unknown -> `UsernameNotFoundException`. `AuthTestStubConfig`
  DELETED and its `@Import` removed from all four Batch 1-2 test classes (the stub's
  javadoc-documented deferral ended here).
- Mapper queries (ADDITIVE methods, no existing signature touched): 
  `RoleMenuMapper.selectPermsByUserId` (`user_role -> role_menu -> menu_info`, DISTINCT
  non-blank perms), `MenuInfoMapper.selectMenusByUserId` (same joins -> `MenuInfo`
  rows -> `MenuTreeService.build`), `RoleMapper.selectRoleNamesByUserId`
  (`user_role -> role` for the profile). **`deleted = 0` enforced on EVERY joined
  table** (Batch-2 review handoff).
- DTOs (`dto/`): `LoginRequest` (@NotBlank x2), `ProfileDto{uid, username, name, enable,
  mustChangePassword, roles}` - NO password field (Batch-2 handoff: hash never
  serializes), `LoginResponse{token, profile, menus, perms}`, `MeResponse{profile,
  menus, perms}`. Menu tree = the copied `MenuTree` POJO via Batch 2's
  `MenuTreeService.build`.
- `pom.xml`: added `spring-boot-starter-validation` - common brings only
  `validation-api` (no implementation), so `@NotBlank` would be inert otherwise.
- `application-test.yml`: `token.header: Authorization` (the real client header name;
  secret/expireTime stay documented test-only dummies).

### 3.2 - Anonymous login + guarded everything-else

- `config/AuthWebSecurityConfig`: a `WebSecurityCustomizer` bean with
  `web.ignoring().antMatchers("/api/v1/auth/login")` - rationale below (Concerns 1).
  The copied `SecurityConfig` remains the active chain for everything else.
- Proof: the no-token `/auth/me` rejection test (copied `AuthenticationEntryPointImpl`
  envelope `code:"203"`, HTTP 200, msg contains 认证失败) is green in EVERY run -
  including the very first RED run with zero production code.

### 3.3 - Audit identity proof (create-user vehicle)

- `service/UserInfoService` + `impl/UserInfoServiceImpl`: minimal
  `createUser(CreateUserRequest)` - BCrypt-encode the password, insert via
  `UserInfoMapper`; `must_change_password`/`enable` fall to the V1 defaults (0/1).
  `create_by` deliberately NOT set here - the copied `MetaObjectConfig` fill owns it.
- `controller/UserInfoController` (`POST /api/v1/auth/users`): separate from the
  token-loop `AuthController` (resource management, not authentication), same
  `/api/v1/auth` namespace; Batch 4's department/unit/role CRUD follows the same
  per-resource layout.
- `filter/UserHeaderBridgeFilter`: plain `@Component OncePerRequestFilter` - Boot
  registers it AFTER the security chain, where the copied JWT filter has already set
  the `UserLogin` principal. Builds `UserHeader{uid=userId, name=user.getName()}`,
  fastjson2 JSON, URL-encoded (symmetric with `SecurityUtils.getUserHeader`'s
  URLDecoder), and continues the chain wrapped - rationale below (Concerns 2).

## TDD evidence

All runs: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`,
`cd lumen-sev && ./mvnw -pl lumen-auth -am test -q` (reactor-only).

### Pair 1 - 3.1 login tests, RED (tests written first; stub-context; nothing implemented)

`-Dtest=AuthLoginTests` -> MVN_EXIT=1:
```
[ERROR] Tests run: 5, Failures: 4, Errors: 0, Skipped: 0  <<< FAILURE! - in org.birdbro.lumen.auth.AuthLoginTests
successful login must carry the success envelope code ==> expected: <200> but was: <203>
wrong credentials must map to the A0210 code ==> expected: <A0210> but was: <203>
an unknown account must map to the A0201 code ==> expected: <A0201> but was: <203>
the token source (login) must succeed first ==> expected: <200> but was: <203>
```
(The brief's parenthetical guessed "404"; the machine's actual RED shape is `203` - the
copied security chain rejects before the dispatcher is ever reached, so no controller
can 404. Evidence recorded as-is. `meWithoutTokenRejectedWith203Envelope` PASSED in
this run already - the standing 3.2 proof is green from the first run on.)

Also captured in this phase (pre-fix RED of the test itself): my first draft asserted
`data` explicitly null; the copied entry point writes the 203 envelope with fastjson2
`JSON.toJSONString`, which OMITS null fields - assertion corrected to
`!hasNonNull("data")` (absent-or-null both acceptable; the A-class rejections from
Jackson do carry explicit `"data":null`).

### Pair 2 - 3.2 anonymous rule, RED (controller + service + real UserDetailsService live; NO WebSecurityCustomizer)

`-Dtest=AuthLoginTests` -> MVN_EXIT=1 - context loads WITHOUT the stub (the real
`UserDetailsServiceImpl` satisfies the copied `SecurityConfig` injection), yet login is
still machine-blocked:
```
[ERROR] Tests run: 5, Failures: 4 ... successful login must carry the success envelope code ==> expected: <200> but was: <203>
wrong credentials must map to the A0210 code ==> expected: <A0210> but was: <203>
an unknown account must map to the A0201 code ==> expected: <A0201> but was: <203>
```
(`meWithoutToken` passes - "all other endpoints filter-guarded" proven in the same run.)

### Pair 2/1/3 - GREEN (WebSecurityCustomizer added) - and a real bug the pair-3 test caught

First green run: login/A0210/A0201 flipped green, but `meWithAdminToken` failed:
```
AuthLoginTests.meWithAdminTokenReturnsProfileMenusAndPerms:124 a valid admin token must open /auth/me ==> expected: <200> but was: <203>
o.b.framework.security.TokenService : Token格式非法: JWT strings must contain exactly 2 period characters. Found: 0
```
Zero periods = the "token" sent to /me was a UUID: `AuthServiceImpl` had set
`response.setToken(loginUser.getToken())` - but `TokenService.createToken` stashes the
REDIS SESSION KEY on loginUser and RETURNS the JWT. Fixed to use the return value
(`String jwt = tokenService.createToken(loginUser)`). This is exactly what pair 3
exists to catch - a login response that "looks" fine (non-empty string) but can never
validate. Re-run -> MVN_EXIT=0, 5/5 green (pair 1 GREEN, pair 2 GREEN, pair 3 GREEN:
me-with-token returns profile.uid=1, the seeded tree (roots 1/3/4/5, menu 2 under 1,
button 6 under 3), and perms containing `system:user:list` + `radiology:report:audit`).

### Pair 4 - 3.3 audit identity, RED (create-user endpoint live; bridge filter NOT yet registered)

`-Dtest=AuthAuditIdentityTests` -> MVN_EXIT=1:
```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0  <<< FAILURE! - in org.birdbro.lumen.auth.AuthAuditIdentityTests
create_by must be the admin id 1 - the JWT principal must reach MetaObjectConfig (bridge filter) and fill the audit column; actual: null ==> expected: <1> but was: <null>
```
The row WAS created (code 200, id backfilled, `create_time` non-null from the DB
default, `deleted = 0`) - only the audit identity is missing. This red is the proof
that the BRIDGE is what carries the identity into `MetaObjectConfig`.

### Pair 4 - GREEN (UserHeaderBridgeFilter registered)

Same command -> MVN_EXIT=0: `create_by = 1` (admin id), `create_time` non-null,
`deleted = 0`, then the test logically hides its row (shared-DB cleanup).

### Final full reactor run

```
MVN_EXIT=0
AuthApplicationTests 1/1 · AuthLoginTests 5/5 · AuthDataLayerTests 4/4 ·
AuthAuditIdentityTests 1/1 · AuthSchemaMigrationTests 4/4 · AuthSeedMigrationTests 5/5
= 20 green
```

## Files changed (commit fe03c96, 27 files, +1095/-55)

New production (14), all under `lumen-sev/lumen-auth/src/main/java/org/birdbro/lumen/auth/`:
`config/AuthWebSecurityConfig.java`, `controller/AuthController.java`,
`controller/UserInfoController.java`, `dto/{LoginRequest,ProfileDto,LoginResponse,
MeResponse,CreateUserRequest}.java`, `filter/UserHeaderBridgeFilter.java`,
`service/{AuthService,UserInfoService}.java`,
`service/impl/{AuthServiceImpl,UserDetailsServiceImpl,UserInfoServiceImpl}.java`.
New tests (2): `AuthLoginTests.java`, `AuthAuditIdentityTests.java`.
Modified (10): `pom.xml` (validation starter), `AuthApplication.java` (javadoc only -
"no controllers" sentence now true-to-fact), `mapper/{MenuInfoMapper,RoleMapper,
RoleMenuMapper}.java` (additive @Select queries), test files x4 (`@Import(AuthTestStubConfig)`
removal), `src/test/resources/application-test.yml` (token.header -> Authorization).
Deleted (1): `src/test/java/org/birdbro/lumen/auth/AuthTestStubConfig.java`.
Nothing outside `lumen-sev/lumen-auth`; `@author birdbro` in every new file; comments
English (Chinese only as copied-machine strings/product display data).

## Self-review findings

1. **RED shapes vs the brief's parentheticals**: "no controller -> 404" was actually
   `203` (the chain precedes dispatch); the brief's own fallback wording ("simpler:
   write all tests ... run login-without-token-red") is what I executed. Evidence
   captured as observed.
2. **The pair-3 test caught a genuine integration bug** (UUID-as-token) - strongest TDD
   artifact of the batch; the login tests alone would have shipped it.
3. **fastjson2-vs-Jackson null rendering**: the copied entry point (fastjson2) omits
   `"data":null`; the copied exception handler (Jackson, via MVC) keeps it. Tests
   assert `!hasNonNull("data")` to hold both truths. Worth remembering for the frontend
   envelope contract.
4. **Mapper changes purely additive** (only an import line moved for grouping; no
   existing method touched - overloading/never-modify rule respected).
5. **Shared-DB discipline**: audit row uses `audit_user_<millis>`, asserted via
   physical query, then logical-deleted; login tests leave only Redis sessions
   (`login_tokens:<uuid>`, by design D3).
6. **`permissionSet` runs twice on login** (once into `UserLogin` for Redis, once into
   the response) - two cheap indexed queries at this scale; noted for a possible
   single-fetch refactor later. Not optimized now to keep the copied
   `UserLogin(..., perms)` constructor semantics verbatim-friendly.
7. **`me()` guards a mid-session deleted account** with `A0203` (账户已作废) instead of
   NPE->500; judgment call recorded here (the Redis copy is identity, the DB is truth).
8. One transient environment event: one run stalled ~15 min during context refresh
   (Druid discarded the Flyway connection - network stall to the remote test server);
   it did NOT recur in any later run (all subsequent runs ~1 s). No code action taken;
   watch-item only.

## Issues or concerns

1. **WebSecurityCustomizer rationale (recorded evidence, task 3.2)**: the copied
   `SecurityConfig` hardcodes its OPS anonymous list (`/api/sign/in`,
   `/api/open/**`, ...) - extending it would mean editing verbatim machinery, which the
   change forbids. `AuthWebSecurityConfig`'s `web.ignoring().antMatchers("/api/v1/auth/
   login")` is the minimal lumen-owned equivalent (exact path, no wildcards). Known
   trade-offs, accepted and documented in the class javadoc: (a) Spring Security logs
   its "use permitAll instead" warning; (b) the login path bypasses the WHOLE chain,
   including the copied `CorsFilter` - a cross-origin browser preflight on /login would
   fail CORS. The nginx unified-entry deployment is same-origin, so this is latent; if
   the SPA ever calls lumen-auth cross-origin directly, a servlet-level CORS
   registration becomes a small follow-up.
2. **Bridge-filter rationale (recorded evidence, task 3.3)**: the copied
   `MetaObjectConfig.insertFill` sources `create_by` from
   `SecurityUtils.getUserHeader()`, which reads the `USER-HEADER` request header -
   gateway-injected in OPS, and lumen has no gateway. The lumen-owned bridge derives it
   from the JWT principal, making the spec sentence "token populates the security
   context with the identity used by MetaObjectConfig" literally true (and the red/
   green pair the proof). Mechanics: plain `@Component OncePerRequestFilter` (Boot
   registers it after the security chain); gateway-wins rule (an upstream USER-HEADER
   passes untouched); case-insensitive header name; anonymous/no-UserSign requests pass
   unwrapped and the fill degrades to no-op exactly as in Batch 2's mapper tests.
3. `application-test.yml` now carries the REAL client header name (`Authorization`)
   while secret/expireTime remain documented test-only dummies; production values still
   come from env/Nacos later (unchanged from the skeleton contract).
4. Bean validation (A0400 path through the copied handler) is wired but not covered by
   a dedicated test - the four brief-defined pairs are the batch's required evidence.
5. The frontend can now mock from the spec: login envelope carries
   `data.{token, profile{uid, username, name, enable, mustChangePassword, roles},
   menus, perms}`; `/auth/me` carries `data.{profile, menus, perms}`; rejections:
   `A0201/A0202/A0210` (login) and `203` (tokenless/protected).
