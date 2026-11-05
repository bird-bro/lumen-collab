# Error Handling

## Purpose
Defines error codes, HTTP status mapping, and error response format for all API responses.

## Requirements

### Requirement: Error Code Scheme
Error codes MUST follow the OPS `ExceptionEnum` scheme, carried as strings in the
envelope's `code` field: success uses HTTP-status codes (`"200"`, `"204"`), client-side
errors use `A`-class codes, system errors use `B`-class codes, and third-party service
errors use `C`-class codes. The enum's `SUCCESS("0000")` constant is retained verbatim from
OPS but unused - lumen code never emits it (the copied `R.ofSuccess` uses `"200"`/`"204"`).

#### Scenario: Success
- **WHEN** a request succeeds
- **THEN** `code` is `"200"` (or `"204"` when there is no content)

#### Scenario: Client error
- **WHEN** a request has invalid parameters
- **THEN** `code` is an `A`-class code such as `"A0400"` with a descriptive `msg`

#### Scenario: Server error
- **WHEN** an internal error occurs
- **THEN** the fallback handler returns the HTTP-status string `"500"` with a generic `msg`
- **AND** `B`-class codes arrive via `BusinessException` (e.g. `"B0001"` for system
  execution failure), matching the copied `GlobalExceptionHandler`

#### Scenario: Third-party error
- **WHEN** a PACS, HIS, or other external service call fails
- **THEN** `code` is a `C`-class code such as `"C0001"`

<!-- synced from: init-backend-skeleton 2026-11-02 -->

### Requirement: Error Response Format
All error responses MUST use the standard envelope with `data` null and a human-readable
`msg`.

#### Scenario: Error envelope
- **WHEN** any error occurs
- **THEN** the response body is
  `{ "timestamp": ..., "code": "<errorCode>", "msg": "<description>", "data": null }`

<!-- synced from: init-backend-skeleton 2026-11-02 -->

### Requirement: Permission Denied Response
An authenticated request that lacks the permission string required by the target
endpoint family SHALL be rejected before any controller method executes: HTTP 200
transport (the copied machine's recorded auth-error semantic, matching the `"203"`
entry-point ruling) with the standard envelope, `code` `"A0301"` (访问未授权 - the
copied `ExceptionEnum` constant reused verbatim), a descriptive `msg`, `data` null.

#### Scenario: Denied request envelope
- **WHEN** an authenticated caller without the family permission calls a gated endpoint
- **THEN** the response body is
  `{ "timestamp": ..., "code": "A0301", "msg": "<访问未授权 description>", "data": null }`
- **AND** the response never leaks whether the endpoint exists

#### Scenario: Unauthenticated still uses the entry point
- **WHEN** a tokenless request hits a gated endpoint
- **THEN** the copied entry point answers `"203"` as before - `"A0301"` is exclusive to
  the authenticated-but-unauthorized case
