# Proposal: password-transport-encryption-api

## Why

The user's security review (2026-11-09) showed every password-bearing endpoint accepts the
raw password as plaintext JSON (`{"username":"admin","password":"admin123"}`), observable
in DevTools and on any non-TLS hop. Storage was audited CORRECT (BCrypt `$2a$10` hash in
`user_info.password`, log aspect excludes password fields, Redis session carries no hash) —
the gap is transport only. The user ruled: close it with SM2 国密 transport encryption
(hospital deployments may face 商用密码应用安全性评估), following the EIS reference
keyPair precedent, as a static-config-keypair clean cutover. Cross-stack parent:
`openspec/coordination/password-transport-encryption.md` (decisions D1–D9).

## What Changes

- **NEW** `GET /api/v1/system/auth-key` (anonymous, lumen-system `AuthController`):
  returns `{ keyId, publicKey }` where `publicKey` is the 130-char hex uncompressed SM2
  point (`04||x||y`) — sm-crypto's native input format. The keypair is STATIC config:
  `lumen.security.password-crypto.{key-id, sm2-private-key}` (private key = raw scalar D,
  64-char hex; public key derived D·G, never configured — coordination D2/D3). D8 config
  hygiene: private key ships via env / gitignored `application-dev.yml` only.
- **BREAKING** the `password` field of `POST /api/v1/system/login`, the `password` field
  of `POST /api/v1/system/users`, and the `newPassword` field of
  `PUT /api/v1/system/users/{id}/password` now carry SM2 ciphertext (C1C3C2, hex) instead
  of plaintext. Plaintext / non-hex / undecryptable values are rejected with `A0400`
  (用户请求参数错误 — copied `ExceptionEnum` constant REUSED verbatim, house precedent;
  no OPS enum modification). Clean cutover, no dual-track (coordination D5).
- **NEW** `org.birdbro.lumen.system.service.support.PasswordCipherHelper` — wraps one
  Hutool `SM2` instance built from config; decrypt-before-BCrypt step added UPSTREAM of
  the untouched existing BCrypt lines in `AuthServiceImpl.login` /
  `UserInfoServiceImpl` (create + admin reset) (coordination D7).
- **NEW** `lumen-bom` pin: `bcprov-jdk18on` (BouncyCastle, Hutool SM2's provider — the
  ONLY new dependency; jdk18on = JDK 1.8+, Java 11 OK) (coordination D9).
- **NEW** test keypair in lumen-system test resources + interop-format unit tests pinning
  the exact wire format (C1C3C2 hex) on the Java side (coordination D4).
- No DB migration: `user_info.password` keeps storing the BCrypt hash; nothing at rest
  changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `api`: Authentication Endpoints gains the key-distribution endpoint
  (`GET /api/v1/system/auth-key`, anonymous, response shape) and the login request's
  `password` semantic becomes SM2 ciphertext; User Management Endpoints' `password` /
  `newPassword` request fields become ciphertext with `A0400` rejection semantics.
- `auth-rbac`: Login and Token Issuance requirement re-states the login loop with the
  decrypt step (ciphertext in → decrypt → BCrypt match), adds the clean-cutover rejection
  scenarios (plaintext / tampered ciphertext → `A0400`), and documents the anonymous
  key endpoint.

## Impact

- `lumen-sev/lumen-bom`: one managed-dependency pin (`bcprov-jdk18on`).
- `lumen-sev/lumen-system`: `AuthController` (+key endpoint), `AuthServiceImpl` /
  `UserInfoServiceImpl` (decrypt step), new `service/support/PasswordCipherHelper`,
  committed `application.yml` (empty-default properties + comment), test resources
  (test keypair), updated + new tests (`AuthLoginTests`, user CRUD tests).
- `lumen-sev/common`: **untouched** — `ExceptionEnum` A0400 reused verbatim; the
  never-modify rule holds.
- API consumers: only `password-transport-encryption-web` (coordinated sibling change);
  the wire contract is the coordination doc's D3/D4/D5.
- Out of scope: login rate-limiting/lockout (separate future proposal — the actual
  brute-force gap flagged in the same review), the change-own-password endpoint
  (`must_change_password` first-login flow), TLS termination config (deployment form).
