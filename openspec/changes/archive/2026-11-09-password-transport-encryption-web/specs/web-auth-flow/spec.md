# web-auth-flow Delta: password-transport-encryption-web

## MODIFIED Requirements

### Requirement: Login Page

The application SHALL provide a login page (OPS-style centered card with the product title)
that posts credentials to `POST /api/v1/system/login`. Submission SHALL be triggerable by both
the login button and the Enter key, SHALL show a pending state while the request is in flight,
and SHALL surface the failure `msg` of any `A`-class error envelope (e.g. wrong password) as a
user-visible message without leaving the page. Before submit, the page SHALL fetch the SM2
public key from the anonymous `GET /api/v1/system/auth-key` endpoint (result cached
module-level for the session) and SHALL encrypt the typed password with it
(`sm2.doEncrypt(raw, publicKey, 1)`, C1C3C2 hex — coordination password-transport-encryption
D4/D8): the typed password value SHALL NOT appear in any request body, URL, or log as
plaintext. A backend `A0400` rejection (undecryptable ciphertext, e.g. after a server-side
key rotation) SHALL surface through the same inline failure message path as other `A`-class
errors — no automatic retry loop.

#### Scenario: Successful login

- **WHEN** a user submits valid credentials
- **THEN** the request body's `password` field carries SM2 ciphertext (hex), not the
  typed value, and the backend's decrypt-then-BCrypt loop accepts it
- **AND** the JWT and profile from the response are stored as the session
- **AND** the user is routed into the application shell

#### Scenario: Wrong password

- **WHEN** a user submits an invalid password
- **THEN** the envelope's `A`-class `msg` (e.g. wrong-password text) is shown on the login page
- **AND** no session is created and the route does not change

#### Scenario: Enter submits

- **WHEN** the password field has focus and the user presses Enter
- **THEN** the login submits exactly as if the button was pressed

#### Scenario: Plaintext never on the wire

- **WHEN** any login submission is inspected (network body or captured request in tests)
- **THEN** the `password` value is a hex string that differs from the typed password
- **AND** the typed password is not present anywhere else in the request

#### Scenario: Stale key surfaces as a message

- **WHEN** the backend rejects the encrypted password with envelope code `A0400`
  (e.g. the server key was rotated under a cached public key)
- **THEN** the backend's `msg` is shown in the inline failure box exactly like other
  `A`-class failures
- **AND** the next submit attempt uses the freshly fetched key (no page reload required)
