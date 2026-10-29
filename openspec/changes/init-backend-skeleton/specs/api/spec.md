# Spec Delta

## MODIFIED Requirements

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

### Requirement: Gateway Path Convention
All endpoints MUST be exposed through the platform's unified API entry (nginx in the
hospital form; Spring Cloud Gateway optional in the cloud form) under
`/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`,
`/api/v1/radiology/exam-orders`). Individual services MUST NOT be called directly by the
frontend.

#### Scenario: Service routing
- **WHEN** the frontend calls `/api/v1/radiology/exam-orders`
- **THEN** the unified entry routes the request to `lumen-radiology` with auth context
  forwarded
