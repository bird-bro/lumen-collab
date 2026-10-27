# API Contract

## Purpose
Authoritative API contract. Frontend mocks from this; backend implements to this. Defines base URL, response envelope, and endpoint conventions.

## Requirements

### Requirement: API Response Envelope
All API responses MUST use the standard envelope `{ "code": 0, "data": {}, "message": "" }`. Base URL: `/api/v1`.

#### Scenario: Successful response
- **WHEN** a request succeeds
- **THEN** returns `code: 0` with the typed payload in `data`

#### Scenario: Error response
- **WHEN** a request fails
- **THEN** returns a non-zero `code` with an error `message` and `data: null`

### Requirement: Endpoint Documentation
Each endpoint MUST be documented as a Requirement with WHEN/THEN scenarios. Add new Requirements for each resource.

#### Scenario: Resource retrieval
- **WHEN** a client requests `GET /api/v1/[resource]`
- **THEN** returns `code: 0` with `[payload]`

### Requirement: Gateway Path Convention
All endpoints MUST be exposed through `lumen-gateway` under `/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`, `/api/v1/ris/exam-orders`). Individual services MUST NOT be called directly by the frontend.

#### Scenario: Service routing
- **WHEN** the frontend calls `/api/v1/ris/exam-orders`
- **THEN** the gateway routes the request to `lumen-ris` with auth context forwarded

### Requirement: ID Serialization
Entity IDs are snowflake `bigint` (application-layer generated). All IDs MUST be serialized as JSON strings to avoid JavaScript precision loss.

#### Scenario: ID in JSON payload
- **WHEN** a response contains an entity ID (e.g. `123456789012345678`)
- **THEN** the JSON value is a string `"123456789012345678"`, never a bare number
