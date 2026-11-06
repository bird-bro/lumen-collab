# web-app-shell Delta: org-logo-upload-web

## MODIFIED Requirements

### Requirement: Unified API Entry Boundary

All backend requests SHALL go to the `/api/v1` base path. In development the dev
server SHALL proxy `/api/v1` to the platform's unified API entry (nginx, port 80 in
the hospital form) as recorded in `openspec/coordination/init-project-skeleton.md`.
Frontend code MUST NOT address an individual service host or port. The ONE sanctioned
segment outside `/api/v1` is the static upload read-back tree `/profile/**` (api spec
§ Gateway Path Convention): the dev server SHALL proxy it to the same unified-entry
target so stored upload paths resolve as same-origin `<img src>` in every dev form.

#### Scenario: Dev-server request path

- **WHEN** the dev server handles a request to `/api/v1/<anything>`
- **THEN** it proxies the request to the unified API entry on its allocated port
- **AND** no per-service port is targeted

#### Scenario: Static read-back path

- **WHEN** the dev server handles a request to `/profile/<anything>` (an `<img>`
  resolving a stored upload path, e.g. the org logo)
- **THEN** the same proxy entry target is used as for `/api/v1`
- **AND** the direct-service preview override (`VITE_API_ENTRY_URL`) retargets it
  identically

#### Scenario: Service host appears in source

- **WHEN** a source file hard-codes a backend service host or port
- **THEN** the unified-entry boundary is violated
- **AND** the call must be routed through the `/api/v1` base path instead

## ADDED Requirements

### Requirement: Multipart Upload Transport

The client seam SHALL provide an additive multipart transport (`apiUpload`) beside
the JSON `apiRequest`: it wraps one `File` in `FormData` (part name per the endpoint
contract), sends the Bearer header, sets NO `Content-Type` (the browser's multipart
boundary), and unwraps the same envelope — code `"200"` resolves `data`, `"204"`
resolves null, `"203"` fires `onSessionExpired()` then throws, any other code throws
`ApiError{code, msg}`. Only endpoints documented in the api spec MAY be called
through it (the invented-endpoints rule applies unchanged).

#### Scenario: Upload call shape

- **WHEN** a client calls `apiUpload('/system/common/upload/image', file)`
- **THEN** the request is a POST with a `FormData` body carrying the file under the
  documented part name and an `Authorization: Bearer` header
- **AND** no `Content-Type` header is set by the transport (browser sets the boundary)

#### Scenario: Envelope semantics are shared

- **WHEN** an upload response arrives with envelope codes `200` / `204` / `203` /
  any `A`/`B` class
- **THEN** the transport resolves/throws exactly as the JSON transport does for the
  same code (including the `"203"` session-expiry callback)

#### Scenario: No invented upload paths

- **WHEN** a page needs to upload a file the api spec does not define
- **THEN** no upload path is invented in `src/api/`
- **AND** the missing contract is raised through a proposal (unchanged seam rule)
