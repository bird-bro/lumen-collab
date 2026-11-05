# Spec Delta

## ADDED Requirements

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
