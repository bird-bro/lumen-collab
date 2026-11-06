# api Delta: org-logo-upload-api

## MODIFIED Requirements

### Requirement: Gateway Path Convention

All endpoints MUST be exposed through the platform's unified API entry (nginx in the
hospital form; Spring Cloud Gateway optional in the cloud form) under
`/api/v1/<service>/**` (e.g. `/api/v1/patient/patients`,
`/api/v1/radiology/exam-orders`). Individual services MUST NOT be called directly by
the frontend. Uploaded static files are the ONE sanctioned segment outside
`/api/v1`: the `/profile/**` tree is served by the owning service (v1: lumen-system)
as anonymous GET read-back for uploaded files, and both the unified entry and the dev
proxy MUST route it to the same owning service (coordination: org-logo-upload D7).

#### Scenario: Service routing

- **WHEN** the unified-entry routing contract is inspected (deployment config arrives
  with the deferred `docker/` change; the frontend arrives with `init-web-skeleton`)
- **THEN** every documented endpoint path lives under `/api/v1/<service>/**`
- **AND** no contract, code, or config routes the frontend directly to a backend
  service port

#### Scenario: Static read-back segment

- **WHEN** a client GETs a stored upload path such as `/profile/avatar/2026/11/06/uuid.png`
- **THEN** the owning service (v1: lumen-system) serves the file bytes anonymously —
  no Authorization header, no envelope, direct `image/*` response
- **AND** writes under `/profile/**` are not possible (read-only static mapping)

## ADDED Requirements

### Requirement: Common Image Upload Endpoint

lumen-system SHALL expose `POST /api/v1/system/common/upload/image` as the common
image transport (OPS `CommonController.uploadImage` 1:1; coordination org-logo-upload
D1–D5): authenticated callers only (no business permission bound — D3), one
`file` multipart part, stored server-side under the configured upload root, answered
with the standard envelope whose `data` is `{ "path": <stored path> }`. The endpoint is
transport-only: it records nothing in any table (logo is a 1:1 column value; the
sys_attach bookkeeping layer is a recorded future direction, D2).

#### Scenario: Upload succeeds

- **WHEN** an authenticated caller POSTs a valid image (≤2MB) as the `file` part
- **THEN** the envelope answers `{ "code": "200", "msg": ..., "data": { "path": "/profile/avatar/<yyyy/MM/dd>/<uuid>.<ext>" } }`
- **AND** the bytes exist on disk under `${upload.home}` at that relative path

#### Scenario: Invalid image is rejected with A0400

- **WHEN** the posted file's extension is outside `png/jpg/jpeg/webp`, exceeds 2MB,
  or is empty
- **THEN** the envelope answers with code `A0400` and a descriptive msg
- **AND** no file is written to disk

#### Scenario: Unauthenticated caller

- **WHEN** a request without a valid token hits the endpoint
- **THEN** the copied entry point answers the transport-auth semantic (`"203"`)
  before any controller logic runs

#### Scenario: Stored path is readable back anonymously

- **WHEN** an upload has returned `data.path = /profile/avatar/.../x.png`
- **THEN** `GET /profile/avatar/.../x.png` (no token) serves the identical bytes
- **AND** the same path is stable for reuse as `<img src>` (the logo column value)

#### Scenario: Other services are unaffected

- **WHEN** a lumen service whose config does not define `upload.home` boots
- **THEN** its `/profile/**` registration stays without locations (today's
  OPS-parity behavior) and nothing about its request handling changes
