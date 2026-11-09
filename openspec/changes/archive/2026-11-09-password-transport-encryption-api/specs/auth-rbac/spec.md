# auth-rbac Delta: password-transport-encryption-api

## MODIFIED Requirements

### Requirement: Login and Token Issuance

`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.
The login request's `password` field SHALL arrive as SM2 ciphertext (C1C3C2, hex —
coordination password-transport-encryption D4) and the service SHALL decrypt it with the
static configured key BEFORE the BCrypt match; the decrypted value is the only input to
the existing BCrypt verification, which is unchanged. A `password` field that is not
decryptable ciphertext (plaintext, non-hex, wrong length, wrong key) SHALL be rejected
with envelope code `"A0400"` (用户请求参数错误, the copied `ExceptionEnum` constant
reused verbatim — no OPS enum modification) and SHALL NOT be counted as a wrong-password
attempt (coordination D5). There is NO plaintext acceptance path — clean cutover, no
dual-track mode flag. `lumen-system` SHALL additionally expose `GET
/api/v1/system/auth-key` (anonymous) returning `{ keyId, publicKey }` per the api
capability's Authentication Endpoints requirement; the key pair is static configuration
(`lumen.security.password-crypto.{key-id, sm2-private-key}`, private key = raw scalar D
hex, public key derived, sensitive-config hygiene per coordination D2).
The cached login session SHALL carry the user's permission set (the `perms` strings
assembled from the granted menu rows) so every authenticated request can be authorized
server-side.

#### Scenario: Successful login

- **WHEN** a client posts valid credentials of the seeded admin — the `password` field
  being SM2 ciphertext of the raw password encrypted under the distributed `publicKey`
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`

#### Scenario: Invalid credentials

- **WHEN** a client posts a wrong password (correctly SM2-encrypted)
- **THEN** the response carries an `A`-class code (e.g. `"A0210"` 用户密码错误) with
  `data` null
- **AND** no token is issued

#### Scenario: Plaintext password rejected (clean cutover)

- **WHEN** a client posts `{"username":"admin","password":"admin123"}` — any value that
  is not SM2 ciphertext
- **THEN** the response is `code: "A0400"` with a descriptive `msg` and `data` null
- **AND** no token is issued and the account lookup outcome is not disclosed

#### Scenario: Tampered ciphertext rejected

- **WHEN** a client posts SM2 ciphertext that was altered after encryption (or encrypted
  under a foreign key)
- **THEN** the response is `code: "A0400"` with `data` null
- **AND** no token is issued

#### Scenario: Key endpoint is anonymous

- **WHEN** a client requests `GET /api/v1/system/auth-key` without a token
- **THEN** the envelope answers `code: "200"` with `data = { keyId, publicKey }` where
  `publicKey` is the 130-char hex uncompressed SM2 point derived from the configured
  private scalar

#### Scenario: Session carries the permission set

- **WHEN** a user logs in
- **THEN** the cached session holds that user's permission set exactly as the `/me`
  response reports it
- **AND** a session created before the permission set existed is treated as empty
  (deny) rather than trusted
