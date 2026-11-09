# Tasks: password-transport-encryption-api

## 1. Crypto foundation (TDD)

- [x] 1.1 Pin `bcprov-jdk18on` in `lumen-bom/pom.xml` (dependencyManagement, ONE
      additive entry — coordination D9) and declare it (compile scope) in
      `lumen-system/pom.xml`. `./mvnw -pl lumen-bom -q install` NOT needed (reactor
      rule) — build via the aggregator throughout.
- [x] 1.2 Generate the FIXED test keypair once (64-char hex scalar D + derived
      130-char hex point) and commit it into lumen-system TEST resources only
      (`src/test/resources/application-test.yml` or the test base's properties):
      `lumen.security.password-crypto.key-id=sm2-test`,
      `lumen.security.password-crypto.sm2-private-key=<test D hex>`. The test vector
      hex literal (C1C3C2 of a known plaintext) is recorded alongside for T1.
- [x] 1.3 Write failing `PasswordCipherHelperTest` (design T1/T2): interop fixed-vector
      round-trip (frontend `sm2.doEncrypt(pt, pub, 1)` hex literal → decrypt → exact
      plaintext); failure modes (non-hex, odd-length, empty, tampered last byte,
      foreign-key ciphertext) → `BusinessException` code `A0400`.
- [x] 1.4 Write failing config fail-fast slice test (design T5): blank/missing
      `sm2-private-key` → application context startup failure with a clear message.
- [x] 1.5 Implement `org.birdbro.lumen.system.service.support.PasswordCipherHelper`
      (`@Component`, `@author birdbro`): `@Value`-injected config; builds one Hutool
      `SmUtil.sm2(privateDHex, null)` at construction (fail-fast on
      absent/invalid/short D — design D2); `decrypt(String cipherHex)` → raw String;
      `getKeyId()` / `getPublicKeyHex()` (derived point, `04`-prefixed 130-char hex).
      Green: 1.3 + 1.4 pass.

## 2. Endpoint + service integration (TDD)

- [x] 2.1 Add `security.permit-all` entry for `GET /api/v1/system/auth-key` in the
      committed `lumen-system/application.yml` (same list `POST /login` already uses;
      framework `AnonymousPathCustomizer` consumes it — no security-code change), plus
      the commented empty-default crypto properties:
      `lumen.security.password-crypto.key-id: sm2-v1`,
      `lumen.security.password-crypto.sm2-private-key: ` (comment: generate per
      coordination D2, values only via env / gitignored application-dev.yml).
- [x] 2.2 Write failing integration tests (house base `AuthIntegrationTestBase`, design
      T3/T4) in `AuthLoginTests` + the user CRUD test class: (a) encrypted correct
      password → `200` + JWT; (b) plaintext `admin123` → `A0400`; (c) tampered
      ciphertext → `A0400`; (d) encrypted wrong password → `A0210`; (e) tokenless
      `GET /api/v1/system/auth-key` → `200` `{keyId, publicKey:04-prefixed 130-char hex}`;
      (f) create-user + admin-reset with encrypted values behave as before, plaintext
      variants → `A0400` and no row change.
- [x] 2.3 Implement `AuthKeyResponse` DTO (`keyId`, `publicKey`) + `AuthController`
      `@GetMapping("/auth-key")` (design D5). Green: 2.2(e).
- [x] 2.4 Wire `PasswordCipherHelper` into `AuthServiceImpl` (decrypt-first ordering —
      format failure short-circuits BEFORE the username query, design Error Handling)
      and `UserInfoServiceImpl` (create + admin reset, decrypt before BCrypt encode);
      additive constructor parameters where the class allows, `@Autowired` field
      precedent otherwise (never-modify rule; backend CLAUDE.md). Existing BCrypt lines
      untouched. Green: 2.2(a)–(d),(f) all pass.
- [x] 2.5 Confirm the copied `LogAspect` still excludes the (now-ciphertext) password
      fields by field name — assertion in the existing operation-log test or a comment
      check in 2.4's review (design D8; no aspect code change expected).

## 3. Verification

- [x] 3.1 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
      && ./mvnw -pl lumen-system -am test -q` from `lumen-sev/` — all green
      (reactor rule; evidence in verify.md).
- [ ] 3.2 Cross-stack interop smoke (with the web change deployed): `pnpm dev` login
      via UI with real backend — network tab shows hex ciphertext in the request body,
      login succeeds; manual plaintext curl → `A0400`. Evidence recorded in verify.md.
      > DONE at curl level 2026-11-09 (evidence below, moved to verify.md): anon
      > /auth-key → 200 {sm2-dev, 04-prefixed 130-char point}; plaintext curl → A0400
      > 密码解密失败; live sm-crypto (node) encrypted admin123 → 200 + JWT + uid 1.
      > UI network-panel part pends the -web change's joint smoke.
