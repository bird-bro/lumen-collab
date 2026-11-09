# Design: password-transport-encryption-api

## Reference Lineage Map

| lumen artifact | reference source |
|:--|:--|
| `GET /api/v1/system/auth-key` (key distribution) | EIS `GET /userLogin/keyPair` (`lumen-reference/code/eis-web/.../api/login.ts` + backend keyPair endpoint) — modernized: hex point instead of modulus/exponent, sm-crypto-native format |
| frontend encrypt call (sibling change) | EIS `utils/http.ts` `passwordSecret` — modernized: sm-crypto instead of the legacy `window.RSAUtils` block cipher |
| `A0400` rejection constant | copied `common/ExceptionEnum.ERROR_A0400` — REUSED verbatim (house precedent: org-logo-upload `A0400`, permission denial `A0301`) |
| decrypt helper placement | `service/support/` stateless-collaborator ruling (naming.md, service-package-tidy 2026-11-06) — `MenuTreeService` / dict `*Helper` precedent |

OPS has no password-transport encryption in its copied login path; this is a lumen-new
security feature, additive-first (no OPS file modified — see common untouched below).

## Decisions

- **D1 SM2, C1C3C2, hex (coordination D1/D4)**: ciphertext = `sm2.doEncrypt(msg, pub, 1)`
  on the frontend ↔ Hutool `SmUtil.sm2(privateDHex, null).decrypt(hex, KeyType.PrivateKey)`
  on the backend. Hutool 5.8.20's SM2 default mode IS C1C3C2 (GM/T 0003-2012 ordering), so
  both sides interop with zero mode plumbing — but the interop unit test pins a fixed
  vector anyway (see Testing) so a future Hutool/sm-crypto bump cannot silently flip the
  format. Hex (not Base64) is sm-crypto's native output — no JS-side encoding step.
- **D2 static config keypair (user ruling; coordination D2)**: properties
  `lumen.security.password-crypto.key-id` (default `sm2-v1`) and
  `lumen.security.password-crypto.sm2-private-key` (raw scalar D, 64-char hex). The
  committed `application.yml` ships EMPTY defaults + a generation comment; real values
  live in gitignored `application-dev.yml` / env vars (D8 ruling). Public key derived
  D·G inside `PasswordCipherHelper` (Hutool derives when constructed private-only) —
  a separately configured public key would be a drift hazard for zero benefit. The helper
  FAILS FAST at bean construction when the private key is absent/invalid (a service that
  cannot decrypt passwords must not boot half-working; the dev guide documents generating
  a keypair).
- **D3 decrypt step placement (coordination D7)**: `PasswordCipherHelper.decrypt(cipherHex)`
  returns the raw password; call sites are EXACTLY three: `AuthServiceImpl.login`,
  `UserInfoServiceImpl` create, `UserInfoServiceImpl` admin reset. The existing BCrypt
  `matches`/`encode` lines are untouched — decryption is a new step upstream, so the
  never-modify rule holds for the constructor signatures and public methods (the
  `String rawPassword` parameter's SEMANTIC changes, documented in javadoc; no overload
  needed since the parameter name/type is unchanged and this is the same logical input).
- **D4 A0400 reused verbatim (coordination D5)**: decrypt failures throw
  `BusinessException(ExceptionEnum.ERROR_A0400)` with msg naming the cause class
  (e.g. `密码解密失败，请刷新页面后重试`). NOT `A0210` (wrong password) — a transport-format
  failure is a request-parameter failure, and conflating it with wrong-password semantics
  would corrupt future lockout counters. No new `ExceptionEnum` constant: OPS file stays
  byte-identical.
- **D5 /auth-key lives in AuthController (coordination D3/D7)**: `@GetMapping("/auth-key")`
  under the existing `/api/v1/system` mapping; anonymous via the SAME
  `security.permit-all` service-declared config the login endpoint already uses
  (framework `AnonymousPathCustomizer`, lumen adaptation (6)) — one config line, no
  security-code changes. Response DTO `AuthKeyResponse(keyId, publicKey)` (two String
  fields) wrapped by `GlobalResponseAdvice`.
- **D6 dependency (coordination D9)**: `lumen-bom` gains ONE pin `bcprov-jdk18on`
  (BouncyCastle provider; jdk18on artifact targets JDK 1.8+, so the Java 11 train is
  served). `lumen-system` declares it (runtime scope is NOT used — Hutool's SM2 classes
  touch BC types directly, and tests need the provider too). No other module, no version
  bump anywhere else — the OPS managed set stays 1:1 plus this one additive pin.
- **D7 no keyId round-trip enforcement on requests (coordination D5 note)**: the request
  body carries ONLY the ciphertext (DTO field sets unchanged). The server holds exactly
  one configured key, so a client that fetched the current key cannot target a wrong key;
  keyId mismatch is only possible after a rotation-with-stale-cache, which the A0400
  decrypt failure already covers with a refresh-and-retry msg. Request-side keyId would
  be ceremony for a single-key deployment (recorded for the future multi-key rotation
  form).
- **D8 logging**: the copied `LogAspect.EXCLUDE_PROPERTIES` already excludes
  `password`/`newPassword` — ciphertexts still flow through that exclusion (field-name
  based), so neither plaintext nor ciphertext reaches `log_operation`. No aspect change.

## Error Handling

| case | code | msg pattern |
|:--|:--|:--|
| password field missing/blank | (bean validation, existing) | existing `@NotBlank` path |
| non-hex / odd-length / not 04-prefixed hex | `A0400` | 密码解密失败，请刷新页面后重试 |
| decrypt throws (wrong key, tampered) | `A0400` | same as above |
| helper misconfigured at boot (missing/short private key) | fail-fast `IllegalStateException` on startup | — (not a request path) |
| everything else (unknown account A0201, disabled A0202, wrong password A0210) | unchanged | existing semantics, now fed by the DECRYPTED value |

The A0400 path must not reveal WHETHER the account exists — decrypt precedes account
lookup ordering-wise per endpoint: login decrypts FIRST (a format failure short-circuits
before the username query, so plaintext probing cannot enumerate accounts).

## Testing (house integration harness, TDD)

- **T1 interop vector (unit)**: `PasswordCipherHelperTest` — a FIXED test keypair (D +
  derived point, committed in test resources ONLY); encrypts a known plaintext with the
  same fixed vector the frontend test uses (`sm2.doEncrypt(pt, pub, 1)` output recorded as
  a hex literal) and asserts `decrypt` returns the plaintext byte-exact. This is the
  C1C3C2-hex pin of coordination D4.
- **T2 helper failure modes (unit)**: non-hex, odd-length, empty, tampered (last byte
  flipped), foreign-key ciphertext → all `BusinessException` with code `A0400`.
- **T3 login loop (integration, existing harness)**: `AuthLoginTests` gains scenarios —
  (a) encrypted correct password → 200 + JWT (helper used to build the request body);
  (b) plaintext `admin123` → `A0400`; (c) tampered ciphertext → `A0400`; (d) encrypted
  wrong password → still `A0210` (decryption success + mismatch unchanged); (e) no token
  `GET /auth-key` → 200 with 04-prefixed 130-char hex publicKey + keyId.
- **T4 user CRUD loop**: create-user and admin-reset tests switch their request building
  to encrypted values; add plaintext-rejected assertions (no row created).
- **T5 config fail-fast**: test context with a missing private key property →
  application context fails to start (assert the guard), via a dedicated slice test.
- Full suite stays green: `./mvnw test -q` from `lumen-sev/` (zero-infrastructure
  harness, JDK 11, reactor build).
