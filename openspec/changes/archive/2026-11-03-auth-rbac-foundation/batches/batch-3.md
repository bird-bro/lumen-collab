# Batch 3 Brief: Login loop - the first real endpoint (tasks 3.1-3.3)

## Goal

`POST /api/v1/auth/login` (JWT issuance through the copied `TokenService`) and
`GET /api/v1/auth/me` (profile + menu tree + perms set), anonymous access for login,
security chain live, and audit identity (`create_by`) proven end-to-end through a real
write endpoint.

## Task text (tasks.md 3.1-3.3)

- **3.1** `AuthController`: `POST /api/v1/auth/login` (BCrypt verify →
  `TokenService.createToken` → envelope carries JWT + profile) and `GET /api/v1/auth/me`
  (token → profile + menu tree + perms set). Red tests first (TestRestTemplate against the
  real server context): valid credentials → `"200"`+token; wrong credentials → `A0210`;
  no token on `/auth/me` → `"203"` envelope rejection (copied
  `AuthenticationEntryPointImpl` semantic, user ruling 2026-11-02). Verify: all green.
- **3.2** Anonymous access: `/auth/login` exempt from token (mechanism per the copied
  implementation's reality), all other endpoints filter-guarded. Verify: the no-token
  `/auth/me` rejection test IS this proof.
- **3.3** Audit identity proof: with the admin token call a write endpoint (create user) →
  assert `create_by` = admin id (MetaObjectConfig fill proven against the real DB).

## Copied-machine facts (verified by coordinator - do not re-derive)

- `framework/security/TokenService.createToken(UserLogin)`: needs `${token.header}`,
  `${token.secret}`, `${token.expireTime}` (test dummies exist in application-test.yml -
  CHANGE `token.header` to `Authorization`, the real client header name); stores
  `UserLogin` in Redis (`login_tokens:<uuid>`, fastjson2 with WriteClassName; value
  deserializes back as a JSONObject whose toString re-parses - proven OPS semantics).
  Clients send `Authorization: Bearer <jwt>` (Constants.TOKEN_PREFIX = "Bearer ").
- `framework/config/SecurityConfig`: `anyRequest().authenticated()`, anonymous list is
  hardcoded OPS paths; constructor-injects `UserDetailsService` (concrete need) +
  `AuthenticationEntryPointImpl` + `LogoutSuccessHandlerImpl` + `JwtAuthenticationTokenFilter`
  + `CorsFilter` (CorsFilter bean exists in framework WebMvcConfig). JWT filter runs inside
  the chain and populates SecurityContext with the `UserLogin` principal.
- `framework/config/MetaObjectConfig.insertFill`: fills `createBy` from
  `SecurityUtils.getUserHeader()` - which reads the **`USER-HEADER` HTTP request header**
  (URL-encoded JSON of `org.birdbro.common.core.pojo.user.UserHeader`; gateway-injected in
  OPS). lumen-auth has no gateway → a lumen-owned bridge filter must derive USER-HEADER
  from the JWT-authenticated principal (this makes the spec sentence "token populates the
  security context with the identity used by MetaObjectConfig" literally true).
- `framework/global/GlobalResponseAdvice` wraps any non-R return into `code:"200"`;
  `GlobalExceptionHandler` turns `BusinessException(ExceptionEnum.ERROR_A0210)` into
  `code:"A0210"` HTTP 200.
- `UserLogin` (copied POJO): userId, user (UserSign: uid/account/name/enable/...),
  permissions Set<String>. `UserSign.account` = the username.

## Binding design (coordinator-decided under design D6)

1. **Anonymous login** - `WebSecurityCustomizer` bean (new code, framework untouched):
   `org.birdbro.lumen.auth.config.AuthWebSecurityConfig` with
   `web.ignoring().antMatchers("/api/v1/auth/login")`. The copied SecurityConfig stays the
   active chain for everything else. (Design D6 anticipated "apply 时核对": the copied
   mechanism is a hardcoded permitAll list we cannot extend without editing verbatim code;
   web.ignoring() is the minimal lumen-side equivalent. Record in the task evidence.)
2. **UserDetailsService ships for real**: `org.birdbro.lumen.auth.service.impl.UserDetailsServiceImpl`
   - load UserInfo by username (deleted=0) → `UserLogin(userId, UserSign{uid, account=
   username, name, enable}, perms)`; unknown → `UsernameNotFoundException`. DELETE
   `AuthTestStubConfig.java` and remove its `@Import` from `AuthApplicationTests`
   (its javadoc documents this exact deferral - the deferral ends now).
3. **UserHeaderBridgeFilter**: `org.birdbro.lumen.auth.filter.UserHeaderBridgeFilter`
   (plain `@Component OncePerRequestFilter`; Boot registers it after the security chain):
   if `SecurityUtils.getAuthentication()` principal is a `UserLogin` with a non-null
   `getUser()` AND the incoming request has NO `USER-HEADER` header yet (upstream gateway
   wins when one exists), build `UserHeader{uid=userId, name=user.getName()}`, JSON via
   fastjson2, URL-encode (SecurityUtils decodes with URLDecoder), and continue the chain
   with an `HttpServletRequestWrapper` overriding `getHeader`/`getHeaders`/`getHeaderNames`
   for USER-HEADER. Otherwise pass through unwrapped.
4. **AuthService** (`org.birdbro.lumen.auth.service` + impl):
   - `login(username, rawPassword)`: load by username (deleted=0) → not found:
     `BusinessException(ERROR_A0201)`; enable=0: `ERROR_A0202`; BCrypt mismatch
     (`BCryptPasswordEncoder.matches`): `ERROR_A0210`. Then build UserLogin with the
     permission set, call `TokenService.createToken`, map to response DTO.
   - `permissionSet(userId)`: DISTINCT menu_info.perms over
     user_role→role_menu→menu_info (perms non-empty) - a `@Select` annotated method on
     `RoleMenuMapper` (matches the @MapperScan) or an equivalent wrapper query.
   - `menuTree(userId)`: same joins → List<MenuInfo> → `MenuTreeService` (Batch 2).
   - `me()`: principal from `SecurityUtils.getLoginUser()` → fresh profile/menus/perms
     from DB (never trust the Redis copy for profile data).
5. **DTOs** (`org.birdbro.lumen.auth.dto`): `LoginRequest{username, password}` (bean
   validation), `ProfileDto{uid, username, name, enable, mustChangePassword, roles
   List<String>}`, `LoginResponse{token, profile, menus List<MenuTree>, perms
   Set<String>}`, `MeResponse{profile, menus, perms}`. Controllers return the DTO (advice
   wraps → `"200"`).
6. **Minimal create-user endpoint as the 3.3 vehicle** (the full user endpoint is 4.3 -
   do NOT build list/change-password/enable/duplicate-rejection here):
   `UserInfoService.createUser(CreateUserRequest{username, password, name})`:
   BCrypt-encode the password, insert via UserInfoMapper (audit fill happens by
   MetaObjectConfig). Expose `POST /api/v1/auth/users` on the same AuthController or a
   `UserInfoController` - pick the layout you'd defend at review (department CRUD lands in
   Batch 4; consistency matters). must_change_password defaults 0, enable defaults 1.

## TDD sequence (red first)

1. Login tests in a concrete test class extending `AuthIntegrationTestBase`
   (TestRestTemplate): valid admin/admin123 → HTTP 200 + `code:"200"` + non-empty
   `data.token` + `data.profile.uid=1`; wrong password → `code:"A0210"`, `data` null; unknown
   user → `code:"A0201"`. Run RED (no controller → 404), implement, GREEN.
2. No-token `/auth/me` → `code:"203"` (assert msg mentions 认证失败). RED (currently 404
   because no controller exists - to make the red meaningful for 3.2, run this test AFTER
   the controller exists but BEFORE the WebSecurityCustomizer bean: the observed failure is
   the login-path-404-while-me-203... simpler: write all tests, implement controller +
   service, run: login-without-token-red (anonymous rule missing → 203 on login), then add
   the WebSecurityCustomizer → GREEN. Capture both runs as your 3.2 red/green evidence.)
3. `/auth/me` WITH the admin token (`Authorization: Bearer <jwt>` from a real login call)
   → `code:"200"`, profile.uid=1, menus contain the seeded tree, perms contains
   `radiology:report:audit`.
4. Audit identity (3.3): login → POST /api/v1/auth/users with the token (unique username
   `audit_user_<timestampMillis>`) → then query `user_info` through UserInfoMapper (or
   DataSource) and assert the new row's `create_by` = 1 AND `create_time` non-null. RED
   first: run with the filter NOT yet registered (create_by null) - capture - then add
   UserHeaderBridgeFilter → GREEN. (This red/green pair is the proof that the bridge is
   what carries the identity - keep both excerpts in the report.)

Run: `cd lumen-sev && ./mvnw -pl lumen-auth -am test -q`.

## Hygiene

- The Redis state from tests accumulates on the shared test server - acceptable by design
  (D3); do not add cleanup code beyond what the tests already do.
- application-test.yml: update `token.header` to `Authorization`; keep the secret/expire
  dummies (documented as test-only). Production values still come from env/Nacos later.
- NEVER edit framework/common. If something in the copied code genuinely blocks you,
  STOP and report NEEDS_CONTEXT with the exact class/line.

## Commit

`✨ feat(sev): Batch 3 - login loop, first real endpoints, audit identity bridge (auth-rbac-foundation 3.1-3.3)`

## Report

Write to `openspec/sdd/reports/auth-b3-implementer.md`: what you implemented, TDD
evidence (every RED + GREEN pair above), files changed, self-review, concerns (include
the WebSecurityCustomizer rationale + bridge-filter rationale the tasks ask to record).
