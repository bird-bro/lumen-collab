# Spec Delta: api

## MODIFIED Requirements

### Requirement: User Management Endpoints

`lumen-system` SHALL expose user management under `/api/v1/system/users`: `POST /api/v1/system/users`
creates a user (duplicate live username rejected with an `A`-class code),
`GET /api/v1/system/users/list` returns the user list (implemented surface: a plain
`List`, no page wrapper),
`GET /api/v1/system/users/{id}` returns one user's full profile detail,
`PUT /api/v1/system/users/{id}` partial-updates the profile (name, phone, email, gender,
title, depId, roleIds, modalitys, repAudit, signature — `username` and `password` are NOT
updatable through it),
`DELETE /api/v1/system/users/{id}` logically deletes the user and unbinds its
`user_role` rows, plus the existing `PUT /api/v1/system/users/{id}/password` reset and
`PUT /api/v1/system/users/{id}/enable` toggle. All respond with the standard envelope.
The create request's `password` field and the reset request's `newPassword` field
SHALL carry SM2 ciphertext (C1C3C2, hex) instead of plaintext, decrypted server-side
before BCrypt encoding; values that are not decryptable ciphertext SHALL be rejected with
envelope code `"A0400"` and a descriptive `msg` (coordination password-transport-encryption
D5/D6).

#### Scenario: Paged user list

- **WHEN** an authenticated client requests `GET /api/v1/system/users/list`
- **THEN** it returns `code: "200"` with the user payload in `data` (plain list, per the
  implemented surface - a later backend change may add a page wrapper)

#### Scenario: List rows carry the profile and role names

- **WHEN** the list is returned for a user holding profile values and bound roles
- **THEN** the row carries `phone`, `email`, `gender`, `title`, `depId`, `depName`,
  `modalitys`, `repAudit`, `signature`, and `roleNames` (the live role names bound
  through `user_role`)
- **AND** the BCrypt hash never serializes

#### Scenario: Create user

- **WHEN** an authenticated client posts a fresh username to `POST /api/v1/system/users`
- **THEN** the user is created with the BCrypt hash of the DECRYPTED initial password and
  `code: "200"` with the new user id in `data`

#### Scenario: Duplicate username

- **WHEN** the posted username already exists live
- **THEN** the envelope carries the duplicate-username `A`-class code and `data` is null

#### Scenario: User detail for the edit dialog

- **WHEN** an authenticated client requests `GET /api/v1/system/users/{id}` for a live user
- **THEN** the envelope carries `code: "200"` with the full profile (username, name,
  phone, email, gender, title, depId, roleIds, modalitys, repAudit, signature) in `data`
- **AND** the password hash is absent

#### Scenario: Detail of a missing user

- **WHEN** the detail or update or delete endpoint is called for a non-live user id
- **THEN** the envelope answers the B-class not-found code with `data` null

#### Scenario: Profile update

- **WHEN** an authenticated client PUTs profile fields to `/api/v1/system/users/{id}`
- **THEN** the live user's mutable fields update in one transaction and the response is
  `code: "200"`
- **AND** a `roleIds` field REPLACES the user's role bindings (full-replace, the
  role-menu assignment precedent); fields absent from the body stay unchanged

#### Scenario: Signature stores the uploaded path

- **WHEN** the update body carries a `signature` value
- **THEN** the stored value is the common image upload's returned path (a
  `/profile/...` relative path usable as `<img src>`), stored verbatim — no byte
  storage, no table bookkeeping (the logo precedent)

#### Scenario: Username is immutable through the update

- **WHEN** the update body carries a `username` different from the stored one
- **THEN** the stored username is unchanged (the field is ignored, not applied)

#### Scenario: Update validates role ids and department

- **WHEN** the update body's `roleIds` contains a non-live role id, or its `depId` a
  non-live department id
- **THEN** the response is an `A`-class rejection and no field of the user changes

#### Scenario: User logical delete with cascade unbind

- **WHEN** an authorized client DELETEs a live user (neither self nor the seed admin)
- **THEN** the envelope returns `code: "200"` with `data: true`
- **AND** the row persists with `deleted = 1`, disappears from list/detail, and its
  `user_role` bindings are unbound (logical delete)
- **AND** the username becomes re-creatable (the partial unique index covers live rows)

#### Scenario: Self-delete rejected

- **WHEN** the authenticated caller DELETEs their own user id
- **THEN** the response is an `A`-class rejection and no row changes

#### Scenario: Seed-admin delete rejected

- **WHEN** a client DELETEs user id 1 (the seeded bootstrap administrator)
- **THEN** the response is an `A`-class rejection and no row changes

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

#### Scenario: New button permissions gate the new endpoints

- **WHEN** a caller without `system:user:update` calls the profile update, or one without
  `system:user:delete` calls the delete endpoint
- **THEN** the copied permission machine rejects the request with `A0301` before any
  controller logic runs

<!-- synced from: user-edit-delete-profile-api 2026-11-10 -->
