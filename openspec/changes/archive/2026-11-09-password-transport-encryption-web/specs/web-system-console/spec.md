# web-system-console Delta: password-transport-encryption-web

## MODIFIED Requirements

### Requirement: User Management

The console SHALL provide a user list view (`GET /api/v1/system/users/list`, which returns
a plain list - the view paginates client-side) with create
(`POST /api/v1/system/users`), password reset (`PUT /api/v1/system/users/{id}/password`), and
enable/disable (`PUT /api/v1/system/users/{id}/enable`). A duplicate-username rejection SHALL
surface the backend's `A`-class `msg` on the form. The create request's `password` and the
reset request's `newPassword` SHALL be SM2-encrypted with the session-cached public key from
`GET /api/v1/system/auth-key` (C1C3C2 hex — coordination password-transport-encryption D4/D6)
inside the API-client boundary (`src/api/users.ts` keeps raw-password signatures; the typed
value never reaches the wire as plaintext). A backend `A0400` rejection SHALL surface
through the same message path as other `A`-class failures on the form.

#### Scenario: Paged user list

- **WHEN** an administrator opens the user page
- **THEN** the user list renders and re-queries on filter/page change

#### Scenario: Duplicate username feedback

- **WHEN** a user is created with an existing username
- **THEN** the backend's duplicate-username message is shown and the dialog stays open

#### Scenario: Password and enable actions

- **WHEN** an administrator resets a user's password or toggles enable
- **THEN** the corresponding documented endpoint is called and the list reflects the result

#### Scenario: Encrypted password fields on create and reset

- **WHEN** the create dialog submits or the reset action confirms
- **THEN** the captured request body's `password` / `newPassword` value is a hex SM2
  ciphertext that differs from the typed value
- **AND** the backend accepts it (decrypt-then-BCrypt), so the operation succeeds exactly
  as the plaintext flow did before password-transport-encryption
