# password-transport-encryption — Coordination (Cross-Stack Feature)

> Feature: encrypt the password field in transit for every password-bearing endpoint.
> Origin: user security review 2026-11-09 — login posts `{"username":"admin","password":"admin123"}`
> in plaintext JSON. Backend storage was audited CORRECT (BCrypt `$2a$10`, salted, verbatim
> spring-security-crypto; log aspect excludes password fields; Redis session carries no hash),
> so this feature closes the TRANSPORT gap only, following the EIS reference precedent
> (`lumen-reference/code/eis-web/.../utils/http.ts` `passwordSecret` + `GET /userLogin/keyPair`).

## Change Registry

| Change | Stack | Scope |
|:--|:--|:--|
| `password-transport-encryption-api` | lumen-sev | SM2 key endpoint + decrypt-before-BCrypt on 3 password endpoints + bcprov pin + test keypair |
| `password-transport-encryption-web` | lumen-web | sm-crypto client + key fetch/cache util + 3 encrypt call sites |

**Gate**: SATISFIED 2026-11-09 — both changes verify PASS (api: 16/16 scenarios,
125/125 tests; web: 9/9, 249/249) and are archived as
`2026-11-09-password-transport-encryption-{api,web}`; specs synced into api /
auth-rbac / web-auth-flow / web-system-console (sync markers added). Git commit is a
separate track. Outstanding user-side: the browser UI smoke (DevTools wire body),
recorded as a process note in both verify.md credentials. Backend first; the frontend
built against the real endpoint (no mock layer, user ruling 2026-11-05).

## Shared Design (decided 2026-11-09, user-confirmed)

- **D1 — Algorithm: SM2 国密** (user final ruling 2026-11-09, superseding the brainstorm's
  initial RSA-2048 direction after the 等保/密评 question). Rationale: medical deployments
  may face 商用密码应用安全性评估; clean cutover is free pre-production; Hutool (house
  toolkit, user directive 2026-11-03) natively wraps SM2. Wire cost vs RSA: one new BC
  dependency + pinned interop-format tests.
- **D2 — Static config keypair (user ruling).** Properties
  `lumen.security.password-crypto.key-id` + `lumen.security.password-crypto.sm2-private-key`
  (private key = the 32-byte raw scalar D as 64-char hex — a format sm-crypto and Hutool
  share verbatim). The public key is NEVER configured: derived from D via D·G (Hutool
  derives it natively), so the pair cannot drift. D8 sensitive-config rule applies: the
  private key ships via env vars / gitignored `application-dev.yml`, never committed; the
  committed yml carries only the empty default + comment. Rotation = new key-id + new
  private key + service restart (restart accepted as part of the static-key ruling;
  multi-instance deployments share the same configured pair, so no sticky-session hazard).
- **D3 — Key endpoint**: `GET /api/v1/system/auth-key` (anonymous, lumen-system
  `AuthController`) → `{ keyId: "<config key-id>", publicKey: "<130-char hex uncompressed
  point 04||x||y>" }`. The hex point is sm-crypto's native input — the frontend does zero
  format conversion.
- **D4 — Wire format (pinned by tests on BOTH sides)**: ciphertext = SM2 **C1C3C2** mode,
  **hex** encoding: frontend `sm2.doEncrypt(rawPassword, publicKey, 1)` ↔ backend Hutool
  `SmUtil.sm2(privateD, null).decrypt(hex, KeyType.PrivateKey)` (Hutool 5.8 default mode is
  C1C3C2). Interop unit tests on both stacks assert the exact same fixed-vector ciphertext,
  so any library-side drift surfaces as a red test, not a production mystery.
  **Refined during apply (2026-11-09, empirically proven)**: sm-crypto's native output has
  a MARKERLESS C1 (no `04` byte) while BC's SM2Engine requires the standard uncompressed
  point header — the WIRE format is therefore `"04" + sm-crypto doEncrypt(...)`: the
  frontend PREPENDS the marker before sending; the backend consumes the standard encoding
  verbatim. Both fixed vectors (sm-crypto-produced and Hutool-produced) decrypt
  byte-exact; the smoke proved it live (sm-crypto → live service → JWT). The frontend
  change MUST include this prepend.
- **D5 — Clean cutover, no plaintext back door** (user ruling; project not yet in
  production, lumen-web is the only API consumer). Plaintext, non-hex, wrong-length, and
  undecryptable password fields are ALL rejected with envelope code **`A0400`**
  (用户请求参数错误 — the copied `ExceptionEnum` constant REUSED verbatim with a descriptive
  msg, house precedent: org-logo-upload reused `A0400`, permission denial reused `A0301`;
  zero OPS enum modification). A stale/mismatched `keyId` request field, when the client
  sends it, also fails fast with `A0400`.
- **D6 — Scope: all 3 password-bearing endpoints** (user ruling): `POST /api/v1/system/login`
  (`password`), `POST /api/v1/system/users` (`password` initial), `PUT
  /api/v1/system/users/{id}/password` (`newPassword`). DTO field NAMES unchanged — only the
  value semantics change to ciphertext. The future change-own-password endpoint
  (`must_change_password` first-login flow) inherits the behavior for free by calling the
  same helper.
- **D7 — Backend shape**: `org.birdbro.lumen.system.service.support.PasswordCipherHelper`
  (stateless collaborator per the service-package-tidy ruling) wraps one `SM2` instance
  built from config; `AuthServiceImpl.login` / `UserInfoServiceImpl` create+reset call it
  BEFORE the existing BCrypt encode/match lines (those lines are untouched — decrypt is a
  new step upstream). `AuthController` gains `/auth-key`.
- **D8 — Frontend shape**: `pnpm add sm-crypto` (pure JS, Vite-safe); new
  `src/utils/password-cipher.ts` = module-cached `{keyId, publicKey}` + `encryptPassword(raw)`
  (fetch → cache; a failed decrypt surfaces the backend msg inline via the existing
  `ApiError` path — no auto-retry, YAGNI). Call sites: `pages/login/index.tsx` submit,
  `api/users.ts` `createUser` + `resetPassword`.
- **D9 — Dependency**: `lumen-bom` pins `bcprov-jdk18on` (BouncyCastle — the ONLY new
  dependency, required on the classpath for Hutool's SM2; jdk18on = JDK 1.8+, Java 11 OK).
  Exact version pinned inside the backend change's lumen-bom edit; nothing else moves.

## Decisions Log

- 2026-11-09: algorithm = SM2 国密 (user final ruling after RSA-2048 vs SM2 trade-off;
  driver: 等保/密评 future-proofing for hospital deployments, Hutool native support).
- 2026-11-09: key management = static config keypair (user ruling, deviating from the
  one-time-Redis recommendation — ops predictability favored; D8 config hygiene applies).
- 2026-11-09: scope = all 3 password endpoints (user ruling — no patch-style follow-ups).
- 2026-11-09: clean cutover, plaintext rejected (user ruling; no dual-track).
- 2026-11-09: error code = `A0400` reused verbatim (no new `ExceptionEnum` constant — OPS
  file stays unmodified, consistent with every prior lumen change).

## Gate

Feature done = BOTH registered changes verify PASS → archive each → specs synced
(api / auth-rbac / errors / web-auth-flow / web-system-console).
