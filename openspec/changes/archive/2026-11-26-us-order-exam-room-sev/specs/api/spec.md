# api Delta

## ADDED Requirements

### Requirement: Internal Exam Room Reads

`lumen-system` SHALL expose INTERNAL exam-room reads for service-to-service
consumers — `GET /api/v1/system/internal/org-rooms/list?depId=` (the department's
ENABLED rooms as slim `{id, name}` rows ordered by id) and
`GET /api/v1/system/internal/org-rooms/{id}` (the room row carrying `id`, `oid`,
`did`, `name`, `enable`). Both SHALL sit OUTSIDE the JWT permission gates and be
guarded by the shared internal key (the `InternalKeyInterceptor` registration — the
`/api/v1/system/number-rules` internal family precedent): a caller without the key
is refused; the console registry endpoints (`/api/v1/system/org-rooms/**`) are
UNTOUCHED and stay JWT + `system:room:*` gated.

#### Scenario: Internal list returns the department's enabled rooms

- **WHEN** a service consumer calls the internal list with a department id and the
  shared internal key
- **THEN** the envelope carries that department's enabled rooms as `{id, name}` rows
  ordered by id — no device join payload (the consumers pick rooms, not devices)

#### Scenario: Internal detail carries the validation fields

- **WHEN** a service consumer calls the internal detail for a room id
- **THEN** the envelope carries the room row with `did` and `enable` — the fields the
  station's create-time validation (department match + enabled) reads

#### Scenario: Missing internal key is refused

- **WHEN** any caller hits an internal rooms endpoint without the shared internal key
- **THEN** the request is refused before the controller body runs
