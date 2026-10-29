# Spec Delta

## MODIFIED Requirements

### Requirement: Error Code Scheme
Error codes MUST follow the OPS `ExceptionEnum` scheme, carried as strings in the
envelope's `code` field: success uses HTTP-status codes (`"200"`, `"204"`), client-side
errors use `A`-class codes, system errors use `B`-class codes, and third-party service
errors use `C`-class codes.

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

### Requirement: Error Response Format
All error responses MUST use the standard envelope with `data` null and a human-readable
`msg`.

#### Scenario: Error envelope
- **WHEN** any error occurs
- **THEN** the response body is
  `{ "timestamp": ..., "code": "<errorCode>", "msg": "<description>", "data": null }`
