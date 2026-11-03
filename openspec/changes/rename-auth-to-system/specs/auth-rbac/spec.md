# Spec Delta

## MODIFIED Requirements

### Requirement: Login and Token Issuance
`lumen-system` SHALL expose `POST /api/v1/system/login`: valid credentials (username +
password) SHALL return the standard envelope with a JWT issued by the copied `TokenService`
(token state in Redis) and the authenticated user's profile; invalid credentials SHALL
return an `A`-class error code with a descriptive `msg`. Passwords SHALL be stored hashed.

#### Scenario: Successful login
- **WHEN** a client posts valid credentials of the seeded admin
- **THEN** the response is `code: "200"` with a JWT and the user profile in `data`
- **AND** the token is verifiable by the copied `JwtAuthenticationTokenFilter`

#### Scenario: Invalid credentials
- **WHEN** a client posts a wrong password
- **THEN** the response carries an `A`-class code (e.g. `"A0210"` 用户密码错误) with
  `data` null
- **AND** no token is issued

### Requirement: Token Validation on Every Request
Every authenticated request to `lumen-system` SHALL pass through the copied
`JwtAuthenticationTokenFilter`: a missing, expired, or invalid token SHALL be rejected by
the copied `AuthenticationEntryPointImpl` with envelope code `"203"` (HTTP 200 - the copied
machine's recorded transport-auth semantic, user ruling 2026-11-02) and SHALL NOT reach any
controller; a valid token SHALL populate the security context with the user identity used
by `MetaObjectConfig` audit filling.

#### Scenario: Request without token
- **WHEN** a request hits a protected endpoint without a token
- **THEN** it is rejected with envelope code `"203"` (HTTP 200, `data` null) and no
  controller method executes

#### Scenario: Audit identity from valid token
- **WHEN** an authenticated user creates data through any management endpoint
- **THEN** `MetaObjectConfig` fills `create_by` with that user's id
