## MODIFIED Requirements

### Requirement: Internal Service Issuance Contract

The platform SHALL expose the FIRST inter-service Feign contract through the
`lumen-feign` module: `NumberApi` (`POST /api/v1/system/number-rules/issue`) with a
FAIL-FAST fallback — a fallback that fabricates numbers is forbidden. The `/issue`
endpoint SHALL require the internal-service key (`X-Internal-Key` header, gitignored
config on both sides) and SHALL reject anonymous or wrong-key calls:

#### Scenario: Issue endpoint rejects non-internal callers

- **WHEN** a request reaches `/issue` without the internal key or with a wrong key
- **THEN** it is rejected with an auth failure and no number is issued

#### Scenario: Feign failure fails fast

- **WHEN** the number-rule service is unreachable for a consumer
- **THEN** the Sentinel fallback THROWS (the caller's operation refuses)
- **AND** no fallback path ever fabricates a number

<!-- rename-lumen-api-feign 2026-11-20: contract host module name only (lumen-api → lumen-feign), zero behavior change -->
