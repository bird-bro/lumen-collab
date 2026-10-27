# Error Handling

## Purpose
Defines error codes, HTTP status mapping, and error response format for all API responses.

## Requirements

### Requirement: Error Code Scheme
Error codes MUST follow a numeric scheme mapping to HTTP statuses: `0` = success (200), `4xxxx` = client errors (4xx), `5xxxx` = server errors (5xx).

#### Scenario: Success
- **WHEN** a request succeeds
- **THEN** returns `code: 0` (HTTP 200)

#### Scenario: Client error
- **WHEN** a request has invalid parameters
- **THEN** returns `code: 40001` (HTTP 400) with a descriptive message

#### Scenario: Server error
- **WHEN** an internal error occurs
- **THEN** returns `code: 50001` (HTTP 500) with a generic message

### Requirement: Error Response Format
All error responses MUST use the standard envelope with `data: null` and a human-readable `message`.

#### Scenario: Error envelope
- **WHEN** any error occurs
- **THEN** response body is `{ "code": <errorCode>, "data": null, "message": "<description>" }`
