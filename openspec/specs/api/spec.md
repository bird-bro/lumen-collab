# API Contract

## Purpose
Authoritative API contract. Frontend mocks from this; backend implements to this. Defines base URL, response envelope, and endpoint conventions.

## Requirements

### Requirement: API Response Envelope
All API responses MUST use the standard envelope of the OPS `R` contract:
`{ "timestamp": "...", "code": "...", "msg": "...", "data": {} }`, where `timestamp` is the
server time formatted `yyyy-MM-dd hh:mm:ss.SSS`, `code` is a string, and `msg` is a
human-readable description. Base URL: `/api/v1`.

#### Scenario: Successful response
- **WHEN** a request succeeds and has content to return
- **THEN** `code` is `"200"` and the typed payload is in `data`

#### Scenario: Successful response without payload
- **WHEN** a request succeeds but has no content to return
- **THEN** `code` is `"204"` and `data` is null

#### Scenario: Error response
- **WHEN** a request fails
- **THEN** `code` carries the error code string defined by the Error Code Scheme and `msg`
  describes the failure
- **AND** `data` is null


<!-- synced from: auth-rbac-foundation 2026-11-03 -->
### Requirement: Endpoint Documentation
Each endpoint MUST be documented as a Requirement with WHEN/THEN scenarios. Add new
Requirements for each resource. All endpoints live under `/api/v1/<service>/**` and respond
with the standard envelope (string `code`).

#### Scenario: Resource retrieval
- **WHEN** a client requests `GET /api/v1/[resource]`
- **THEN** returns `code: "200"` with the typed payload in `data`

### Requirement: Gateway Path Convention
All endpoints MUST be exposed through the platform's unified API entry (nginx in the
hospital form; Spring Cloud Gateway optional in the cloud form) under
`/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`,
`/api/v1/radiology/exam-orders`). Individual services MUST NOT be called directly by the
frontend.

#### Scenario: Service routing
- **WHEN** the unified-entry routing contract is inspected (deployment config arrives with
  the deferred `docker/` change; the frontend arrives with `init-web-skeleton`)
- **THEN** every documented endpoint path lives under `/api/v1/<service>/**`
- **AND** no contract, code, or config routes the frontend directly to a backend service
  port

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
### Requirement: ID Serialization
Entity IDs SHALL be Integer auto-increment (user decision 2026-11-02, OPS/随访 lineage; the
former snowflake-bigint clause is withdrawn together with the data capability's delta in
this change). IDs SHALL serialize as JSON numbers.

#### Scenario: ID in JSON payload
- **WHEN** a response contains an entity ID (e.g. the seeded admin's `uid`)
- **THEN** the JSON value is the Integer number `1`, never a string

<!-- synced from: auth-rbac-foundation 2026-11-03 -->
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
