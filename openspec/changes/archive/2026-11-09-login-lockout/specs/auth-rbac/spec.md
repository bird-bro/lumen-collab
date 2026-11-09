# auth-rbac Delta: login-lockout

## MODIFIED Requirements

### Requirement: Login and Token Issuance

`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.
The login request's `password` field SHALL arrive as SM2 ciphertext (C1C3C2, hex —
coordination password-transport-encryption D4) and the service SHALL decrypt it with the
static configured key BEFORE the BCrypt match; the decrypted value is the only input to
the existing BCrypt verification. A `password` field that is not decryptable ciphertext
SHALL be rejected with envelope code `"A0400"` (the copied `ExceptionEnum` constant
reused verbatim) and SHALL NOT affect any lockout counter. There is NO plaintext
acceptance path. `lumen-system` SHALL additionally expose `GET /api/v1/system/auth-key`
(anonymous) returning `{ keyId, publicKey }` per the api capability's Authentication
Endpoints requirement.
`lumen-system` SHALL additionally enforce per-account login failure lockout
(user rulings 2026-11-09): a failed BCrypt match (`"A0210"`) increments a per-username
failure counter in Redis (fixed window, `lumen.security.login-lockout.window-minutes`,
default 15); when the counter reaches `lumen.security.login-lockout.max-attempts`
(default 5) the account is LOCKED for `lumen.security.login-lockout.lock-minutes`
(default 30). A locked account SHALL be rejected with envelope code `"A0211"` (用户输入
密码错误次数超限, the copied `ExceptionEnum` constant REUSED verbatim) BEFORE the BCrypt
round — including when the submitted password is CORRECT — until the lock expires.
Unknown accounts (`"A0201"`) and transport-format failures (`"A0400"`) SHALL NOT affect
any counter. A successful login SHALL clear the account's counter. An administrator
password reset SHALL clear the target account's counter and lock (the emergency unlock
channel; automatic expiry is the normal path). Lockout parameters are committed
non-sensitive configuration; the counting state lives entirely in Redis alongside the
existing token state.
The cached login session SHALL carry the user's permission set (the `perms` strings
assembled from the granted menu rows) so every authenticated request can be authorized
server-side.

#### Scenario: Successful login

- **WHEN** a client posts valid credentials of the seeded admin — the `password` field
  being SM2 ciphertext of the raw password encrypted under the distributed `publicKey`
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`
- **AND** the account's failure counter is cleared

#### Scenario: Invalid credentials

- **WHEN** a client posts a wrong password (correctly SM2-encrypted) below the lockout
  threshold
- **THEN** the response carries an `A`-class code `"A0210"` (用户密码错误) with `data` null
- **AND** no token is issued and the failure counter increments

#### Scenario: Plaintext password rejected (clean cutover)

- **WHEN** a client posts `{"username":"admin","password":"admin123"}` — any value that
  is not SM2 ciphertext
- **THEN** the response is `code: "A0400"` with a descriptive `msg` and `data` null
- **AND** no token is issued, the account lookup outcome is not disclosed, and the
  lockout counter is NOT affected

#### Scenario: Tampered ciphertext rejected

- **WHEN** a client posts SM2 ciphertext that was altered after encryption (or encrypted
  under a foreign key)
- **THEN** the response is `code: "A0400"` with `data` null and the lockout counter is
  NOT affected

#### Scenario: Key endpoint is anonymous

- **WHEN** a client requests `GET /api/v1/system/auth-key` without a token
- **THEN** the envelope answers `code: "200"` with `data = { keyId, publicKey }` where
  `publicKey` is the 130-char hex uncompressed SM2 point derived from the configured
  private scalar

#### Scenario: Unknown accounts never count and cannot be locked

- **WHEN** a client posts an unknown username with any validly-encrypted password
- **THEN** the response is `"A0201"` and no counter or lock key is created for that
  username string

#### Scenario: Failure threshold locks the account

- **WHEN** the same username accumulates `max-attempts` (default 5) wrong-password
  rejections within the window
- **THEN** the account is locked in Redis with the lock TTL
- **AND** the very next login attempt for that username is rejected with `"A0211"`
  before any BCrypt round, regardless of the submitted password

#### Scenario: Locked account rejects the correct password

- **WHEN** a locked account submits its CORRECT password as valid SM2 ciphertext
- **THEN** the response is `"A0211"` with `data` null and no token is issued

#### Scenario: Lock expires automatically

- **WHEN** the lock TTL elapses
- **THEN** login attempts for that username proceed through the normal loop again
  (a correct password succeeds, a wrong one counts from zero)

#### Scenario: Successful login clears the counter

- **WHEN** a user with 1..max-attempts-1 recorded failures logs in successfully
- **THEN** the account's failure counter and any residual window state are cleared —
  earlier failures do not accumulate toward the next lockout

#### Scenario: Admin password reset clears the lock

- **WHEN** an administrator resets a locked (or failing) account's password through
  `PUT /api/v1/system/users/{id}/password`
- **THEN** the account's counter and lock key are deleted, so the account can log in
  again immediately with the new credentials

#### Scenario: Session carries the permission set

- **WHEN** a user logs in
- **THEN** the cached session holds that user's permission set exactly as the `/me`
  response reports it
- **AND** a session created before the permission set existed is treated as empty
  (deny) rather than trusted
