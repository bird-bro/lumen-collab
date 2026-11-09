# api Delta: password-transport-encryption-api

## MODIFIED Requirements

### Requirement: Authentication Endpoints

`lumen-system` SHALL document and expose the auth endpoints as the platform's first concrete
endpoints: `POST /api/v1/system/login` (credentials → JWT + profile) and
`GET /api/v1/system/me` (valid token → profile + permission set). Both respond with the
standard envelope and the error-code scheme of `openspec/specs/errors/spec.md`.
`lumen-system` SHALL additionally expose `GET /api/v1/system/auth-key` as the ANONYMOUS
password-crypto key distribution endpoint: it responds with the standard envelope whose
`data` is `{ "keyId": "<configured key-id>", "publicKey": "<130-char hex uncompressed SM2
point 04||x||y>" }`, the public key derived server-side from the configured private scalar
D (never separately configured; coordination password-transport-encryption D2/D3). The
login request's `password` field SHALL carry SM2 ciphertext — C1C3C2 mode, hex encoding —
instead of plaintext; the server decrypts it before the BCrypt match (auth-rbac capability).
A request whose password field is not decryptable ciphertext SHALL be rejected with
envelope code `"A0400"` (用户请求参数错误, the copied `ExceptionEnum` constant reused
verbatim) and a descriptive `msg` (coordination D5).

#### Scenario: Login endpoint documented and reachable

- **WHEN** a client posts valid credentials — SM2-encrypted password ciphertext — to
  `POST /api/v1/system/login`
- **THEN** it returns `code: "200"` with the JWT and profile
- **AND** this endpoint requires no token itself

#### Scenario: Current-user endpoint

- **WHEN** a client requests `GET /api/v1/system/me` with a valid token
- **THEN** it returns `code: "200"` with the profile and permission set (menus + perms
  strings) of the authenticated user

#### Scenario: Auth-key endpoint is anonymous

- **WHEN** a client requests `GET /api/v1/system/auth-key` without any token
- **THEN** the envelope answers `code: "200"` with `data.keyId` and `data.publicKey`
- **AND** the publicKey is the 130-char hex uncompressed point (starts with `04`)

#### Scenario: Login password field carries ciphertext

- **WHEN** the login request body is inspected
- **THEN** the `password` value is a hex SM2 ciphertext (C1C3C2), never the raw password
- **AND** a plaintext password value is rejected with `code: "A0400"`, `data` null

### Requirement: User Management Endpoints

`lumen-system` SHALL expose user management under `/api/v1/system/users`: `POST /api/v1/system/users`
creates a user (duplicate live username rejected with an `A`-class code),
`GET /api/v1/system/users/list` returns the user list (implemented surface: a plain
`List`, no page wrapper),
`PUT /api/v1/system/users/{id}/password` resets a user's password, and
`PUT /api/v1/system/users/{id}/enable` toggles the enable flag. All respond with the
standard envelope. (Implemented surface - documentation sync of shipped `lumen-system`
code.) The create request's `password` field and the reset request's `newPassword` field
SHALL carry SM2 ciphertext (C1C3C2, hex) instead of plaintext, decrypted server-side
before BCrypt encoding; values that are not decryptable ciphertext SHALL be rejected with
envelope code `"A0400"` and a descriptive `msg` (coordination password-transport-encryption
D5/D6).

#### Scenario: Paged user list

- **WHEN** an authenticated client requests `GET /api/v1/system/users/list`
- **THEN** it returns `code: "200"` with the user payload in `data` (plain list, per the
  implemented surface - a later backend change may add a page wrapper)

#### Scenario: Create user

- **WHEN** an authenticated client posts a fresh username to `POST /api/v1/system/users`
- **THEN** the user is created with the BCrypt hash of the DECRYPTED initial password and
  `code: "200"` with the new user id in `data`

#### Scenario: Duplicate username

- **WHEN** the posted username already exists live
- **THEN** the envelope carries the duplicate-username `A`-class code and `data` is null

#### Scenario: Password reset and enable toggle

- **WHEN** an authenticated client calls the password-reset or enable endpoint for a user id
- **THEN** it returns `code: "200"` and the mutation persists

#### Scenario: Encrypted password fields

- **WHEN** the create request's `password` or the reset request's `newPassword` is a hex
  SM2 ciphertext produced with the distributed `publicKey`
- **THEN** the operation proceeds exactly as the plaintext flow did before this change
  (BCrypt of the decrypted raw value, `must_change_password = 1` on reset)

#### Scenario: Plaintext or undecryptable value rejected

- **WHEN** `password` / `newPassword` arrives as plaintext, non-hex text, or ciphertext
  undecryptable under the configured key
- **THEN** the envelope answers `code: "A0400"` with a descriptive `msg` and `data` null
- **AND** no user row is created or modified
