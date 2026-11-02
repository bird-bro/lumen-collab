# Spec Delta

## MODIFIED Requirements

### Requirement: Endpoint Documentation
Each endpoint MUST be documented as a Requirement with WHEN/THEN scenarios. Add new
Requirements for each resource. All endpoints live under `/api/v1/<service>/**` and respond
with the standard envelope (string `code`).

#### Scenario: Resource retrieval
- **WHEN** a client requests `GET /api/v1/[resource]`
- **THEN** returns `code: "200"` with the typed payload in `data`

### Requirement: ID Serialization
Entity IDs SHALL be Integer auto-increment (user decision 2026-11-02, OPS/随访 lineage; the
former snowflake-bigint clause is withdrawn together with the data capability's delta in
this change). IDs SHALL serialize as JSON numbers.

#### Scenario: ID in JSON payload
- **WHEN** a response contains an entity ID (e.g. the seeded admin's `uid`)
- **THEN** the JSON value is the Integer number `1`, never a string

## ADDED Requirements

### Requirement: Authentication Endpoints
`lumen-auth` SHALL document and expose the auth endpoints as the platform's first concrete
endpoints: `POST /api/v1/auth/login` (credentials → JWT + profile) and
`GET /api/v1/auth/me` (valid token → profile + permission set). Both respond with the
standard envelope and the error-code scheme of `openspec/specs/errors/spec.md`.

#### Scenario: Login endpoint documented and reachable
- **WHEN** a client posts valid credentials to `POST /api/v1/auth/login`
- **THEN** it returns `code: "200"` with the JWT and profile
- **AND** this endpoint requires no token itself

#### Scenario: Current-user endpoint
- **WHEN** a client requests `GET /api/v1/auth/me` with a valid token
- **THEN** it returns `code: "200"` with the profile and permission set (menus + perms
  strings) of the authenticated user
